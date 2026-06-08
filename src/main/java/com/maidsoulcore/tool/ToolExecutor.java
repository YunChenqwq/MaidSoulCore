package com.maidsoulcore.tool;

/**
 * 工具执行器抽象。
 * <p>
 * 未来这里会承接真正的动作执行、权限控制、冷却和失败回退。
 */
public interface ToolExecutor {
    /**
     * 执行一次工具调用。
     */
    Object execute(ToolCall call);
}
