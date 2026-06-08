package com.maidsoulcore.forge.execution;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulCombatExecutionController;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import net.minecraft.world.entity.LivingEntity;

/**
 * 群体攻击驱动器。
 * <p>
 * 该驱动器的核心约束只有一条：
 * - 同一时刻只允许一个锁定目标参与寻路和攻击
 * 其他候选目标只保留在会话队列里，当前目标完成后再切到下一个。
 */
public final class MaidSoulGroupAttackTaskDriver implements MaidSoulCombatTaskDriver {
    public static final String ID = "group_attack";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String initialize(EntityMaid maid, IAttackTask attackTask, MaidSoulExecutionSession session) {
        if (session.targets().isEmpty()) {
            session.status(MaidSoulExecutionStatus.FAILED);
            session.lastResult("group_target_missing");
            return "group_target_missing";
        }
        for (int index = 0; index < session.targets().size(); index++) {
            MaidSoulExecutionSession.TargetEntry entry = session.targets().get(index);
            LivingEntity target = MaidSoulCombatExecutionController.entityFromId(maid, entry.entityId());
            if (!MaidSoulCombatExecutionController.isCandidateValid(maid, attackTask, target, session.entityTypeFilter())) {
                continue;
            }
            session.lockCurrent(index, target.getId());
            MaidSoulCombatExecutionController.syncAttackTarget(maid, target);
            MaidSoulCombatExecutionController.emitLockEffect(maid, target);
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.execution.combat.group.init",
                    "target=" + target.getName().getString() + "#" + target.getId()
                            + ", queued=" + session.targets().size()
            );
            return "group_locked:" + target.getName().getString() + "#" + target.getId();
        }
        session.status(MaidSoulExecutionStatus.FAILED);
        session.lastResult("group_target_missing");
        return "group_target_missing";
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
                    "maidsoul.execution.combat.group.timeout",
                    "target=" + session.currentTargetId()
            );
        }

        for (int index = session.currentIndex() + 1; index < session.targets().size(); index++) {
            MaidSoulExecutionSession.TargetEntry entry = session.targets().get(index);
            LivingEntity nextTarget = MaidSoulCombatExecutionController.entityFromId(maid, entry.entityId());
            if (!MaidSoulCombatExecutionController.isCandidateValid(maid, attackTask, nextTarget, session.entityTypeFilter())) {
                continue;
            }
            session.lockCurrent(index, nextTarget.getId());
            MaidSoulCombatExecutionController.syncAttackTarget(maid, nextTarget);
            MaidSoulCombatExecutionController.emitLockEffect(maid, nextTarget);
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.execution.combat.group.advance",
                    "target=" + nextTarget.getName().getString() + "#" + nextTarget.getId()
                            + ", remaining=" + session.remainingCount()
            );
            return;
        }

        session.status(MaidSoulExecutionStatus.COMPLETED);
        session.lastResult("group_cleared");
        MaidSoulCombatExecutionController.clearAttackState(maid);
    }
}
