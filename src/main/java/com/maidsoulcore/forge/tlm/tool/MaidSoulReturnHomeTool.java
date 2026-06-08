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
 * 让女仆回到当前 home 点附近。
 * <p>
 * 这不是瞬移，而是切回 home mode 并给导航下发一个回家的移动目标。
 */
public final class MaidSoulReturnHomeTool implements ITool<MaidSoulReturnHomeTool.Result> {
    private static final String TOOL_ID = "maidsoul_return_home";
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
        return "Use this when the user wants the maid to return to her configured home area.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(ENABLE_HOME_MODE, BoolParameter.create()
                .setDescription("Usually true. Enable home mode before moving back."));
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        BlockPos home = maid.getSchedulePos().getNearestPos(maid);
        if (home == null) {
            return callback.addToolResult("Home is not configured yet.", toolCallId);
        }
        if (result.enableHomeMode()) {
            maid.setHomeModeEnable(true);
        }
        maid.setInSittingPose(false);
        maid.getNavigation().stop();
        maid.getNavigation().moveTo(home.getX() + 0.5d, home.getY(), home.getZ() + 0.5d, 1.0d);
        return callback.addToolResult("Returning to home area at " + home, toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { enable_home_mode=" + result.enableHomeMode() + " }";
    }

    public record Result(boolean enableHomeMode) {
    }
}
