package com.maidsoulcore.forge.tlm.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.function.Function;

/**
 * 面向女仆实体的通用上下文实现。
 * <p>
 * 很多上下文只是“从 EntityMaid 读取一个值，再转成字符串”，
 * 没必要每个字段都写一套重复类，所以这里提供一个可复用适配器。
 */
public final class MaidSoulEntityContext implements IMaidContext {
    private final String key;
    private final String label;
    private final Function<EntityMaid, String> extractor;

    public MaidSoulEntityContext(String key, String label, Function<EntityMaid, String> extractor) {
        this.key = key;
        this.label = label;
        this.extractor = extractor;
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
        return extractor.apply(maid);
    }
}
