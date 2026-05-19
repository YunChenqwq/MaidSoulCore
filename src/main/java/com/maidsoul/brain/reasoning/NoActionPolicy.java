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
        // 极短冷反馈可以被当作暂不回复；有语义、追问、提醒和投诉的输入不能被吞掉。
        return text.length() >= 4 || text.contains("？") || text.contains("?") || text.contains("吗");
    }
}
