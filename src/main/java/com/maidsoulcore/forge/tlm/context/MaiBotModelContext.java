package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.integration.maibot.MaiBotConfigService;
import com.maidsoulcore.integration.maibot.MaiBotModelSettings;

import java.util.List;
import java.util.function.Function;

/**
 * 把 MaiBot 的模型分组配置映射成 TLM 可读上下文。
 * <p>
 * 这是一个通用实现，通过传入 selector 来决定读取 planner、
 * reply、tool_use 还是 vlm 的模型列表。
 */
public final class MaiBotModelContext implements IMaidContext {
    private final String key;
    private final String label;
    private final Function<MaiBotModelSettings, List<String>> selector;

    public MaiBotModelContext(String key, String label, Function<MaiBotModelSettings, List<String>> selector) {
        this.key = key;
        this.label = label;
        this.selector = selector;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String getValue(EntityMaid maid) {
        return String.join(", ", selector.apply(MaiBotConfigService.getSnapshot().modelSettings()));
    }
}
