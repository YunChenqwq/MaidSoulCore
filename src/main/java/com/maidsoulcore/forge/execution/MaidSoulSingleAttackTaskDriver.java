package com.maidsoulcore.forge.execution;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulCombatExecutionController;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import net.minecraft.world.entity.LivingEntity;

/**
 * 单目标攻击驱动器。
 * <p>
 * 规则非常明确：
 * - 一次只锁一只目标
 * - 目标活着且仍可攻击时持续追击
 * - 目标失效或超时则直接结束
 */
public final class MaidSoulSingleAttackTaskDriver implements MaidSoulCombatTaskDriver {
    public static final String ID = "single_attack";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String initialize(EntityMaid maid, IAttackTask attackTask, MaidSoulExecutionSession session) {
        MaidSoulExecutionSession.TargetEntry first = session.targets().isEmpty() ? null : session.targets().get(0);
        if (first == null) {
            session.status(MaidSoulExecutionStatus.FAILED);
            session.lastResult("target_missing");
            return "target_missing";
        }
        LivingEntity target = MaidSoulCombatExecutionController.entityFromId(maid, first.entityId());
        if (!MaidSoulCombatExecutionController.isCandidateValid(maid, attackTask, target, null)) {
            session.status(MaidSoulExecutionStatus.FAILED);
            session.lastResult("target_missing");
            return "target_missing";
        }
        session.lockCurrent(0, target.getId());
        MaidSoulCombatExecutionController.syncAttackTarget(maid, target);
        MaidSoulCombatExecutionController.emitLockEffect(maid, target);
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.execution.combat.single.init",
                "target=" + target.getName().getString() + "#" + target.getId()
        );
        return "target_locked:" + target.getName().getString() + "#" + target.getId();
    }

    @Override
    public void tick(EntityMaid maid, IAttackTask attackTask, MaidSoulExecutionSession session) {
        LivingEntity currentTarget = MaidSoulCombatExecutionController.entityFromId(maid, session.currentTargetId());
        if (MaidSoulCombatExecutionController.isLockedTargetStillValid(maid, attackTask, currentTarget)) {
            MaidSoulCombatExecutionController.ensureAttackTargetMemory(maid, currentTarget);
            MaidSoulCombatExecutionController.emitLockEffect(maid, currentTarget);
            MaidSoulCombatExecutionController.traceLockedTarget(maid, session, currentTarget);
            return;
        }
        if (session.isCurrentTargetTimedOut(MaidSoulCombatExecutionController.targetLockTimeoutMillis())) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.execution.combat.single.timeout",
                    "target=" + session.currentTargetId()
            );
            session.status(MaidSoulExecutionStatus.FAILED);
            session.lastResult("target_timeout");
        } else {
            session.status(MaidSoulExecutionStatus.COMPLETED);
            session.lastResult("target_cleared");
        }
        MaidSoulCombatExecutionController.clearAttackState(maid);
    }
}
