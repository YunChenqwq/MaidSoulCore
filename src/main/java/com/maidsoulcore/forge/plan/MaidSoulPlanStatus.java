package com.maidsoulcore.forge.plan;

/**
 * 计划整体状态。
 */
public enum MaidSoulPlanStatus {
    /**
     * 已入队，尚未成为当前计划。
     */
    QUEUED,
    /**
     * 当前计划正在推进。
     */
    RUNNING,
    /**
     * 当前计划被更高优先级计划暂时顶掉，后续会恢复。
     */
    PAUSED,
    /**
     * 所有步骤已完成。
     */
    COMPLETED,
    /**
     * 当前计划失败。
     */
    FAILED,
    /**
     * 当前计划被取消。
     */
    CANCELLED
}
