package com.maidsoulcore.forge.runtime;

/**
 * 聊天时序门决策。
 * <p>
 * 这里不是把大模型的 timing gate 全量搬进来，
 * 而是先在本地运行时里补齐同名语义，方便后续继续扩展。
 */
public enum MaidSoulTimingAction {
    CONTINUE,
    WAIT,
    NO_REPLY,
    FINISH
}
