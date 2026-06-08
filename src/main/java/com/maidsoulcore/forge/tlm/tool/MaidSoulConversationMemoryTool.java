package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.conversation.ConversationMemoryService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Tool for reading the clean local conversation memory.
 */
public final class MaidSoulConversationMemoryTool implements ITool<MaidSoulConversationMemoryTool.Result> {
    private static final String TOOL_ID = "maidsoul_query_conversation_memory";
    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("limit", 8).forGetter(Result::limit)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return "Query recent clean conversation lines and lightweight owner preference notes.";
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(
                "limit",
                IntegerParameter.create()
                        .setDescription("Maximum recent conversation lines to return.")
                        .setMinimum(1)
                        .setMaximum(12),
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
        int limit = Math.max(1, Math.min(12, result.limit()));
        List<String> lines = ConversationMemoryService.recentLines(maid, limit);
        String notes = ConversationMemoryService.notesForPrompt(maid);
        String content = "owner_notes:\n" + notes
                + "\n\nrecent_lines:\n"
                + (lines.isEmpty() ? "none" : String.join("\n", lines));
        return callback.addToolResult(content, toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { limit=" + result.limit() + " }";
    }

    public record Result(int limit) {
    }
}
