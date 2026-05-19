package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.message.MessageRole;
import com.maidsoul.brain.memory.MemoryContextProvider;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class ContextWindow {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final BrainConfig config;
    private final MemoryContextProvider memoryContextProvider;

    ContextWindow(BrainConfig config, MemoryContextProvider memoryContextProvider) {
        this.config = config;
        this.memoryContextProvider = memoryContextProvider == null ? MemoryContextProvider.none() : memoryContextProvider;
    }

    String render(List<ChatMessage> messages) {
        return render(messages, null);
    }

    String renderForPlanner(List<ChatMessage> messages, DialogueState dialogueState) {
        return render(messages, dialogueState, ContextPurpose.PLANNER);
    }

    String renderForReplyer(List<ChatMessage> messages, DialogueState dialogueState) {
        return render(messages, dialogueState, ContextPurpose.REPLYER);
    }

    String render(List<ChatMessage> messages, DialogueState dialogueState) {
        return render(messages, dialogueState, ContextPurpose.PLANNER);
    }

    private String render(List<ChatMessage> messages, DialogueState dialogueState, ContextPurpose purpose) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前角色：").append(config.identity().botName()).append('\n');
        builder.append("当前玩家：").append(config.identity().ownerName()).append('\n');
        if (dialogueState != null) {
            builder.append("当前短期对话状态：\n")
                    .append(dialogueState.renderForPrompt())
                    .append('\n');
        }
        builder.append("最近聊天记录：\n");
        for (ChatMessage message : messages) {
            if (purpose == ContextPurpose.REPLYER && shouldHideFromReplyer(message)) {
                continue;
            }
            builder.append("- [id=").append(message.id()).append("] ");
            builder.append(TIME_FORMAT.format(message.timestamp())).append(' ');
            if (message.role() == MessageRole.INTERNAL) {
                builder.append("[参考信息]: ");
            } else {
                builder.append(message.speaker()).append(": ");
            }
            builder.append(message.content()).append('\n');
        }
        String latestText = latestVisibleText(messages);
        String memoryBlock = memoryContextProvider.render(latestText);
        if (memoryBlock != null && !memoryBlock.isBlank()) {
            builder.append("\n长期状态参考：\n")
                    .append(memoryBlock)
                    .append('\n');
        }
        return builder.toString();
    }

    private static String latestVisibleText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.role() != MessageRole.INTERNAL) {
                return message.content();
            }
        }
        return "";
    }

    private static boolean shouldHideFromReplyer(ChatMessage message) {
        if (message.role() != MessageRole.INTERNAL) {
            return false;
        }
        String text = message.content() == null ? "" : message.content();
        // 回复器只需要“可用于说话的参考”，不应该看到节奏工具、主动计时和后台观察原文。
        // 这些信息如果泄漏给回复器，模型很容易把“沉默 14 秒、阶段=topic_push”当成可见话题。
        return text.startsWith("[现场观察]")
                || text.startsWith("[主动候选事件]")
                || text.startsWith("[节奏事件]")
                || text.contains("timing_gate")
                || text.contains("主动节奏")
                || text.contains("规划器");
    }

    private enum ContextPurpose {
        PLANNER,
        REPLYER
    }
}
