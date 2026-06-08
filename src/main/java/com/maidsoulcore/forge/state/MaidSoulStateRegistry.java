package com.maidsoulcore.forge.state;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.trace.RingBufferTraceSink;
import com.maidsoulcore.trace.TraceEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局女仆状态注册表。
 * <p>
 * 每只女仆都有一份独立的运行时状态与 trace 缓冲，
 * 供 Forge 事件桥、上下文拼装器、调试工具与聊天回显共同读取。
 */
public final class MaidSoulStateRegistry {
    private static final Logger LOGGER = LogManager.getLogger("MaidSoulCore");
    private static final ConcurrentMap<UUID, MaidSoulAgentState> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, RingBufferTraceSink> TRACE_SINKS = new ConcurrentHashMap<>();
    private static final AtomicLong TRACE_SEQUENCE = new AtomicLong();

    private MaidSoulStateRegistry() {
    }

    /**
     * 在 tick 阶段观察女仆状态变化。
     */
    public static void observeTick(EntityMaid maid) {
        stateFor(maid).observeTick(maid);
    }

    /**
     * 记录一条显式事件。
     */
    public static void record(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        stateFor(maid).recordEvent(maid, eventType, detail, priority);
    }

    /**
     * 获取当前女仆的轻量状态快照。
     */
    public static MaidSoulStateSnapshot snapshot(EntityMaid maid) {
        return stateFor(maid).snapshot();
    }

    /**
     * 更新主人视角原始摘要与解释后摘要。
     */
    public static void updateOwnerView(EntityMaid maid, String rawSummary, String interpretedSummary) {
        stateFor(maid).updateOwnerView(maid, rawSummary, interpretedSummary);
    }

    /**
     * 获取最近若干条 trace。
     */
    public static List<TraceEvent> tail(EntityMaid maid, int limit) {
        RingBufferTraceSink sink = TRACE_SINKS.get(maid.getUUID());
        if (sink == null) {
            return List.of();
        }
        List<TraceEvent> all = sink.snapshot();
        if (all.size() <= limit) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - limit, all.size()));
    }

    /**
     * 把关键 trace 直接回显到主人的聊天栏。
     * <p>
     * 这样调试时不必再切日志文件，能在游戏里直接看到事件链路。
     */
    public static void echoTraceToOwnerChat(EntityMaid maid, String traceType, String detail) {
        if (!MaidSoulCommonConfig.TRACE_ENABLED.get()) {
            return;
        }
        String normalizedDetail = detail == null || detail.isBlank() ? "-" : abbreviate(detail);
        logTraceToFile(maid, traceType, normalizedDetail);
        if (!MaidSoulCommonConfig.DEBUG_CHAT_ECHO_ENABLED.get()
                || !MaidSoulCommonConfig.TRACE_CHAT_ECHO_ENABLED.get()) {
            return;
        }
        if (!MaidSoulCommonConfig.TRACE_CHAT_VERBOSE_ECHO_ENABLED.get() && !isImportantChatTrace(traceType)) {
            return;
        }
        if (!(maid.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        player.sendSystemMessage(Component.literal("[MSC/TRACE] " + traceType + " | " + normalizedDetail)
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /**
     * 把完整调试块分段回显到主人聊天栏。
     *
     * <p>这用于现场排查 prompt、记忆、情绪、门控输入和模型原始输出。
     * 它不是模型隐藏思维，只是 MaidSoulCore 实际送入/取回的可观察数据。</p>
     */
    public static void echoFullDebugToOwnerChat(EntityMaid maid, String title, String content) {
        if (!MaidSoulCommonConfig.DEBUG_FULL_PROMPT_CHAT_ECHO_ENABLED.get()) {
            return;
        }
        if (!(maid.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        String normalizedTitle = title == null || title.isBlank() ? "debug" : title.trim();
        String normalizedContent = content == null || content.isBlank() ? "(empty)" : content;
        List<String> chunks = chunk(normalizedContent, 220);
        int total = chunks.size();
        for (int index = 0; index < total; index++) {
            player.sendSystemMessage(Component.literal(
                    "[MSC/FULL " + normalizedTitle + " " + (index + 1) + "/" + total + "] " + chunks.get(index)
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 写入 Forge 日志文件。
     * <p>
     * 聊天栏回显可以关闭，但 timeout / runtime / error 这类关键线索应该能在 latest.log 里找到。
     * 详细 trace 只有开启 verbose 时才落盘，避免平时把日志刷得太厚。
     */
    private static void logTraceToFile(EntityMaid maid, String traceType, String detail) {
        if (!MaidSoulCommonConfig.TRACE_LOG_TO_FILE_ENABLED.get()) {
            return;
        }
        if (!MaidSoulCommonConfig.TRACE_CHAT_VERBOSE_ECHO_ENABLED.get() && !isImportantFileTrace(traceType)) {
            return;
        }
        String maidName = maid == null ? "unknown" : maid.getName().getString();
        UUID maidId = maid == null ? new UUID(0L, 0L) : maid.getUUID();
        if (traceType != null && (traceType.endsWith(".error") || traceType.endsWith(".timeout"))) {
            LOGGER.warn("[MSC/TRACE] maid={}({}) type={} detail={}", maidName, maidId, traceType, detail);
        } else {
            LOGGER.info("[MSC/TRACE] maid={}({}) type={} detail={}", maidName, maidId, traceType, detail);
        }
    }

    /**
     * 懒创建单只女仆的状态对象。
     */
    private static MaidSoulAgentState stateFor(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        return STATES.computeIfAbsent(maidId, id -> new MaidSoulAgentState(id, sinkFor(id), TRACE_SEQUENCE));
    }

    /**
     * 懒创建对应的环形 trace 缓冲区。
     */
    private static RingBufferTraceSink sinkFor(UUID maidId) {
        return TRACE_SINKS.computeIfAbsent(maidId, id -> new RingBufferTraceSink(MaidSoulCommonConfig.TRACE_BUFFER_SIZE.get()));
    }

    /**
     * 控制聊天栏 trace 的最大长度，避免刷屏。
     */
    private static String abbreviate(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    private static List<String> chunk(String text, int maxChars) {
        ArrayList<String> result = new ArrayList<>();
        String normalized = text.replace('\r', ' ').replace('\t', ' ');
        int safeMax = Math.max(80, maxChars);
        int cursor = 0;
        while (cursor < normalized.length()) {
            int end = Math.min(normalized.length(), cursor + safeMax);
            result.add(normalized.substring(cursor, end));
            cursor = end;
        }
        if (result.isEmpty()) {
            result.add("(empty)");
        }
        return result;
    }

    /**
     * 聊天栏只显示低频关键 trace，高频细节仍保留在内存 trace 里，避免客户端渲染被刷爆。
     */
    private static boolean isImportantChatTrace(String traceType) {
        if (traceType == null || traceType.isBlank()) {
            return false;
        }
        return traceType.endsWith(".error")
                || traceType.endsWith(".timeout")
                || traceType.contains("runtime.client.entry")
                || traceType.contains("conversation.flow")
                || traceType.contains("timing_gate")
                || traceType.contains("heartflow.timing")
                || traceType.contains("reply_effect");
    }

    private static boolean isImportantFileTrace(String traceType) {
        if (isImportantChatTrace(traceType)) {
            return true;
        }
        return traceType != null
                && (traceType.contains("runtime.turn")
                || traceType.contains("runtime.job.finish")
                || traceType.contains("runtime.queue")
                || traceType.contains("runtime.followup")
                || traceType.contains("heartflow.decision")
                || traceType.contains("speech.segmented"));
    }
}
