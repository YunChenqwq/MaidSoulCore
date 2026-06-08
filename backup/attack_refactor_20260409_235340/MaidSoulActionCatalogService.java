package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * MaidSoulCore 动作候选清单服务。
 * <p>
 * 这个类的职责不是执行动作，而是把当前可用动作、可选任务和附近可攻击目标
 * 整理成稳定文本，供 planner、tool loop 和调试链路复用。
 */
public final class MaidSoulActionCatalogService {
    private MaidSoulActionCatalogService() {
    }

    /**
     * 返回 planner 可选的动作类型说明。
     */
    public static String actionTypeSummary() {
        return """
                NONE: 只聊天，不执行动作
                FOLLOW_ON: 切回跟随主人
                FOLLOW_OFF: 停止跟随，留在当前位置作为 home
                SIT_ON: 原地坐下
                SIT_OFF: 起身站立
                SET_SCHEDULE: 切换日程，action_value 填 DAY / NIGHT / ALL
                SET_TASK: 切换工作任务，action_value 填 task_id
                ENTER_COMBAT: 进入战斗任务并锁定一个明确实体，target_entity_id 必填
                ENTER_COMBAT_GROUP: 进入战斗任务并按实体类型逐个清理，action_value 填实体类型 id，例如 minecraft:zombie
                """.trim();
    }

    /**
     * 返回当前女仆可切换任务清单。
     */
    public static String availableTaskSummary(EntityMaid maid) {
        StringJoiner joiner = new StringJoiner("\n");
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            ResourceLocation id = task.getUid();
            String type = task instanceof IAttackTask ? "attack" : "work";
            joiner.add("- %s [%s]: %s".formatted(id, type, task.getMaidActionSummary()));
        }
        return joiner.length() == 0 ? "(no task)" : joiner.toString();
    }

    /**
     * 返回附近可作为战斗目标的实体摘要。
     */
    public static String nearbyCombatTargetSummary(EntityMaid maid, double radius, int limit) {
        List<Entity> entities = maid.level().getEntities(
                maid,
                maid.getBoundingBox().inflate(radius),
                entity -> entity instanceof LivingEntity && entity.isAlive()
        ).stream().sorted(Comparator.comparingDouble(maid::distanceToSqr)).limit(limit).toList();

        if (entities.isEmpty()) {
            return "(no nearby target)";
        }

        StringJoiner joiner = new StringJoiner("\n");
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living) || living == maid || living == maid.getOwner()) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            String danger = entity instanceof Monster ? "hostile" : "non_hostile";
            joiner.add("- id=%d, name=%s, type=%s, danger=%s, distance=%.1f".formatted(
                    entity.getId(),
                    entity.getName().getString(),
                    key == null ? "unknown" : key,
                    danger,
                    maid.distanceTo(entity)
            ));
        }
        String result = joiner.toString();
        return result.isBlank() ? "(no nearby target)" : result;
    }

    /**
     * 返回供模型参考的简化行为知识。
     */
    public static String builtInSkillSummary() {
        return """
                maid_follow:
                - 主人明确说“跟着我”时，优先 FOLLOW_ON
                - 主人明确说“你留在这里/待在家里”时，优先 FOLLOW_OFF

                maid_sit:
                - 主人明确要求坐下、休息、别乱跑时，可用 SIT_ON
                - 主人要求起来、出发、继续行动时，可用 SIT_OFF

                maid_schedule:
                - 主人要求白天工作，切 DAY
                - 主人要求夜班或晚上守夜，切 NIGHT
                - 主人要求全天工作或一直干活，切 ALL

                maid_combat_single:
                - 主人要求“打那个/锁定那个/先杀那只”时，优先 ENTER_COMBAT
                - 必须提供 target_entity_id
                - 单目标模式一次只锁一个目标，打完或超时后再切下一个

                maid_combat_group:
                - 主人要求“把附近这群僵尸清掉/把苦力怕都清掉”时，优先 ENTER_COMBAT_GROUP
                - action_value 填实体类型 id，例如 minecraft:zombie
                - 群体模式会先筛出同类型目标，再按距离顺序逐个处理

                maid_combat_policy:
                - 若主人要求攻击非敌对生物，优先使用 maidsoulcore:global_attack
                - 原版攻击任务仍然更适合只打敌对目标
                - 如果缺少目标、目标失效或条件不足，要明确反馈原因
                """.trim();
    }
}
