package com.maidsoulcore.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.maidsoulcore.forge.service.MaidSoulChatLoopRuntimeService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

/**
 * MaidSoulCore 自定义聊天客户端。
 * <p>
 * 现在这个 client 不再自己处理路由分叉，
 * 而是把主人聊天入口统一交给当前 Forge V2 主链路。
 */
public final class MaidSoulRuntimeClient implements LLMClient {
    private final MaidSoulRuntimeSite site;

    public MaidSoulRuntimeClient(MaidSoulRuntimeSite site) {
        this.site = site;
    }

    @Override
    public void chat(LLMCallback callback) {
        if (callback != null && callback.getMaid() != null) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(callback.getMaid(), "maidsoul.runtime.client.entry", site == null ? "-" : site.url());
        }
        MaidSoulChatLoopRuntimeService.handleChat(this.site, callback);
    }
}
