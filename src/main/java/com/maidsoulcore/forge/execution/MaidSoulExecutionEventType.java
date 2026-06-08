package com.maidsoulcore.forge.execution;

/**
 * 执行层统一事件类型。
 */
public enum MaidSoulExecutionEventType {
    STEP_DISPATCHED,
    STEP_STARTED,
    STEP_RUNNING,
    STEP_COMPLETED,
    STEP_FAILED,
    STEP_CANCELLED
}
