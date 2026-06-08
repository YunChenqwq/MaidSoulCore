package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.integration.maibot.MaiBotConfigService;

/**
 * 暴露 MaiBot 的回复风格配置。
 */
public final class MaiBotReplyStyleContext implements IMaidContext {
    @Override
    public String key() {
        return "maidsoul_reply_style";
    }

    @Override
    public String label() {
        return "Reply style";
    }

    @Override
    public String getValue(EntityMaid maid) {
        return MaiBotConfigService.getSnapshot().personalitySettings().replyStyle();
    }
}
