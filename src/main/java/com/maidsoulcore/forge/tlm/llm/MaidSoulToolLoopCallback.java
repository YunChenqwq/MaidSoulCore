package com.maidsoulcore.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.service.MaidSoulReplyPostProcessor;
import com.maidsoulcore.forge.service.MaidSoulSpeechService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.List;

/**
 * 命令型 tool loop 的专用回调。
 * <p>
 * 仍然继承 TLM 原生 LLMCallback，以保留 tool loop 行为；
 * 但最终文本回复改为走 MaidSoulCore 的净化、trace 和分句气泡输出。
 */
public final class MaidSoulToolLoopCallback extends LLMCallback {
    public MaidSoulToolLoopCallback(MaidAIChatManager chatManager, List<LLMMessage> messages) {
        super(chatManager, messages);
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        String rawReply = responseChat.getChatText();
        String finalReply = MaidSoulReplyPostProcessor.process(rawReply,
                new com.maidsoulcore.forge.service.MaidSoulChatRuntimeService.PlannerDecision(
                        true, "tool_loop", "command", "finish command response", "tool loop final reply",
                        false, "", "NONE", "", -1
                ));
        if (finalReply.isBlank()) {
            finalReply = "我来啦，主人。";
        }

        EntityMaid maid = getMaid();
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.tool_loop.reply.raw", rawReply);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.tool_loop.reply.final", finalReply);
        getChatManager().addAssistantHistory(finalReply);
        MaidSoulSpeechService.queueSpeech(maid, finalReply, getWaitingChatBubbleId());
        MaidSoulStateRegistry.record(maid, "maidsoul.tool_loop.reply", finalReply, EventPriority.P1);
    }
}
