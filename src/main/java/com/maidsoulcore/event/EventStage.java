package com.maidsoulcore.event;

/**
 * 调试链路中的事件处理阶段。
 * <p>
 * trace 会记录事件经过了哪个阶段，
 * 便于判断问题卡在采集、规划还是执行。
 */
public enum EventStage {
    INGEST,
    NORMALIZE,
    GATE,
    PLAN,
    EXECUTE,
    REPLY
}
