package com.maidsoulcore.forge.execution;

/**
 * 执行会话状态。
 * <p>
 * 当前首版只先服务战斗执行层，因此状态保持精简：
 * - `RUNNING`：任务仍在持续推进
 * - `COMPLETED`：任务自然完成
 * - `FAILED`：任务因目标失效、超时或条件不足而失败
 * - `CANCELLED`：任务被主动取消或被新任务覆盖
 */
public enum MaidSoulExecutionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
