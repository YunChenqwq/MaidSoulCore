package com.maidsoulcore.forge.execution;

/**
 * 计划层与执行层之间传递的标准事件。
 */
public record MaidSoulExecutionEvent(
        String planId,
        int stepIndex,
        String actionType,
        MaidSoulExecutionEventType eventType,
        MaidSoulExecutionStatus status,
        String detail,
        long timestamp
) {
    public static MaidSoulExecutionEvent of(String planId,
                                            int stepIndex,
                                            String actionType,
                                            MaidSoulExecutionEventType eventType,
                                            MaidSoulExecutionStatus status,
                                            String detail) {
        return new MaidSoulExecutionEvent(
                planId,
                stepIndex,
                actionType,
                eventType,
                status,
                detail == null ? "" : detail,
                System.currentTimeMillis()
        );
    }
}
