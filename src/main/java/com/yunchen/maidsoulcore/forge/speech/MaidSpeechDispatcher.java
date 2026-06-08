package com.yunchen.maidsoulcore.forge.speech;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.core.text.SentenceSplitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidSpeechDispatcher {
    private static final long STALE_MILLIS = 30_000L;
    private static final SentenceSplitter SPLITTER = new SentenceSplitter();
    private static final ConcurrentMap<UUID, PendingSpeech> PENDING = new ConcurrentHashMap<>();

    private MaidSpeechDispatcher() {
    }

    public static void queueOnServer(EntityMaid maid, String text) {
        if (maid == null || !maid.isAlive() || maid.getServer() == null) {
            return;
        }
        maid.getServer().execute(() -> queue(maid, text));
    }

    private static void queue(EntityMaid maid, String text) {
        if (maid == null || !maid.isAlive()) {
            return;
        }
        List<String> segments = SPLITTER.split(text);
        if (!segments.isEmpty()) {
            PENDING.put(maid.getUUID(), new PendingSpeech(segments, 0, maid.tickCount, System.currentTimeMillis()));
        }
    }

    public static void flush(EntityMaid maid) {
        PendingSpeech speech = PENDING.get(maid.getUUID());
        if (speech == null) {
            return;
        }
        if (!maid.isAlive() || System.currentTimeMillis() - speech.createdAtMillis > STALE_MILLIS) {
            PENDING.remove(maid.getUUID());
            return;
        }
        if (maid.tickCount < speech.nextEmitTick) {
            return;
        }
        String segment = speech.segments.get(speech.index);
        maid.getChatBubbleManager().addLLMChatText(segment, -1L);
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
        return Math.max(18, Math.min(70, 12 + len * 2));
    }

    private static final class PendingSpeech {
        private final List<String> segments;
        private int index;
        private int nextEmitTick;
        private final long createdAtMillis;

        private PendingSpeech(List<String> segments, int index, int nextEmitTick, long createdAtMillis) {
            this.segments = List.copyOf(segments);
            this.index = index;
            this.nextEmitTick = nextEmitTick;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
