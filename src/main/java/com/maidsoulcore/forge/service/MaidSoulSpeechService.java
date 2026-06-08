package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MaidSoulCore 统一发言输出服务。
 * <p>
 * 不管是主动事件回复，还是玩家主动聊天后的回复，
 * 最终都统一走这里的“等待态 -> 分句 -> 逐句落地”链路。
 * 这样可以保证整个模组的说话节奏一致，不会一部分是整段秒回，一部分是分句输出。
 */
public final class MaidSoulSpeechService {
    /**
     * 保险阈值：逐句气泡如果因为实体 tick、气泡管理器或异常状态没有被推进，
     * 不能永远挡住主动续话和后续事件。
     */
    private static final long SPEECH_STALE_MILLIS = 30_000L;
    /**
     * 每只女仆当前待输出的发言队列。
     */
    private static final ConcurrentMap<UUID, PendingSpeech> PENDING_SPEECH = new ConcurrentHashMap<>();

    private MaidSoulSpeechService() {
    }

    /**
     * 把一整段回复拆成句子并进入逐句输出队列。
     *
     * @param maid 女仆实体
     * @param reply 模型生成的整段回复
     * @param waitingBubbleId 思考气泡 id；首句落地时会替换掉它
     */
    public static void queueSpeech(EntityMaid maid, String reply, long waitingBubbleId) {
        List<String> sentences = MaidSoulSentenceSplitter.split(reply);
        if (sentences.isEmpty()) {
            maid.getChatBubbleManager().removeChatBubble(waitingBubbleId);
            return;
        }
        PENDING_SPEECH.put(maid.getUUID(), new PendingSpeech(sentences, 0, maid.tickCount, waitingBubbleId, System.currentTimeMillis()));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.speech.segmented", "sentences=" + sentences.size());
    }

    public static void interruptSpeech(EntityMaid maid, String reason) {
        if (maid == null) {
            return;
        }
        PendingSpeech removed = PENDING_SPEECH.remove(maid.getUUID());
        if (removed == null) {
            return;
        }
        if (removed.waitingBubbleId >= 0L) {
            maid.getChatBubbleManager().removeChatBubble(removed.waitingBubbleId);
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.speech.interrupt", reason == null ? "" : reason);
    }

    /**
     * 每个 tick 推进一次待输出队列。
     */
    public static void flushPendingSpeech(EntityMaid maid) {
        PendingSpeech speech = PENDING_SPEECH.get(maid.getUUID());
        if (speech == null) {
            return;
        }
        if (maid.tickCount < speech.nextEmitTick) {
            return;
        }
        if (speech.index >= speech.sentences.size()) {
            PENDING_SPEECH.remove(maid.getUUID());
            return;
        }

        String sentence = speech.sentences.get(speech.index);
        long waitingBubbleId = speech.index == 0 ? speech.waitingBubbleId : -1L;
        maid.getChatBubbleManager().addLLMChatText(sentence, waitingBubbleId);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.speech.sentence", sentence);

        int nextIndex = speech.index + 1;
        if (nextIndex >= speech.sentences.size()) {
            PENDING_SPEECH.remove(maid.getUUID());
            return;
        }
        speech.index = nextIndex;
        speech.nextEmitTick = maid.tickCount + calculateSentenceDelayTicks(sentence);
    }

    /**
     * 判断当前女仆是否还有正在输出中的句子。
     */
    public static boolean hasPendingSpeech(EntityMaid maid) {
        PendingSpeech speech = PENDING_SPEECH.get(maid.getUUID());
        if (speech == null) {
            return false;
        }
        if (System.currentTimeMillis() - speech.createdAtMillis > SPEECH_STALE_MILLIS) {
            PENDING_SPEECH.remove(maid.getUUID());
            if (speech.waitingBubbleId >= 0L) {
                maid.getChatBubbleManager().removeChatBubble(speech.waitingBubbleId);
            }
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.speech.stale.clear", "age_ms>" + SPEECH_STALE_MILLIS);
            return false;
        }
        return true;
    }

    /**
     * 基于句子长度估算下一句的停顿时间。
     */
    private static int calculateSentenceDelayTicks(String sentence) {
        int effectiveLength = Math.max(1, sentence.replaceAll("\\s+", "").length());
        int min = MaidSoulCommonConfig.CONVERSATION_SPEECH_MIN_DELAY_TICKS.get();
        int max = Math.max(min + 1, MaidSoulCommonConfig.CONVERSATION_SPEECH_MAX_DELAY_TICKS.get());
        int charsPerTick = Math.max(1, MaidSoulCommonConfig.CONVERSATION_SPEECH_CHARS_PER_DELAY_TICK.get());
        return Math.min(max, min + effectiveLength / charsPerTick);
    }

    /**
     * 单次待输出发言。
     */
    private static final class PendingSpeech {
        private final List<String> sentences;
        private int index;
        private int nextEmitTick;
        private final long waitingBubbleId;
        private final long createdAtMillis;

        private PendingSpeech(List<String> sentences, int index, int nextEmitTick, long waitingBubbleId, long createdAtMillis) {
            this.sentences = List.copyOf(sentences);
            this.index = index;
            this.nextEmitTick = nextEmitTick;
            this.waitingBubbleId = waitingBubbleId;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
