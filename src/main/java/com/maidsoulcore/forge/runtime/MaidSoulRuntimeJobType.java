package com.maidsoulcore.forge.runtime;

/**
 * 聊天运行时内部作业类型。
 * <p>
 * 当前先区分两种主作业：
 * - {@link #OWNER_CHAT}：主人主动发来的聊天或命令；
 * - {@link #PROACTIVE_EVENT}：女仆根据环境、视角、受击等事件触发的主动发言。
 */
public enum MaidSoulRuntimeJobType {
    OWNER_CHAT,
    PROACTIVE_EVENT,
    TIMEOUT
}
