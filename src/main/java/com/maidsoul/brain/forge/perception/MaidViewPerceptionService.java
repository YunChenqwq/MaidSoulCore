package com.maidsoul.brain.forge.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidViewPerceptionService {
    private static final long IDLE_SCAN_INTERVAL_MILLIS = 45_000L;
    private static final long NOTABLE_EVENT_COOLDOWN_MILLIS = 90_000L;
    private static final ConcurrentMap<UUID, Long> LAST_SCAN = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Integer> LAST_SIGNATURE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_NOTABLE_EVENT = new ConcurrentHashMap<>();

    private MaidViewPerceptionService() {
    }

    /**
     * 轻量视角摘要。
     *
     * <p>这里不是做视觉大模型，而是把车万女仆和主人附近最关键的 MC 状态压成结构化
     * 世界事件：主人看向什么、附近是否有怪、时间/天气/生命值如何。只有风险或显著变化
     * 才会送入核心，避免 tick 级刷屏。</p>
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
        if (previous != null && previous == summary.signature()) {
            return;
        }
        if (!summary.notable()) {
            return;
        }
        Long lastNotable = LAST_NOTABLE_EVENT.get(maid.getUUID());
        if (lastNotable != null && now - lastNotable < NOTABLE_EVENT_COOLDOWN_MILLIS) {
            return;
        }
        LAST_NOTABLE_EVENT.put(maid.getUUID(), now);
        MaidBrainRuntimeRegistry.receiveWorldEvent(maid, summary.eventType(), summary.text());
    }

    private static ViewSummary capture(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return new ViewSummary("owner.view.none", "owner unavailable", false);
        }
        String focus = describeFocus(owner);
        List<Monster> monsters = owner.level().getEntitiesOfClass(
                Monster.class,
                owner.getBoundingBox().inflate(10.0D),
                entity -> entity.isAlive() && entity.distanceTo(owner) <= 10.0F
        );
        String time = timeLabel(owner.level().getDayTime() % 24000L);
        String weather = owner.level().isThundering() ? "thunder" : owner.level().isRaining() ? "rain" : "clear";
        String ownerState = owner instanceof Player player
                ? "health=%.1f,hunger=%d".formatted(owner.getHealth(), player.getFoodData().getFoodLevel())
                : "health=%.1f".formatted(owner.getHealth());
        String text = "owner=" + owner.getName().getString()
                + ", focus=" + focus
                + ", time=" + time
                + ", weather=" + weather
                + ", owner_state=" + ownerState
                + ", nearby_monsters=" + monsters.size()
                + ", maid_distance=%.1f".formatted(maid.distanceTo(owner));
        if (!monsters.isEmpty()) {
            return new ViewSummary("owner.view.risk_mob", text, true);
        }
        return new ViewSummary("owner.view.changed", text, false);
    }

    private static String describeFocus(LivingEntity owner) {
        HitResult hit = owner.pick(12.0D, 1.0F, false);
        if (hit instanceof EntityHitResult entityHit) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entityHit.getEntity().getType());
            return "entity:" + (key == null ? "unknown" : key);
        }
        BlockHitResult blockHit;
        if (hit instanceof BlockHitResult directBlock) {
            blockHit = directBlock;
        } else {
            blockHit = owner.level().clip(new ClipContext(
                    owner.getEyePosition(),
                    owner.getEyePosition().add(owner.getViewVector(1.0F).scale(12.0D)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    owner
            ));
        }
        BlockPos pos = blockHit.getBlockPos();
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(owner.level().getBlockState(pos).getBlock());
        return "block:" + (key == null ? "unknown" : key) + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String timeLabel(long dayTime) {
        if (dayTime < 1000) return "dawn";
        if (dayTime < 6000) return "morning";
        if (dayTime < 12000) return "day";
        if (dayTime < 13000) return "sunset";
        if (dayTime < 18000) return "evening";
        return "night";
    }

    private record ViewSummary(String eventType, String text, boolean notable) {
        private int signature() {
            return java.util.Objects.hash(eventType, text);
        }
    }
}
