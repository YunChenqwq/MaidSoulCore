package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * 上下文文本格式化工具。
 * <p>
 * 这层故意把“如何把游戏对象压缩成短文本”集中起来，
 * 这样后续想统一风格、限制长度、增加字段时只需要改这里。
 */
public final class MaidSoulContextFormatter {
    private MaidSoulContextFormatter() {
    }

    /**
     * 格式化女仆当前位置与维度信息。
     */
    public static String formatPosition(EntityMaid maid) {
        BlockPos pos = maid.blockPosition();
        return "x=%d, y=%d, z=%d, dimension=%s".formatted(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                maid.level().dimension().location()
        );
    }

    /**
     * 格式化女仆朝向。
     */
    public static String formatRotation(EntityMaid maid) {
        return "yaw=%.1f, pitch=%.1f".formatted(maid.getYRot(), maid.getXRot());
    }

    /**
     * 格式化 home 点、限制中心与作息点。
     */
    public static String formatHomeState(EntityMaid maid) {
        BlockPos restrictCenter = maid.getRestrictCenter();
        BlockPos workPos = maid.getSchedulePos().getWorkPos();
        BlockPos idlePos = maid.getSchedulePos().getIdlePos();
        BlockPos sleepPos = maid.getSchedulePos().getSleepPos();
        return "homeMode=%s, restrict=%s, work=%s, idle=%s, sleep=%s".formatted(
                maid.isHomeModeEnable(),
                formatPos(restrictCenter),
                formatPos(workPos),
                formatPos(idlePos),
                formatPos(sleepPos)
        );
    }

    /**
     * 格式化与主人的相对状态。
     */
    public static String formatOwnerRelation(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return "owner=none";
        }
        return "owner=%s, distance=%.2f, owner_pos=%s".formatted(
                owner.getName().getString(),
                maid.distanceTo(owner),
                formatPos(owner.blockPosition())
        );
    }

    /**
     * 将女仆背包压缩成一段可读摘要。
     * <p>
     * 这里不会把全部 36 格完整展开，而是只显示非空格的前若干项。
     */
    public static String formatInventorySummary(EntityMaid maid, int limit) {
        ItemStackHandler inventory = maid.getMaidInv();
        StringJoiner joiner = new StringJoiner(", ");
        int appended = 0;
        int nonEmptyCount = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            nonEmptyCount++;
            if (appended < limit) {
                joiner.add("%s x%d".formatted(stack.getDisplayName().getString(), stack.getCount()));
                appended++;
            }
        }
        if (nonEmptyCount == 0) {
            return "inventory=empty";
        }
        if (nonEmptyCount > limit) {
            joiner.add("... +" + (nonEmptyCount - limit) + " stacks");
        }
        return joiner.toString();
    }

    /**
     * 格式化主手和副手物品。
     */
    public static String formatHands(EntityMaid maid) {
        return "main=%s, off=%s".formatted(
                formatStack(maid.getMainHandItem()),
                formatStack(maid.getOffhandItem())
        );
    }

    /**
     * 扫描并格式化附近实体。
     */
    public static String formatNearbyEntities(EntityMaid maid, double radius, int limit) {
        List<Entity> entities = maid.level().getEntities(
                maid,
                maid.getBoundingBox().inflate(radius),
                entity -> entity.isAlive()
        ).stream().sorted(Comparator.comparingDouble(maid::distanceToSqr)).limit(limit).toList();

        if (entities.isEmpty()) {
            return "nearby=none";
        }

        StringJoiner joiner = new StringJoiner("; ");
        for (Entity entity : entities) {
            String threat = entity instanceof Monster ? "hostile" : "neutral";
            joiner.add("%s[%s] dist=%s".formatted(
                    entity.getName().getString(),
                    threat,
                    String.format(Locale.ROOT, "%.1f", maid.distanceTo(entity))
            ));
        }
        return joiner.toString();
    }

    private static String formatStack(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getDisplayName().getString() + " x" + stack.getCount();
    }

    private static String formatPos(BlockPos pos) {
        return "(%d,%d,%d)".formatted(pos.getX(), pos.getY(), pos.getZ());
    }
}
