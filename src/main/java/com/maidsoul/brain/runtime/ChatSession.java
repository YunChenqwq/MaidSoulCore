package com.maidsoul.brain.runtime;

import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.message.MessageRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 会话级状态容器。
 *
 * <p>这里保存“同一个人正在经历的聊天现场”：完整历史、待处理消息、当前等待状态和强制继续标记。
 * 运行时只围绕这个对象推进，不允许其它模块绕过它单独说话。</p>
 */
public final class ChatSession {
    private static final int MAX_CACHE_SIZE = 200;

    private final List<ChatMessage> messageCache = new ArrayList<>();
    private final List<ChatMessage> history = new ArrayList<>();
    private int lastProcessedIndex;
    private boolean forceNextContinue;
    private String forceReason = "";

    public synchronized void appendIncoming(ChatMessage message) {
        messageCache.add(message);
        pruneProcessedCache();
    }

    public synchronized boolean hasPendingMessages() {
        return lastProcessedIndex < messageCache.size();
    }

    public synchronized int pendingCount() {
        return Math.max(0, messageCache.size() - lastProcessedIndex);
    }

    public synchronized List<ChatMessage> collectPendingMessages() {
        List<ChatMessage> pending = messageCache.subList(lastProcessedIndex, messageCache.size());
        List<ChatMessage> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ChatMessage message : pending) {
            if (seen.add(message.id())) {
                unique.add(message);
            }
        }
        lastProcessedIndex = messageCache.size();
        return unique;
    }

    public synchronized void ingest(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            history.add(message);
        }
    }

    public synchronized void appendAssistant(ChatMessage message) {
        history.add(message);
    }

    public synchronized void appendInternal(String content) {
        history.add(ChatMessage.internal(content));
    }

    public synchronized void appendReference(String content) {
        history.add(ChatMessage.reference(content));
    }

    public synchronized Optional<ChatMessage> findMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.id().equals(messageId)) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<ChatMessage> latestUserMessage() {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.role() == MessageRole.USER) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    public synchronized List<ChatMessage> contextWindow(int maxCount) {
        List<ChatMessage> counted = history.stream()
                .filter(ChatMessage::countInContext)
                .toList();
        int from = Math.max(0, counted.size() - Math.max(1, maxCount));
        return new ArrayList<>(counted.subList(from, counted.size()));
    }

    public synchronized void forceNextContinue(String reason) {
        forceNextContinue = true;
        forceReason = reason == null ? "" : reason;
    }

    public synchronized boolean hasForcedContinue() {
        return forceNextContinue;
    }

    public synchronized String consumeForceReason() {
        if (!forceNextContinue) {
            return "";
        }
        forceNextContinue = false;
        String reason = forceReason;
        forceReason = "";
        return reason;
    }

    private void pruneProcessedCache() {
        int excess = messageCache.size() - MAX_CACHE_SIZE;
        if (excess <= 0 || lastProcessedIndex <= 0) {
            return;
        }
        int removable = Math.min(excess, lastProcessedIndex);
        messageCache.subList(0, removable).clear();
        lastProcessedIndex -= removable;
    }
}
