package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulActionCatalogService;
import com.maidsoulcore.forge.service.MaidSoulPlanService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/**
 * Tool for searching currently useful gameplay and conversation capabilities.
 */
public final class MaidSoulCapabilitySearchTool implements ITool<MaidSoulCapabilitySearchTool.Result> {
    private static final String TOOL_ID = "maidsoul_search_capability";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("query", "").forGetter(Result::query)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return "Search available capabilities before choosing an action, plan, or reply strategy.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(
                "query",
                StringParameter.create().setDescription("Capability keyword, such as chat, memory, follow, sit, schedule, task, combat, trace, plan."),
                false
        );
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        String query = result.query() == null ? "" : result.query().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        appendIfMatches(builder, query, "chat memory", "- maidsoul_query_conversation_memory: read recent clean chat lines and owner notes.");
        appendIfMatches(builder, query, "pacing wait no_reply finish continue", "- maidsoul_runtime_control: choose continue, wait, no reply, or finish for conversation pacing.");
        appendIfMatches(builder, query, "trace debug", "- maidsoul_trace_tail: inspect recent runtime trace.");
        appendIfMatches(builder, query, "plan multi step sequence", "- maidsoul_submit_plan: submit ordered multi-step tasks when a command has several actions.");
        appendIfMatches(builder, query, "follow sit schedule task combat action", "- maidsoul_execute_action: execute direct follow, sit, schedule, task, and combat actions.");
        appendIfMatches(builder, query, "target combat nearby", "- maidsoul_select_combat_target: select a nearby target for combat actions.");
        builder.append("\ncurrent_plan:\n").append(MaidSoulPlanService.describePlanState(maid));
        builder.append("\n\nstate:\n").append(MaidSoulStateRegistry.snapshot(maid).lastEventType());
        builder.append("\n\nactions:\n").append(MaidSoulActionCatalogService.actionTypeSummary());
        return callback.addToolResult(builder.toString(), toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { query=" + result.query() + " }";
    }

    private static void appendIfMatches(StringBuilder builder, String query, String keys, String line) {
        if (query.isBlank() || containsAny(keys, query.split("\\s+"))) {
            builder.append(line).append('\n');
            return;
        }
        for (String key : keys.split("\\s+")) {
            if (query.contains(key)) {
                builder.append(line).append('\n');
                return;
            }
        }
    }

    private static boolean containsAny(String text, String[] values) {
        for (String value : values) {
            if (!value.isBlank() && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public record Result(String query) {
    }
}
