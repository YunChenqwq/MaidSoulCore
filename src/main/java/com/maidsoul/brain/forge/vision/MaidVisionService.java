package com.maidsoul.brain.forge.vision;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.network.ModNetwork;
import com.maidsoul.brain.forge.network.VisionCaptureRequestPacket;
import com.maidsoul.brain.forge.perception.MaidViewPerceptionService;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;
import com.maidsoul.brain.forge.speech.MaidSpeechDispatcher;
import com.maidsoul.brain.vision.VisionConfig;
import com.maidsoul.brain.vision.VisionSummaryClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 服务端视觉入口。
 *
 * <p>默认链路是 client_direct：服务端只请求客户端“看一下”，客户端本地调用视觉模型，
 * 再把文字摘要发回服务端。这样多人服务器不会承受图片包带宽。server_proxy 仅作为备用模式，
 * 用于服务器统一持有视觉 API Key 的场景。</p>
 */
public final class MaidVisionService {
    private static final ConcurrentMap<UUID, Long> LAST_AUTO_REQUEST = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_MANUAL_REQUEST = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, CompletableFuture<String>> PENDING_SUMMARIES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, VisionRequestContext> PENDING_REQUESTS = new ConcurrentHashMap<>();

    private MaidVisionService() {
    }

    public static boolean requestAutoSummary(EntityMaid maid, String sceneHint) {
        return requestSummary(maid, sceneHint, false);
    }

    public static boolean requestManualSummary(EntityMaid maid, String sceneHint) {
        return requestSummary(maid, sceneHint, true);
    }

    public static String requestPlannerSummary(EntityMaid maid, String sceneHint, long timeoutMillis) {
        VisionConfig config = VisionConfig.load(ForgeBrainConfigInstaller.configRoot());
        if (!config.available()) {
            return "[视角摘要] 视觉模型未启用或未配置。";
        }
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return "[视角摘要] 找不到可截图的主人客户端。";
        }
        UUID requestId = UUID.randomUUID();
        CompletableFuture<String> future = new CompletableFuture<>();
        PENDING_SUMMARIES.put(requestId, future);
        rememberRequest(requestId, maid, player, "planner");
        String enrichedSceneHint = MaidViewPerceptionService.sceneHintForVision(maid, sceneHint);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new VisionCaptureRequestPacket(
                        maid.getUUID(),
                        requestId,
                        "planner",
                        enrichedSceneHint,
                        config.maxImageWidth(),
                        config.maxImageHeight(),
                        config.jpegQuality()
                )
        );
        try {
            long waitMillis = Math.max(1000L, timeoutMillis);
            return future.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            forgetRequest(requestId);
            return "[视角摘要] 视觉观察超时，暂时没有拿到当前画面。";
        } catch (InterruptedException e) {
            forgetRequest(requestId);
            Thread.currentThread().interrupt();
            return "[视角摘要] 视觉观察被中断。";
        } catch (ExecutionException e) {
            forgetRequest(requestId);
            String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            return "[视角摘要] 视觉观察失败：" + clip(message, 160);
        }
    }

    public static void receiveSummary(ServerPlayer player, UUID maidUuid, UUID requestId, String reason, String summary, String sceneHint) {
        VisionRequestContext requestContext = requestContext(requestId, maidUuid, player, reason);
        EntityMaid maid = findRequestMaid(player, requestContext);
        if (maid == null) {
            notifyMissingMaid(player, reason);
            completePending(requestId, "[视角摘要] 没有找到可绑定的女仆。");
            return;
        }
        String cleanSummary = summary == null ? "" : summary.trim();
        if (cleanSummary.isBlank()) {
            completePending(requestId, "[视角摘要] 视觉摘要是空的。");
            if ("manual".equals(reason)) {
                MaidSpeechDispatcher.queueSpeechOnServer(maid, "唔，视觉摘要是空的。");
            }
            return;
        }
        completePending(requestId, cleanSummary);
        String detail = "source=" + reason
                + ", owner=" + player.getName().getString()
                + ", request_maid_uuid=" + requestContext.maidUuid()
                + ", scene_hint=" + clip(sceneHint, 300)
                + ", summary=" + clip(cleanSummary, 1200);
        MaidBrainRuntimeRegistry.receiveWorldEvent(maid, "owner.view.vision_summary", detail);
        if ("manual".equals(reason)) {
            MaidSpeechDispatcher.queueSpeechOnServer(maid, cleanSummary.startsWith("[视觉摘要失败]")
                    ? cleanSummary
                    : "我看到了：" + cleanSummary);
        }
    }

    public static void receiveProxyImage(ServerPlayer player, UUID maidUuid, UUID requestId, String reason, String imageFormat, String imageBase64, String sceneHint) {
        VisionConfig config = VisionConfig.load(ForgeBrainConfigInstaller.configRoot());
        if (!config.available()) {
            player.sendSystemMessage(Component.literal("MaidSoulCore 视觉模型未启用或未配置。"));
            completePending(requestId, "[视角摘要] 视觉模型未启用或未配置。");
            return;
        }
        VisionRequestContext requestContext = requestContext(requestId, maidUuid, player, reason);
        EntityMaid maid = findRequestMaid(player, requestContext);
        if (maid == null) {
            notifyMissingMaid(player, reason);
            completePending(requestId, "[视角摘要] 没有找到可绑定的女仆。");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String summary = new VisionSummaryClient(config).summarize(imageFormat, imageBase64, sceneHint);
                receiveSummary(player, maidUuid, requestId, reason, summary, sceneHint);
            } catch (RuntimeException e) {
                MaidSoulCoreForgeMod.LOGGER.warn("MaidSoulCore server proxy vision summary failed for maid {}", maidUuid, e);
                completePending(requestId, "[视角摘要失败] " + clip(e.getMessage(), 160));
                if ("manual".equals(reason)) {
                    MaidSpeechDispatcher.queueSpeechOnServer(maid, "唔，视觉摘要失败了：" + clip(e.getMessage(), 90));
                }
            }
        });
    }

    private static boolean requestSummary(EntityMaid maid, String sceneHint, boolean manual) {
        VisionConfig config = VisionConfig.load(ForgeBrainConfigInstaller.configRoot());
        if (!config.available()) {
            return false;
        }
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        UUID maidUuid = maid.getUUID();
        long now = System.currentTimeMillis();
        ConcurrentMap<UUID, Long> cooldownMap = manual ? LAST_MANUAL_REQUEST : LAST_AUTO_REQUEST;
        long cooldown = manual ? config.manualCooldownMillis() : config.autoCooldownMillis();
        Long last = cooldownMap.get(maidUuid);
        if (last != null && now - last < cooldown) {
            return false;
        }
        cooldownMap.put(maidUuid, now);
        UUID requestId = UUID.randomUUID();
        rememberRequest(requestId, maid, player, manual ? "manual" : "auto");
        String enrichedSceneHint = MaidViewPerceptionService.sceneHintForVision(maid, sceneHint);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new VisionCaptureRequestPacket(
                        maidUuid,
                        requestId,
                        manual ? "manual" : "auto",
                        enrichedSceneHint,
                        config.maxImageWidth(),
                        config.maxImageHeight(),
                        config.jpegQuality()
                )
        );
        return true;
    }

    private static void completePending(UUID requestId, String summary) {
        if (requestId == null) {
            return;
        }
        CompletableFuture<String> future = PENDING_SUMMARIES.remove(requestId);
        PENDING_REQUESTS.remove(requestId);
        if (future != null) {
            future.complete(summary);
        }
    }

    private static void rememberRequest(UUID requestId, EntityMaid maid, ServerPlayer player, String reason) {
        if (requestId == null || maid == null || player == null) {
            return;
        }
        PENDING_REQUESTS.put(requestId, new VisionRequestContext(
                maid.getUUID(),
                player.getUUID(),
                reason == null ? "" : reason
        ));
    }

    private static void forgetRequest(UUID requestId) {
        if (requestId == null) {
            return;
        }
        PENDING_SUMMARIES.remove(requestId);
        PENDING_REQUESTS.remove(requestId);
    }

    private static VisionRequestContext requestContext(UUID requestId, UUID packetMaidUuid, ServerPlayer player, String reason) {
        VisionRequestContext pending = requestId == null ? null : PENDING_REQUESTS.get(requestId);
        if (pending != null) {
            return pending;
        }
        return new VisionRequestContext(
                packetMaidUuid,
                player == null ? null : player.getUUID(),
                reason == null ? "" : reason
        );
    }

    private static EntityMaid findRequestMaid(ServerPlayer player, VisionRequestContext requestContext) {
        if (player == null || requestContext == null || requestContext.maidUuid() == null) {
            return null;
        }
        // 世界事件归属按请求上下文走：哪只女仆发起了视觉请求，摘要就写回哪只女仆的 runtime。
        // 玩家只是截图客户端/回包来源，不能用“玩家附近有什么女仆”来决定世界事件写给谁。
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(requestContext.maidUuid());
            if (entity instanceof EntityMaid maid && canWriteForRequestOwner(requestContext, maid)) {
                return maid;
            }
        }
        for (EntityMaid maid : player.level().getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(64.0D))) {
            if (!maid.getUUID().equals(requestContext.maidUuid())) {
                continue;
            }
            if (canWriteForRequestOwner(requestContext, maid)) {
                return maid;
            }
        }
        return null;
    }

    private static boolean canWriteForRequestOwner(VisionRequestContext requestContext, EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null || requestContext.ownerUuid() == null || owner.getUUID().equals(requestContext.ownerUuid());
    }

    private static void notifyMissingMaid(ServerPlayer player, String reason) {
        // auto 是后台 45 秒摘要，不能因为一次异步回包定位失败就刷玩家聊天。
        if (!"auto".equals(reason)) {
            player.sendSystemMessage(Component.literal("没有找到可写入视觉摘要的女仆。"));
        }
    }

    private static String clip(String text, int max) {
        String clean = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private record VisionRequestContext(UUID maidUuid, UUID ownerUuid, String reason) {
    }
}
