package com.maidsoulcore.forge.conversation;

/**
 * 单条运行时消息。
 *
 * @param type 消息类型，决定它是可见对话还是脑内参考
 * @param role 发给模型时使用的角色；reference 通常不会直接按 role 放入对话窗口
 * @param content 清洗后的文本
 * @param source 来源模块，用于调试和人生记忆落盘
 * @param eventType 事件类型；非事件消息为空
 * @param countInContext 是否计入“真实对话窗口”的条数
 * @param createdMillis 创建时间
 */
public record RuntimeMessage(
        RuntimeMessageType type,
        String role,
        String content,
        String source,
        String eventType,
        boolean countInContext,
        long createdMillis
) {
    public boolean visibleDialogue() {
        return type != null && type.visibleDialogue();
    }
}
