package com.maidsoulcore.forge.conversation;

/**
 * 运行时消息类型。
 *
 * <p>这里的重点是把“真的说出口的话”和“脑内参考信息”分开。
 * 玩家和女仆可见发言会进入连续对话窗口；事件、视角、情绪、记忆只作为
 * reference 进入提示词，不能伪装成玩家刚刚说的话。</p>
 */
public enum RuntimeMessageType {
    OWNER_VISIBLE(true),
    MAID_VISIBLE(true),
    REFERENCE_EVENT(false),
    REFERENCE_VISION(false),
    REFERENCE_MEMORY(false),
    REFERENCE_EMOTION(false),
    REFERENCE_COGNITION(false),
    REFERENCE_RUNTIME(false);

    private final boolean visibleDialogue;

    RuntimeMessageType(boolean visibleDialogue) {
        this.visibleDialogue = visibleDialogue;
    }

    public boolean visibleDialogue() {
        return visibleDialogue;
    }
}
