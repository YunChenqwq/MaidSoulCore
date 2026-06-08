package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.integration.maibot.MaiBotConfigService;

/**
 * 直接暴露 MaiBot 配置里的人设描述。
 */
public final class MaiBotPersonalityContext implements IMaidContext {
    @Override
    public String key() {
        return "maidsoul_personality";
    }

    @Override
    public String label() {
        return "Companion personality";
    }

    @Override
    public String getValue(EntityMaid maid) {
        return MaiBotConfigService.getSnapshot().personalitySettings().personality();
    }
}
