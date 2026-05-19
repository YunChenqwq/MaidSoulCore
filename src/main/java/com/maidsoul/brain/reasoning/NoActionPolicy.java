package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.message.MessageRole;

import java.util.List;

/**
 * no_action 保护策略。
 *
 * <p>规划器可以选择沉默，但不能把真实用户输入、修复反馈或明确追问吞掉。
 * 这类判断独立出来后，主推理循环只负责执行策略结果，不再把一串关键词写在循环里。</p>
 */
final class NoActionPolicy {
    boolean shouldOverrideForUserInput(List<ChatMessage> pending, DialogueState dialogueState) {
        ChatMessage latestUser = null;
        for (ChatMessage message : pending) {
            if (message.role() == MessageRole.USER) {
                latestUser = message;
            }
        }
        if (latestUser == null) {
            return false;
        }
        String text = latestUser.content() == null ? "" : latestUser.content().trim();
        if (text.isBlank()) {
            return false;
        }
        if (dialogueState != null && dialogueState.mode() != DialogueMode.NORMAL_CHAT) {
            return true;
        }
        // 私聊里真实用户输入不能因为“太短”就被吞掉；只允许纯标点/空白这类无语义占位沉默。
        return hasSemanticCharacter(text) || text.contains("？") || text.contains("?");
    }

    private static boolean hasSemanticCharacter(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }
}
