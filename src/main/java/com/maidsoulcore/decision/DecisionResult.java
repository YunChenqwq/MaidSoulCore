package com.maidsoulcore.decision;

/**
 * 决策闸门输出结果。
 *
 * @param route  本次事件应走的处理路径
 * @param reason 给调试和日志系统看的简短原因
 */
public record DecisionResult(
        DecisionRoute route,
        String reason
) {
}
