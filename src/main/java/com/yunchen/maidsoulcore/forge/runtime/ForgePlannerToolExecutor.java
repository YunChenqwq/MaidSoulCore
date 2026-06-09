package com.yunchen.maidsoulcore.forge.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.core.reasoning.PlanDecision;
import com.yunchen.maidsoulcore.core.reasoning.PlannerToolExecutor;
import com.yunchen.maidsoulcore.core.reasoning.ToolResult;
import com.yunchen.maidsoulcore.forge.perception.MaidViewPerceptionService;
import com.yunchen.maidsoulcore.forge.vision.MaidVisionService;
import com.maidsoul.brain.vision.VisionConfig;
import com.yunchen.maidsoulcore.forge.config.ForgeBrainConfigInstaller;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ForgePlannerToolExecutor implements PlannerToolExecutor {
    private final EntityMaid maid;

    public ForgePlannerToolExecutor(EntityMaid maid) {
        this.maid = maid;
    }

    @Override
    public ToolResult execute(PlanDecision call) {
        String name = call == null || call.tool_name == null ? "" : call.tool_name;
        return switch (name) {
            case "get_owner_status" -> ownerStatus();
            case "get_maid_status" -> maidStatus();
            case "get_world_state" -> worldState();
            case "scan_entities" -> scanEntities();
            case "observe_view" -> observeView(call == null ? "" : call.reason);
            default -> new ToolResult(false, name.isBlank() ? "unknown_tool" : name, "Forge 工具不存在或尚未接入。");
        };
    }

    private ToolResult ownerStatus() {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof Player player)) {
            return new ToolResult(false, "get_owner_status", "女仆当前没有可读取的主人实体。");
        }
        String content = "owner=" + player.getName().getString()
                + ", health=" + one(player.getHealth()) + "/" + one(player.getMaxHealth())
                + ", food=" + player.getFoodData().getFoodLevel()
                + ", dimension=" + player.level().dimension().location()
                + ", position=" + one(player.getX()) + "," + one(player.getY()) + "," + one(player.getZ())
                + ", fire=" + player.isOnFire()
                + ", underwater=" + player.isUnderWater();
        return new ToolResult(true, "get_owner_status", content);
    }

    private ToolResult maidStatus() {
        LivingEntity owner = maid.getOwner();
        String distance = owner == null ? "unknown" : one(Math.sqrt(maid.distanceToSqr(owner)));
        String content = "maid=" + maid.getName().getString()
                + ", health=" + one(maid.getHealth()) + "/" + one(maid.getMaxHealth())
                + ", favorability=" + maid.getFavorability()
                + ", owner_distance=" + distance
                + ", dimension=" + maid.level().dimension().location()
                + ", position=" + one(maid.getX()) + "," + one(maid.getY()) + "," + one(maid.getZ());
        return new ToolResult(true, "get_maid_status", content);
    }

    private ToolResult worldState() {
        if (!(maid.level() instanceof ServerLevel level)) {
            return new ToolResult(false, "get_world_state", "当前世界不是服务端世界。");
        }
        String weather = level.isThundering() ? "thunder" : (level.isRaining() ? "rain" : "clear");
        String content = "dimension=" + level.dimension().location()
                + ", day_time=" + level.getDayTime()
                + ", weather=" + weather
                + ", difficulty=" + level.getDifficulty().getKey();
        return new ToolResult(true, "get_world_state", content);
    }

    private ToolResult scanEntities() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int monsters = 0;
        for (Entity entity : maid.level().getEntities(maid, maid.getBoundingBox().inflate(16.0D))) {
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            String key = entity.getType().builtInRegistryHolder().key().location().toString();
            counts.merge(key, 1, Integer::sum);
            if (entity instanceof Monster) {
                monsters++;
            }
        }
        String summary = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(16)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return new ToolResult(true, "scan_entities", "monsters=" + monsters + ", entities=[" + summary + "]");
    }

    private ToolResult observeView(String reason) {
        VisionConfig vision = VisionConfig.load(ForgeBrainConfigInstaller.configRoot());
        if (vision.available()) {
            String summary = MaidVisionService.requestPlannerSummary(maid, reason, vision.timeoutMillis());
            return new ToolResult(true, "observe_view", summary);
        }
        return new ToolResult(true, "observe_view", MaidViewPerceptionService.scanForPlanner(maid, reason));
    }

    private static String one(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
