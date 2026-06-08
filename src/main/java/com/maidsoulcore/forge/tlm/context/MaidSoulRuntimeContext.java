package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.forge.state.MaidSoulStateSnapshot;

import java.util.function.Function;

/**
 * 把运行时状态快照映射成 TLM 可读上下文。
 * <p>
 * 当前通过 selector 在不同字段上复用同一个实现，
 * 避免为每个显示字段都新写一套类。
 */
public final class MaidSoulRuntimeContext implements IMaidContext {
    private final String key;
    private final String label;
    private final Function<MaidSoulStateSnapshot, String> selector;

    public MaidSoulRuntimeContext(String key, String label, Function<MaidSoulStateSnapshot, String> selector) {
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
        return selector.apply(MaidSoulStateRegistry.snapshot(maid));
    }
}
