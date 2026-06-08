package com.maidsoulcore.runtime;

import java.time.Duration;

/**
 * 运行时基础配置。
 * <p>
 * 这些配置主要服务于事件合并、Planner 超时、主动轮询和调试缓存。
 */
public record RuntimeConfig(
        Duration combatMergeWindow,
        Duration plannerTimeout,
        Duration proactiveTickInterval,
        int traceBufferSize
) {
    /**
     * 返回一套适合当前原型阶段的默认值。
     */
    public static RuntimeConfig defaults() {
        return new RuntimeConfig(
                Duration.ofSeconds(1),
                Duration.ofSeconds(3),
                Duration.ofSeconds(2),
                2048
        );
    }
}
