package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.message.MessageRole;

import java.util.List;

/**
 * 普通用户消息快路径策略。
 *
 * <p>maibotdev 的完整工具环适合需要等、查记忆、主动节奏和复杂决策的场景；
 * 但日常一句一回如果每次都 planner + replyer 串行，会把聊天拖得很慢。
 * 这个策略只让不需要工具的普通用户输入直达回复器，涉及记忆/画像/主动事件时仍走规划器。</p>
 */
final class DirectReplyPolicy {
    boolean canReplyDirectly(List<ChatMessage> pendingMessages) {
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return false;
        }
        if (pendingMessages.size() > 1) {
            // 多条合批输入往往包含补充、改口或情绪升级，交给规划器先统一判断更稳。
            return false;
        }
        boolean hasUserMessage = false;
        StringBuilder userText = new StringBuilder();
        for (ChatMessage message : pendingMessages) {
            if (message.role() == MessageRole.INTERNAL) {
                return false;
            }
            if (message.role() == MessageRole.USER) {
                hasUserMessage = true;
                userText.append(message.content()).append('\n');
            }
        }
        if (!hasUserMessage) {
            return false;
        }
        String text = userText.toString();
        // 这些问题需要工具或完整规划器参与，不能为了速度直接跳过。
        return !containsAny(text,
                "记得", "还记得", "之前", "上次", "我说过", "我的偏好", "用户画像",
                "查一下", "搜索", "工具", "启动", "配置", "施工方案"
        );
    }

    private static boolean containsAny(String text, String... needles) {
        String value = text == null ? "" : text;
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
