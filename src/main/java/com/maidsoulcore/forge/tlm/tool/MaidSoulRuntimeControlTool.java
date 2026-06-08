package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulChatLoopRuntimeService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/**
 * 运行时控制工具。
 * <p>
 * 供 tool loop 在特殊场景显式表达：
 * - 暂时等待；
 * - 本轮不说；
 * - 当前链路完成；
 * - 继续推进。
 */
public final class MaidSoulRuntimeControlTool implements ITool<MaidSoulRuntimeControlTool.Result> {
    private static final String TOOL_ID = "maidsoul_runtime_control";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("action").forGetter(Result::action),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(Result::reason),
            Codec.INT.optionalFieldOf("wait_millis", 0).forGetter(Result::waitMillis)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return """
                Use this only for runtime pacing control.
                action can be CONTINUE / WAIT / NO_REPLY / FINISH.
                WAIT needs wait_millis.
                """.trim();
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        StringParameter action = StringParameter.create()
                .setDescription("Runtime control action.")
                .addEnumValues("CONTINUE")
                .addEnumValues("WAIT")
                .addEnumValues("NO_REPLY")
                .addEnumValues("FINISH");
        root.addProperties("action", action);
        root.addProperties("reason", StringParameter.create().setDescription("Short trace reason."), false);
        root.addProperties("wait_millis", IntegerParameter.create().setDescription("Used only when action=WAIT.").setMinimum(0), false);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        String action = result.action() == null ? "" : result.action().toUpperCase(Locale.ROOT);
        switch (action) {
            case "WAIT" -> MaidSoulChatLoopRuntimeService.requestWait(maid, result.waitMillis(), result.reason());
            case "NO_REPLY" -> MaidSoulChatLoopRuntimeService.requestNoReply(maid, result.reason());
            case "FINISH" -> MaidSoulChatLoopRuntimeService.requestFinish(maid, result.reason());
            default -> MaidSoulChatLoopRuntimeService.requestContinue(maid, result.reason());
        }
        return callback.addToolResult("runtime_control_applied:" + action + "|" + result.reason(), toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { action=" + result.action()
                + ", reason=" + result.reason()
                + ", wait_millis=" + result.waitMillis()
                + " }";
    }

    public record Result(String action, String reason, int waitMillis) {
    }
}
