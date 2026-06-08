package com.maidsoulcore.forge.execution;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 战斗任务驱动器。
 * <p>
 * 首版只抽象一个最小接口：
 * - 初始化时如何写入首个锁定目标
 * - 每 tick 如何推进会话
 * 这样可以先把“动作持续推进”从聊天层拆出来，
 * 后续再继续扩成更完整的执行框架。
 */
public interface MaidSoulCombatTaskDriver {
    /**
     * 返回驱动器 id，用于 trace 和调试。
     */
    String id();

    /**
     * 初始化会话。
     */
    String initialize(EntityMaid maid, IAttackTask attackTask, MaidSoulExecutionSession session);

    /**
     * 每 tick 推进一次执行会话。
     */
    void tick(EntityMaid maid, IAttackTask attackTask, MaidSoulExecutionSession session);
}
