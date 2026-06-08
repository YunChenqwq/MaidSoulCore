package com.maidsoulcore.forge.runtime;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;

import java.util.List;

/**
 * 运行时内部排队的一轮聊天请求。
 *
 * @param version 版本号。用于在“新消息覆盖旧消息”时丢弃过期结果
 * @param callback 原始 TLM 回调
 * @param messages 当前轮次的消息快照
 * @param latestUserMessage 当前轮次归一化后的最新用户输入
 * @param createdAtMillis 进入队列的时间
 */
public record MaidSoulQueuedChatTurn(
        int version,
        LLMCallback callback,
        List<LLMMessage> messages,
        String latestUserMessage,
        long createdAtMillis
) {
}
