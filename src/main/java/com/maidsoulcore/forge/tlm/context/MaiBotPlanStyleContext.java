package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.integration.maibot.MaiBotConfigService;

/**
 * 暴露 MaiBot 的计划规则文本。
 * <p>
 * 这段文本会直接影响大模型对动作选择边界的理解。
 */
public final class MaiBotPlanStyleContext implements IMaidContext {
    @Override
    public String key() {
        return "maidsoul_plan_style";
    }

    @Override
    public String label() {
        return "Planning rules";
    }

    @Override
    public String getValue(EntityMaid maid) {
        return MaiBotConfigService.getSnapshot().personalitySettings().planStyle();
    }
}
