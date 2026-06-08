package com.maidsoulcore.forge.runtime;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.maidsoulcore.event.EventPriority;

import java.util.List;

/**
 * 统一聊天运行时中的单个队列作业。
 *
 * @param jobId 队列内的作业编号
 * @param type 作业类型
 * @param version 主人聊天版本号；主动事件使用 0
 * @param callback 原始 TLM 回调；仅主人聊天使用
 * @param messages 原始消息快照；仅主人聊天使用
 * @param latestUserMessage 清洗后的最新主人输入；仅主人聊天使用
 * @param eventType 主动事件类型；仅主动事件使用
 * @param eventDetail 主动事件详情；仅主动事件使用
 * @param priority 主动事件优先级；仅主动事件使用
 * @param createdAtMillis 进入队列时间
 */
public record MaidSoulRuntimeJob(
        long jobId,
        MaidSoulRuntimeJobType type,
        int version,
        LLMCallback callback,
        List<LLMMessage> messages,
        String latestUserMessage,
        List<String> collectedOwnerMessages,
        String eventType,
        String eventDetail,
        EventPriority priority,
        long createdAtMillis
) {
    /**
     * 构建一条主人聊天作业。
     */
    public static MaidSoulRuntimeJob ownerChat(long jobId,
                                               int version,
                                               LLMCallback callback,
                                               List<LLMMessage> messages,
                                               String latestUserMessage,
                                               long createdAtMillis) {
        return new MaidSoulRuntimeJob(
                jobId,
                MaidSoulRuntimeJobType.OWNER_CHAT,
                version,
                callback,
                messages,
                latestUserMessage,
                List.of(latestUserMessage),
                "",
                "",
                EventPriority.P1,
                createdAtMillis
        );
    }

    public MaidSoulRuntimeJob withCollectedOwnerMessages(List<String> collectedMessages) {
        return new MaidSoulRuntimeJob(
                jobId,
                type,
                version,
                callback,
                messages,
                latestUserMessage,
                collectedMessages == null ? List.of() : List.copyOf(collectedMessages),
                eventType,
                eventDetail,
                priority,
                createdAtMillis
        );
    }

    /**
     * 构建一条主动事件作业。
     */
    public static MaidSoulRuntimeJob proactiveEvent(long jobId,
                                                    int version,
                                                    String eventType,
                                                    String eventDetail,
                                                    EventPriority priority,
                                                    long createdAtMillis) {
        return new MaidSoulRuntimeJob(
                jobId,
                MaidSoulRuntimeJobType.PROACTIVE_EVENT,
                version,
                null,
                List.of(),
                "",
                List.of(),
                eventType,
                eventDetail,
                priority,
                createdAtMillis
        );
    }

    public static MaidSoulRuntimeJob proactiveEvent(long jobId,
                                                    String eventType,
                                                    String eventDetail,
                                                    EventPriority priority,
                                                    long createdAtMillis) {
        return proactiveEvent(jobId, 0, eventType, eventDetail, priority, createdAtMillis);
    }
}
