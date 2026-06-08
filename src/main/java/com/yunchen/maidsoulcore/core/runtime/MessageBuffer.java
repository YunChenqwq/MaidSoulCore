package com.yunchen.maidsoulcore.core.runtime;

import com.yunchen.maidsoulcore.core.message.RuntimeMessage;

import java.util.ArrayList;
import java.util.List;

public final class MessageBuffer {
    private static final int MAX_CACHE_SIZE = 200;
    private final List<RuntimeMessage> allMessages = new ArrayList<>();
    private int lastProcessedIndex;

    public synchronized void append(RuntimeMessage message) {
        if (message == null || message.content().isBlank()) {
            return;
        }
        allMessages.add(message);
        pruneProcessedPrefix();
    }

    public synchronized boolean hasPendingMessages() {
        for (int i = lastProcessedIndex; i < allMessages.size(); i++) {
            if (isActionable(allMessages.get(i))) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<RuntimeMessage> collectPendingMessages() {
        if (!hasPendingMessages()) {
            return List.of();
        }
        List<RuntimeMessage> pending = new ArrayList<>();
        for (int i = lastProcessedIndex; i < allMessages.size(); i++) {
            RuntimeMessage message = allMessages.get(i);
            if (isActionable(message)) {
                pending.add(message);
            }
        }
        lastProcessedIndex = allMessages.size();
        pruneProcessedPrefix();
        return pending;
    }

    public synchronized List<RuntimeMessage> latestCounted(int maxCount) {
        List<RuntimeMessage> selected = new ArrayList<>();
        int counted = 0;
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            RuntimeMessage message = allMessages.get(i);
            selected.add(message);
            if (message.countInContext()) {
                counted++;
                if (counted >= maxCount) {
                    break;
                }
            }
        }
        java.util.Collections.reverse(selected);
        return selected;
    }

    public synchronized RuntimeMessage findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            RuntimeMessage message = allMessages.get(i);
            if (id.equals(message.id())) {
                return message;
            }
        }
        return null;
    }

    public synchronized RuntimeMessage latestUserMessage() {
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            RuntimeMessage message = allMessages.get(i);
            if (message.role() == com.yunchen.maidsoulcore.core.message.DialogueRole.USER) {
                return message;
            }
        }
        return null;
    }

    private void pruneProcessedPrefix() {
        int excess = allMessages.size() - MAX_CACHE_SIZE;
        int removable = Math.min(Math.max(0, excess), lastProcessedIndex);
        if (removable <= 0) {
            return;
        }
        allMessages.subList(0, removable).clear();
        lastProcessedIndex -= removable;
    }

    /**
     * 只有玩家输入和世界事件会触发新一轮规划。
     *
     * <p>assistant 输出和 planner thought 只是历史材料，不能被当成新的待处理消息；
     * 否则运行时会在每次回复后又自己唤起一轮 planner，造成重复 wait、重复情绪写入，
     * 甚至在下一条用户消息到来时把上一轮的 affect_event 写进当前状态。</p>
     */
    private static boolean isActionable(RuntimeMessage message) {
        return message.role() == com.yunchen.maidsoulcore.core.message.DialogueRole.USER
                || message.role() == com.yunchen.maidsoulcore.core.message.DialogueRole.SYSTEM;
    }
}
