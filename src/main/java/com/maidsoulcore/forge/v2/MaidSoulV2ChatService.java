package com.maidsoulcore.forge.v2;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.List;

/**
 * 兼容旧调用点的入口。真实主聊天已经交给 Heartflow 服务。
 */
public final class MaidSoulV2ChatService {
    private MaidSoulV2ChatService() {
    }

    public static String chat(EntityMaid maid, List<LLMMessage> messages) {
        return MaidSoulHeartflowChatService.chat(maid, messages).reply();
    }
}
