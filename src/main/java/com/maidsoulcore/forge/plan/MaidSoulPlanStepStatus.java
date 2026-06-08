package com.maidsoulcore.forge.plan;

/**
 * 单个计划步骤状态。
 */
public enum MaidSoulPlanStepStatus {
    /**
     * 尚未执行。
     */
    PENDING,
    /**
     * 已发起执行，正在等待动作完成或等待事件。
     */
    RUNNING,
    /**
     * 已完成。
     */
    COMPLETED,
    /**
     * 已失败。
     */
    FAILED
}
