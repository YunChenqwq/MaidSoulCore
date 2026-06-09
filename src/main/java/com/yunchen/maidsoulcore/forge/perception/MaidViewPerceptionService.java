package com.yunchen.maidsoulcore.forge.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.runtime.MaidBrainRuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidViewPerceptionService {
    private static final long IDLE_SCAN_INTERVAL_MILLIS = 45_000L;
    private static final double FOCUS_RANGE = 12.0D;
    private static final double NEARBY_ENTITY_RANGE = 16.0D;
    private static final double ENTITY_FOCUS_DOT = 0.965D;
    private static final ConcurrentMap<UUID, Long> LAST_SCAN = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Integer> LAST_SIGNATURE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> LAST_WEATHER = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> LAST_TIME = new ConcurrentHashMap<>();

    private MaidViewPerceptionService() {
    }

    /**
     * 轻量视角摘要。
     *
     * <p>这里不直接强制调用视觉模型，而是每 45 秒把主人和当前女仆附近的
     * Minecraft 结构化状态压成世界事件。planner 可以直接使用这些现场事实；
     * 如果它判断需要真实画面，再主动调用 observe_view 触发截图/VLM。</p>
     */
    public static void onMaidTick(EntityMaid maid) {
        if (maid.tickCount % 40 != 0 || maid.getOwner() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastScan = LAST_SCAN.get(maid.getUUID());
        if (lastScan != null && now - lastScan < IDLE_SCAN_INTERVAL_MILLIS) {
            return;
        }
        LAST_SCAN.put(maid.getUUID(), now);

        ViewSummary summary = capture(maid);
        Integer previous = LAST_SIGNATURE.put(maid.getUUID(), summary.signature());
        String previousWeather = LAST_WEATHER.put(maid.getUUID(), summary.weather());
        String previousTime = LAST_TIME.put(maid.getUUID(), summary.time());
        boolean changed = previous == null || previous != summary.signature();
        boolean weatherChanged = previousWeather != null && !previousWeather.equals(summary.weather());
        boolean timeChanged = previousTime != null && !previousTime.equals(summary.time());
        String eventType = weatherChanged
                ? "owner.view.risk_weather_changed"
                : timeChanged ? "owner.view.risk_time_changed" : summary.notable()
                ? summary.eventType()
                : changed ? "owner.view.changed" : "owner.view.snapshot";
        MaidBrainRuntimeRegistry.receiveWorldEvent(maid, eventType, summary.text());
    }

    /**
     * 给 planner 的 scan_environment 工具使用。
     *
     * <p>这份结果来自服务端 MC 状态，不经过视觉模型。它能可靠回答“主人是不是看着
     * 当前女仆”“附近有没有怪物”“当前焦点是否只是空气”等问题。</p>
     */
    public static String scanForPlanner(EntityMaid maid, String reason) {
        ViewSummary summary = capture(maid);
        String normalizedReason = reason == null || reason.isBlank() ? "planner requested environment scan" : reason.trim();
        return "reason=" + normalizedReason + ", " + summary.text();
    }

    /**
     * 给视觉模型的 sceneHint 使用。
     *
     * <p>VLM 只看截图时很容易把玩家视角里的女仆当成“陌生女仆”。这里把请求女仆
     * 的身份、主人视线焦点和结构化现场事实一并塞进提示，要求模型优先服从 MC 状态。</p>
     */
    public static String sceneHintForVision(EntityMaid maid, String rawHint) {
        ViewSummary summary = capture(maid);
        String hint = rawHint == null || rawHint.isBlank() ? "" : rawHint.trim();
        return "vision_request_context={request_maid_uuid=" + maid.getUUID()
                + ", request_maid_name=" + maid.getName().getString()
                + ", rule=如果结构化状态显示 owner_looking_at_request_maid=true，则截图里被主人看着的女仆就是当前说话的女仆/你自己的身体，不要称为陌生女仆。"
                + "}; structured_scene={" + summary.text() + "}"
                + (hint.isBlank() ? "" : "; planner_reason=" + hint);
    }

    private static ViewSummary capture(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return new ViewSummary("owner.view.none", "maid_uuid=" + maid.getUUID() + ", owner=unavailable", false, "unknown", "unknown");
        }
        FocusInfo focus = describeFocus(owner, maid);
        List<Monster> monsters = owner.level().getEntitiesOfClass(
                Monster.class,
                owner.getBoundingBox().inflate(10.0D),
                entity -> entity.isAlive() && entity.distanceTo(owner) <= 10.0F
        );
        List<Entity> nearbyEntities = owner.level().getEntities(
                owner,
                owner.getBoundingBox().inflate(NEARBY_ENTITY_RANGE),
                entity -> entity.isAlive() && entity.distanceTo(owner) <= NEARBY_ENTITY_RANGE
        );
        List<EntityMaid> nearbyMaids = owner.level().getEntitiesOfClass(
                EntityMaid.class,
                owner.getBoundingBox().inflate(NEARBY_ENTITY_RANGE),
                entity -> entity.isAlive() && entity.distanceTo(owner) <= NEARBY_ENTITY_RANGE
        );
        String time = timeLabel(owner.level().getDayTime() % 24000L);
        String weather = owner.level().isThundering() ? "thunder" : owner.level().isRaining() ? "rain" : "clear";
        List<String> ownerRisks = ownerRisks(owner);
        String ownerState = owner instanceof Player player
                ? "health=%.1f,hunger=%d".formatted(owner.getHealth(), player.getFoodData().getFoodLevel())
                : "health=%.1f".formatted(owner.getHealth());
        boolean ownerLookingAtRequestMaid = focus.ownerLookingAtRequestMaid();
        String text = "maid_uuid=" + maid.getUUID()
                + ", maid_name=" + maid.getName().getString()
                + ", owner=" + owner.getName().getString()
                + ", owner_looking_at_request_maid=" + ownerLookingAtRequestMaid
                + ", focus=" + focus.description()
                + ", time=" + time
                + ", weather=" + weather
                + ", owner_state=" + ownerState
                + ", owner_risks=" + ownerRisks
                + ", nearby_monsters=" + monsters.size()
                + ", nearby_maids=" + nearbyMaids.size()
                + ", nearby_entities_count=" + nearbyEntities.size()
                + ", nearby_entities=" + nearbyEntitiesText(owner, maid, nearbyEntities)
                + ", maid_distance=%.1f".formatted(maid.distanceTo(owner));
        if (!ownerRisks.isEmpty()) {
            return new ViewSummary("owner.view.risk_owner_state", text, true, time, weather);
        }
        if (!monsters.isEmpty()) {
            String monstersText = monsters.stream()
                    .sorted(Comparator.comparingDouble(entity -> entity.distanceTo(owner)))
                    .limit(3)
                    .map(entity -> entityTypeId(entity) + "@%.1f".formatted(entity.distanceTo(owner)))
                    .toList()
                    .toString();
            return new ViewSummary("owner.view.risk_mob", text + ", nearest_monsters=" + monstersText, true, time, weather);
        }
        if (!"clear".equals(weather)) {
            return new ViewSummary("owner.view.risk_weather", text, true, time, weather);
        }
        if ("night".equals(time)) {
            return new ViewSummary("owner.view.risk_time", text, true, time, weather);
        }
        return new ViewSummary("owner.view.changed", text, false, time, weather);
    }

    private static List<String> ownerRisks(LivingEntity owner) {
        List<String> risks = new ArrayList<>();
        if (owner.getHealth() <= 8.0F) {
            risks.add("low_health");
        }
        if (owner instanceof Player player && player.getFoodData().getFoodLevel() <= 6) {
            risks.add("low_hunger");
        }
        if (owner.isOnFire()) {
            risks.add("on_fire");
        }
        if (owner.isInWaterOrBubble()) {
            risks.add("in_water");
        }
        return risks;
    }

    private static String nearbyEntitiesText(LivingEntity owner, EntityMaid requestMaid, List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "[]";
        }
        return entities.stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceTo(owner)))
                .limit(10)
                .map(entity -> {
                    String relation = entity.getUUID().equals(requestMaid.getUUID())
                            ? "current_maid_self"
                            : entity instanceof EntityMaid ? "other_maid" : entity instanceof Monster ? "monster" : entity instanceof LivingEntity ? "living" : "entity";
                    return relation + ":" + entityTypeId(entity) + "@%.1f".formatted(entity.distanceTo(owner));
                })
                .toList()
                .toString();
    }

    private static FocusInfo describeFocus(LivingEntity owner, EntityMaid requestMaid) {
        Entity focusedEntity = focusedEntity(owner);
        if (focusedEntity != null) {
            boolean isRequestMaid = focusedEntity.getUUID().equals(requestMaid.getUUID());
            String relation = isRequestMaid ? "current_maid_self" : focusedEntity instanceof EntityMaid ? "other_maid" : "entity";
            return new FocusInfo(
                    relation + ":" + entityTypeId(focusedEntity)
                            + ",uuid=" + focusedEntity.getUUID()
                            + ",distance=%.1f".formatted(focusedEntity.distanceTo(owner)),
                    isRequestMaid
            );
        }

        HitResult hit = owner.pick(FOCUS_RANGE, 1.0F, false);
        BlockHitResult blockHit = hit instanceof BlockHitResult directBlock
                ? directBlock
                : owner.level().clip(new ClipContext(
                owner.getEyePosition(),
                owner.getEyePosition().add(owner.getViewVector(1.0F).scale(FOCUS_RANGE)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                owner
        ));
        if (blockHit.getType() == HitResult.Type.MISS) {
            return FocusInfo.NONE;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = owner.level().getBlockState(pos);
        if (state.isAir()) {
            return FocusInfo.NONE;
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return new FocusInfo("block:" + (key == null ? "unknown" : key)
                + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ(), false);
    }

    private static Entity focusedEntity(LivingEntity owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getViewVector(1.0F).normalize();
        return owner.level().getEntities(owner, owner.getBoundingBox().inflate(FOCUS_RANGE), entity -> entity.isAlive() && entity.isPickable())
                .stream()
                .map(entity -> new FocusCandidate(entity, focusScore(eye, look, entity)))
                .filter(candidate -> candidate.score() > 0.0D)
                .max(Comparator.comparingDouble(FocusCandidate::score))
                .map(FocusCandidate::entity)
                .orElse(null);
    }

    private static double focusScore(Vec3 eye, Vec3 look, Entity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        Vec3 offset = center.subtract(eye);
        double distance = offset.length();
        if (distance <= 0.01D || distance > FOCUS_RANGE) {
            return 0.0D;
        }
        double dot = offset.normalize().dot(look);
        if (dot < ENTITY_FOCUS_DOT) {
            return 0.0D;
        }
        return dot / Math.max(1.0D, distance);
    }

    private static String entityTypeId(Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? "unknown" : key.toString();
    }

    private static String timeLabel(long dayTime) {
        if (dayTime < 1000) return "dawn";
        if (dayTime < 6000) return "morning";
        if (dayTime < 12000) return "day";
        if (dayTime < 13000) return "sunset";
        if (dayTime < 18000) return "evening";
        return "night";
    }

    private record ViewSummary(String eventType, String text, boolean notable, String time, String weather) {
        private int signature() {
            return java.util.Objects.hash(eventType, text);
        }
    }

    private record FocusInfo(String description, boolean ownerLookingAtRequestMaid) {
        private static final FocusInfo NONE = new FocusInfo("none", false);
    }

    private record FocusCandidate(Entity entity, double score) {
    }
}


