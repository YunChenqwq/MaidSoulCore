package com.maidsoulcore.forge.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMeleeAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.maidsoulcore.forge.MaidSoulCoreMod;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * MaidSoulCore 自定义“全局攻击”任务。
 * <p>
 * 这个任务直接复用 TLM 原版 `TaskAttack` 的近战 AI 行为：
 * - 寻路
 * - 追击
 * - 贴身近战
 * - 盾牌使用
 * <p>
 * 但目标判定改成 MaidSoulCore 自己的策略：
 * - 除盔甲架外，允许攻击任意活着的 `LivingEntity`
 * - 不改变原版其他攻击任务；原版攻击任务仍然只走它自己的敌对生物判定
 */
public final class MaidSoulGlobalAttackTask extends TaskAttack {
    /**
     * 全局攻击任务的唯一 ID。
     */
    public static final ResourceLocation UID = new ResourceLocation(MaidSoulCoreMod.MOD_ID, "global_attack");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.NETHERITE_SWORD.getDefaultInstance();
    }

    /**
     * 放宽目标判定。
     * <p>
     * 这里明确不走原版 `IAttackTask.canAttack` 的敌对过滤逻辑，
     * 而是单独采用 MaidSoulCore 自己的“全局攻击白名单”：
     * - 活着
     * - 不是盔甲架
     */
    @Override
    public boolean canAttack(EntityMaid maid, LivingEntity target) {
        return target.isAlive()
                && target != maid
                && target != maid.getOwner()
                && !(target instanceof ArmorStand);
    }

    /**
     * 覆盖原版脑任务，去掉“自动寻找第一个可攻击目标”这一步。
     * <p>
     * 原版 `TaskAttack` 会在没有指定目标时自动扫描最近可攻击实体。
     * 对我们这个全局攻击任务来说，这会导致一旦切进任务，就开始无差别攻击周围所有可打生物。
     * <p>
     * 这里保留：
     * - 已锁定目标后的追击
     * - 近战攻击
     * - 目标失效后的停止
     * 但不再允许任务自己“起手选目标”。
     */
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create(target -> !isWeapon(maid, maid.getMainHandItem()) || farAwayFromAssignedTarget(target, maid));
        BehaviorControl<Mob> moveToTargetTask = SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.6f);
        BehaviorControl<EntityMaid> attackTargetTask = MaidMeleeAttack.create(20);
        MaidUseShieldTask maidUseShieldTask = new MaidUseShieldTask();

        return Lists.newArrayList(
                Pair.of(5, findTargetTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, attackTargetTask),
                Pair.of(5, maidUseShieldTask)
        );
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create(target -> !isWeapon(maid, maid.getMainHandItem()) || farAwayFromAssignedTarget(target, maid));
        BehaviorControl<EntityMaid> attackTargetTask = MaidMeleeAttack.create(20);
        MaidUseShieldTask maidUseShieldTask = new MaidUseShieldTask();

        return Lists.newArrayList(
                Pair.of(5, findTargetTask),
                Pair.of(5, attackTargetTask),
                Pair.of(5, maidUseShieldTask)
        );
    }

    @Override
    public String getMaidActionSummary() {
        return "Melee attack any living target except armor stands, using MaidSoulCore global attack policy.";
    }

    private boolean farAwayFromAssignedTarget(LivingEntity target, EntityMaid maid) {
        if (!target.isAlive()) {
            return true;
        }
        boolean enable = maid.isHomeModeEnable();
        float radius = maid.getRestrictRadius();
        if (!enable && maid.getOwner() != null) {
            return maid.getOwner().distanceTo(target) > radius;
        }
        return maid.distanceTo(target) > radius;
    }
}
