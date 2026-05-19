package com.maidsoul.brain.llm.message;

/**
 * 统一的模型消息角色。
 *
 * <p>这一层对应模型请求载荷，不等同于业务里的玩家/角色消息。后续 Planner、Replyer、子代理都会使用它。</p>
 */
public enum RoleType {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String wireName;

    RoleType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}

