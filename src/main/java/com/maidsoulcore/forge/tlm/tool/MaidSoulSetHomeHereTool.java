package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.BoolParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * 把女仆当前位置设为 home 锚点。
 * <p>
 * 适合用户明确要求“把这里当家”或“以后在这里待命”时调用。
 */
public final class MaidSoulSetHomeHereTool implements ITool<MaidSoulSetHomeHereTool.Result> {
    private static final String TOOL_ID = "maidsoul_set_home_here";
    private static final String ENABLE_HOME_MODE = "enable_home_mode";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf(ENABLE_HOME_MODE).forGetter(Result::enableHomeMode)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return "Use this when the user explicitly wants the maid to treat the current position as home.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(ENABLE_HOME_MODE, BoolParameter.create()
                .setDescription("Usually true. Whether to immediately enable home mode after setting home."));
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        BlockPos current = maid.blockPosition();
        maid.getSchedulePos().setHomeModeEnable(maid, current);
        maid.setHomeModeEnable(result.enableHomeMode());
        return callback.addToolResult("Home anchor updated to " + current, toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { enable_home_mode=" + result.enableHomeMode() + " }";
    }

    public record Result(boolean enableHomeMode) {
    }
}
