package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.trace.TraceEvent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * 暴露最近 trace 的调试工具。
 * <p>
 * 当模型不知道为什么当前状态变成这样，或者需要解释最近发生了什么，
 * 就可以主动调用这个工具获取最近几条事件轨迹。
 */
public final class MaidSoulTraceTool implements ITool<MaidSoulTraceTool.Result> {
    private static final String TOOL_ID = "maidsoul_trace_tail";
    private static final String COUNT_ID = "count";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf(COUNT_ID).forGetter(Result::count)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return "Use this when you need the latest MaidSoulCore observed events for debugging current behavior.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        IntegerParameter count = IntegerParameter.create()
                .setDescription("Number of recent trace lines to fetch, recommended 3-10.")
                .setMinimum(1)
                .setMaximum(20);
        root.addProperties(COUNT_ID, count);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    /**
     * 执行工具调用并返回最近若干条 trace 文本。
     */
    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        int count = Math.max(1, Math.min(20, result.count()));
        List<TraceEvent> events = MaidSoulStateRegistry.tail(callback.getMaid(), count);
        if (events.isEmpty()) {
            return callback.addToolResult("No MaidSoulCore trace available yet.", toolCallId);
        }
        String body = events.stream()
                .map(event -> "%s | %s | %s".formatted(event.timestamp(), event.type(), event.reason()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No MaidSoulCore trace available yet.");
        return callback.addToolResult(body, toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { count=" + result.count() + " }";
    }

    public record Result(int count) {
    }
}
