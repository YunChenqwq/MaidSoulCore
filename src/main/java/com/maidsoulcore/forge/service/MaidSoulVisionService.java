package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主人视角感知服务。
 * <p>
 * 当前版本先不直接上传真实位图截图，而是走一条更稳定的落地链：
 * 1. 由服务端基于主人的位置、朝向、准星命中结果和周边环境，生成“视角原始摘要”；
 * 2. 再把这段原始摘要送给 MaiBot 的 VLM 槽位，得到更适合写入黑板与提示词的“解释后摘要”；
 * 3. 最后把结果写回运行时状态，并在明显变化时广播给主人对话链做主动搭话。
 * <p>
 * 这样做的优点是：
 * - 不依赖复杂截图网络同步，先把核心多模型协作链跑通；
 * - 仍然保留 VLM 槽位，让“视觉模型”成为真正的感知整理层；
 * - 后续如果要上真实截图，只需要替换原始摘要生成部分，而不用推翻整条链路。
 */
public final class MaidSoulVisionService {
    /**
     * 单线程执行器，用于串行化 VLM 视角解释请求，避免同一时间打爆模型接口。
     */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "maidsoulcore-vision");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * 防止同一只女仆并发重复发起视角感知请求。
     */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    /**
     * 记录最近一次视角摘要签名，用于判断“视角是否真的明显变化”。
     */
    private static final ConcurrentMap<UUID, Integer> LAST_VISION_SIGNATURE = new ConcurrentHashMap<>();
    /**
     * 记录最近一次视角采样时间，用于实现“聊天活跃时高频，不聊天时低频”。
     */
    private static final ConcurrentMap<UUID, Long> LAST_CAPTURE_MILLIS = new ConcurrentHashMap<>();

    private MaidSoulVisionService() {
    }

    /**
     * 在女仆 tick 中驱动一次轻量视角采样。
     * <p>
     * 只要开启配置并达到采样间隔，就会异步调用 VLM 槽位做感知整理。
     */
    public static void onMaidTick(EntityMaid maid) {
        if (!MaidSoulCommonConfig.VISION_ENABLED.get()) {
            return;
        }
        if (MaidSoulCompanionService.isChatFocusActive(maid)) {
            return;
        }
        long intervalMillis = MaidSoulCompanionService.visionIntervalSeconds(maid) * 1000L;
        long now = System.currentTimeMillis();
        Long previousCapture = LAST_CAPTURE_MILLIS.get(maid.getUUID());
        if (previousCapture != null && now - previousCapture < intervalMillis) {
            return;
        }
        if (maid.getOwner() == null || maid.getServer() == null) {
            return;
        }
        UUID maidId = maid.getUUID();
        VisionCaptureResult rawResult = captureOwnerViewSummary(maid);
        LAST_CAPTURE_MILLIS.put(maidId, now);
        int signature = rawResult.signature();
        Integer previousSignature = LAST_VISION_SIGNATURE.get(maidId);
        if (previousSignature != null && previousSignature == signature) {
            return;
        }
        if (!MaidSoulCommonConfig.VISION_LLM_INTERPRET_ENABLED.get()) {
            LAST_VISION_SIGNATURE.put(maidId, signature);
            handleVisionResult(maid, rawResult);
            return;
        }
        if (!IN_FLIGHT.add(maidId)) {
            return;
        }
        LAST_VISION_SIGNATURE.put(maidId, signature);

        SimulationMaiBotRuntimeConfig runtimeConfig = SimulationMaiBotConfigLoader.loadFromDirectory(Path.of(MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get()));
        if (!runtimeConfig.available()) {
            IN_FLIGHT.remove(maidId);
            handleVisionResult(maid, rawResult);
            return;
        }
        String systemPrompt = MaidSoulPromptService.buildVisionSystemPrompt(maid, runtimeConfig);
        String userPrompt = MaidSoulPromptService.buildVisionUserPrompt(maid, rawResult.rawSummary());

        CompletableFuture.supplyAsync(() -> interpretVision(runtimeConfig, systemPrompt, userPrompt, rawResult), EXECUTOR).whenComplete((result, throwable) -> {
            IN_FLIGHT.remove(maidId);
            maid.getServer().execute(() -> {
                if (!maid.isAlive()) {
                    return;
                }
                if (throwable != null) {
                    if (previousSignature == null) {
                        LAST_VISION_SIGNATURE.remove(maidId, signature);
                    } else {
                        LAST_VISION_SIGNATURE.put(maidId, previousSignature);
                    }
                    MaidSoulStateRegistry.record(
                            maid,
                            "owner.view.capture.failed",
                            throwable.getClass().getSimpleName() + ": " + throwable.getMessage(),
                            EventPriority.P2
                    );
                    return;
                }
                handleVisionResult(maid, result);
            });
        });
    }

    /**
     * 生成主人视角的原始摘要，并调用 VLM 槽位做解释整理。
     */
    private static VisionCaptureResult interpretVision(SimulationMaiBotRuntimeConfig runtimeConfig,
                                                       String systemPrompt,
                                                       String userPrompt,
                                                       VisionCaptureResult rawResult) {
        SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(runtimeConfig);
        String interpreted = client.completeText(
                runtimeConfig.vlmTask(),
                systemPrompt,
                userPrompt
        ).trim();
        if (interpreted.isBlank()) {
            interpreted = rawResult.rawSummary();
        }
        return new VisionCaptureResult(rawResult.rawSummary(), interpreted, rawResult.eventType(), rawResult.priority(), rawResult.notableLevel(), rawResult.semanticKey());
    }

    private static void handleVisionResult(EntityMaid maid, VisionCaptureResult result) {
        // 视角摘要默认只更新黑板，作为下一次聊天 prompt 的背景参考。
        // 只有真正危险、需要提醒主人的画面，才进入主动发言链路。
        // 这能避免“看到一个普通方块/风景”就打断当前对话。
        MaidSoulStateRegistry.updateOwnerView(maid, result.rawSummary(), result.interpretedSummary());
        if (shouldSpeakForVision(maid, result) && MaidSoulCompanionService.allowProactiveEvent(maid, result.eventType())) {
            MaidSoulStateRegistry.record(maid, result.eventType(), result.interpretedSummary(), result.priority());
            MaidSoulCompanionService.triggerProactiveEvent(maid, result.eventType(), result.interpretedSummary(), result.priority());
        }
    }

    /**
     * 基于主人当前的第一视角，构造一个足够稳定的场景文本摘要。
     */
    private static VisionCaptureResult captureOwnerViewSummary(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return new VisionCaptureResult("owner view unavailable", "owner view unavailable", "owner.view.idle", EventPriority.P2, 0, "none");
        }
        HitResult hitResult = owner.pick(12.0d, 1.0f, false);
        FocusSummary focusSummary = describeFocus(owner, hitResult);
        String focus = focusSummary.text();
        String biome = owner.level().getBiome(owner.blockPosition())
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
        long dayTime = owner.level().getDayTime() % 24000L;
        String timeLabel = formatTimeLabel(dayTime);
        String heldItem = owner.getMainHandItem().isEmpty() ? "empty_hand" : owner.getMainHandItem().getDisplayName().getString();
        String offhandItem = owner.getOffhandItem().isEmpty() ? "empty_hand" : owner.getOffhandItem().getDisplayName().getString();
        MaidSoulEntityAwarenessService.EnvironmentAwareness awareness = MaidSoulEntityAwarenessService.scan(maid, owner, 10.0d, 4);
        String weather = owner.level().isThundering() ? "thunder" : owner.level().isRaining() ? "rain" : "clear";
        String healthText = owner instanceof Player player
                ? "health=%.1f,hunger=%d".formatted(owner.getHealth(), player.getFoodData().getFoodLevel())
                : "health=%.1f".formatted(owner.getHealth());
        String rawSummary = """
                owner=%s
                position=%d,%d,%d
                rotation=yaw=%.1f,pitch=%.1f
                dimension=%s
                biome=%s
                weather=%s
                time=%s(%d)
                focus=%s
                main_hand=%s
                off_hand=%s
                owner_state=%s
                nearby=%s
                maid_distance=%.1f
                """.formatted(
                owner.getName().getString(),
                owner.blockPosition().getX(),
                owner.blockPosition().getY(),
                owner.blockPosition().getZ(),
                owner.getYRot(),
                owner.getXRot(),
                owner.level().dimension().location(),
                biome,
                weather,
                timeLabel,
                dayTime,
                focus,
                heldItem,
                offhandItem,
                healthText,
                awareness.compactSummary(),
                maid.distanceTo(owner)
        ).trim();
        return new VisionCaptureResult(
                rawSummary,
                rawSummary,
                classifyVisionEventType(awareness, focus),
                classifyVisionPriority(awareness),
                classifyNotableLevel(awareness, focus),
                focusSummary.key() + "|" + awareness.compactSummary()
        );
    }

    /**
     * 描述主人准星正对的目标。
     */
    private static FocusSummary describeFocus(LivingEntity owner, HitResult hitResult) {
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            BlockHitResult blockHit = owner.level().clip(new ClipContext(
                    owner.getEyePosition(),
                    owner.getEyePosition().add(owner.getViewVector(1.0f).scale(12.0d)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    owner
            ));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                return describeBlock(owner, blockHit.getBlockPos());
            }
            return new FocusSummary("focus=none", "none");
        }
        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            String typeId = key == null ? "unknown" : key.toString();
            return new FocusSummary("entity:%s[%s] distance=%.1f".formatted(
                    entity.getName().getString(),
                    typeId,
                    owner.distanceTo(entity)
            ), "entity:" + typeId);
        }
        if (hitResult instanceof BlockHitResult blockHitResult) {
            return describeBlock(owner, blockHitResult.getBlockPos());
        }
        String type = hitResult.getType().name().toLowerCase(Locale.ROOT);
        return new FocusSummary(type, type);
    }

    /**
     * 描述主人正看的方块。
     */
    private static FocusSummary describeBlock(LivingEntity owner, BlockPos blockPos) {
        var state = owner.level().getBlockState(blockPos);
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String blockId = key == null ? "unknown" : key.toString();
        return new FocusSummary("block:%s at %d,%d,%d".formatted(
                blockId,
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ()
        ), "block:" + blockId);
    }

    /**
     * 将 Minecraft 时间转成更像人类理解的标签。
     */
    private static String formatTimeLabel(long dayTime) {
        if (dayTime < 1000) {
            return "dawn";
        }
        if (dayTime < 6000) {
            return "morning";
        }
        if (dayTime < 12000) {
            return "day";
        }
        if (dayTime < 13000) {
            return "sunset";
        }
        if (dayTime < 18000) {
            return "evening";
        }
        return "night";
    }

    /**
     * 判断这次视角变化是否能变成可见发言。
     *
     * 收敛规则：
     * 1. 普通视角只进黑板和 prompt，不主动说；
     * 2. 最近正在聊天时也不让视角抢话；
     * 3. 只有危险视角可以主动提醒主人。
     */
    private static boolean shouldSpeakForVision(EntityMaid maid, VisionCaptureResult result) {
        return result.notableLevel() >= 2 && "owner.view.risk_mob".equals(result.eventType());
    }

    /**
     * 判断这次视角更像哪一类环境事件，方便提示词区分语气。
     */
    private static String classifyVisionEventType(MaidSoulEntityAwarenessService.EnvironmentAwareness awareness, String focus) {
        if (!awareness.riskMobs().isEmpty()) {
            return "owner.view.risk_mob";
        }
        if (!awareness.players().isEmpty()) {
            return "owner.view.player_nearby";
        }
        if (!awareness.cuteAnimals().isEmpty()) {
            return "owner.view.cute_animal";
        }
        if (!awareness.otherMaids().isEmpty()) {
            return "owner.view.other_maid";
        }
        if (focus.contains("entity:") || focus.contains("block:")) {
            return "owner.view.focus_changed";
        }
        return "owner.view.idle";
    }

    /**
     * 为不同视觉场景分配主动回复优先级。
     */
    private static EventPriority classifyVisionPriority(MaidSoulEntityAwarenessService.EnvironmentAwareness awareness) {
        if (!awareness.riskMobs().isEmpty()) {
            return EventPriority.P1;
        }
        if (!awareness.players().isEmpty() || !awareness.otherMaids().isEmpty()) {
            return EventPriority.P1;
        }
        return EventPriority.P2;
    }

    /**
     * 用整数表示“这帧场景有多值得开口”。
     * 0=基本平静，1=聊天窗口内可提，2=即便不聊天也值得提。
     */
    private static int classifyNotableLevel(MaidSoulEntityAwarenessService.EnvironmentAwareness awareness, String focus) {
        if (!awareness.riskMobs().isEmpty() || !awareness.players().isEmpty()) {
            return 2;
        }
        if (!awareness.otherMaids().isEmpty() || !awareness.cuteAnimals().isEmpty()) {
            return 1;
        }
        if (focus.contains("entity:") || focus.contains("block:")) {
            return 1;
        }
        return 0;
    }

    /**
     * 一次完整的视角采样结果。
     */
    private record VisionCaptureResult(
            String rawSummary,
            String interpretedSummary,
            String eventType,
            EventPriority priority,
            int notableLevel,
            String semanticKey
    ) {
        private int signature() {
            return java.util.Objects.hash(eventType, notableLevel, semanticKey);
        }
    }

    private record FocusSummary(String text, String key) {
    }
}
