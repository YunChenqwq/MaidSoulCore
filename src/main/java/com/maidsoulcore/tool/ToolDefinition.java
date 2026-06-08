package com.maidsoulcore.tool;

/**
 * 工具定义元数据。
 *
 * @param name        工具名
 * @param description 工具用途描述
 * @param writeAction 是否会改变游戏状态
 */
public record ToolDefinition(
        String name,
        String description,
        boolean writeAction
) {
}
