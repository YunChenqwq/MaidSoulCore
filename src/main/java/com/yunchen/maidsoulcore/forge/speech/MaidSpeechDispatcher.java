package com.yunchen.maidsoulcore.forge.speech;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.config.SplitterConfig;
import com.maidsoul.brain.text.SentenceSplitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidSpeechDispatcher {
    private static final long STALE_MILLIS = 30_000L;
    private static final SplitterConfig BUBBLE_SPLITTER = new SplitterConfig(true, 90, 6, 2, 0L);
    private static final SentenceSplitter SPLITTER = new SentenceSplitter(BUBBLE_SPLITTER);
    private static final ConcurrentMap<UUID, PendingSpeech> PENDING = new ConcurrentHashMap<>();

    private MaidSpeechDispatcher() {
    }

    public static void queueSpeechOnServer(EntityMaid maid, String text) {
        queueSpeechOnServer(maid, text, -1L);
    }

    public static void queueSpeechOnServer(EntityMaid maid, String text, long firstWaitingChatBubbleId) {
        if (maid.getServer() == null) {
            queueSpeech(maid, text, firstWaitingChatBubbleId);
            flush(maid);
            return;
        }
        maid.getServer().execute(() -> {
            queueSpeech(maid, text, firstWaitingChatBubbleId);
            flush(maid);
        });
    }

    public static void queueSpeech(EntityMaid maid, String text) {
        queueSpeech(maid, text, -1L);
    }

    public static void queueSpeech(EntityMaid maid, String text, long firstWaitingChatBubbleId) {
        List<String> segments = SPLITTER.split(text);
        if (segments.isEmpty()) {
            return;
        }
        PENDING.put(maid.getUUID(), new PendingSpeech(segments, 0, maid.tickCount, System.currentTimeMillis(), firstWaitingChatBubbleId));
    }

    public static void interrupt(EntityMaid maid) {
        if (maid != null) {
            PENDING.remove(maid.getUUID());
        }
    }

    public static void flush(EntityMaid maid) {
        PendingSpeech speech = PENDING.get(maid.getUUID());
        if (speech == null) {
            return;
        }
        if (System.currentTimeMillis() - speech.createdAtMillis > STALE_MILLIS) {
            PENDING.remove(maid.getUUID());
            return;
        }
        if (maid.tickCount < speech.nextEmitTick) {
            return;
        }
        if (speech.index >= speech.segments.size()) {
            PENDING.remove(maid.getUUID());
            return;
        }
        String segment = speech.segments.get(speech.index);
        long waitingId = speech.index == 0 ? speech.firstWaitingChatBubbleId : -1L;
        maid.getChatBubbleManager().addLLMChatText(segment, waitingId);
        int next = speech.index + 1;
        if (next >= speech.segments.size()) {
            PENDING.remove(maid.getUUID());
            return;
        }
        speech.index = next;
        speech.nextEmitTick = maid.tickCount + delayTicks(segment);
    }

    private static int delayTicks(String segment) {
        int len = Math.max(1, segment.replaceAll("\\s+", "").length());
        return Math.max(18, Math.min(70, 14 + len * 2));
    }

    private static final class PendingSpeech {
        private final List<String> segments;
        private int index;
        private int nextEmitTick;
        private final long createdAtMillis;
        private final long firstWaitingChatBubbleId;

        private PendingSpeech(List<String> segments, int index, int nextEmitTick, long createdAtMillis, long firstWaitingChatBubbleId) {
            this.segments = List.copyOf(segments);
            this.index = index;
            this.nextEmitTick = nextEmitTick;
            this.createdAtMillis = createdAtMillis;
            this.firstWaitingChatBubbleId = firstWaitingChatBubbleId;
        }
    }
}
