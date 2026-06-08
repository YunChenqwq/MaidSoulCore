package com.maidsoulcore.forge.runtime;

/**
 * 女仆聊天运行时状态。
 * <p>
 * 这部分直接借鉴了 MaiBot r-dev 的显式运行时状态思路，
 * 但落地到 Forge/TLM 场景时只保留当前确实需要的三种主状态：
 * - {@link #RUNNING}：当前正在处理一轮聊天或命令；
 * - {@link #WAIT}：本轮处理结束，等待下一条外部输入；
 * - {@link #STOP}：当前没有有效任务，也没有待处理输入。
 */
public enum MaidSoulRuntimeMode {
    RUNNING,
    WAIT,
    STOP
}
