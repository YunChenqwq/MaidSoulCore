package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.plan.MaidSoulPlanPriority;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;
import com.maidsoulcore.forge.service.MaidSoulPlanService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 多步骤本地计划提交工具。
 * <p>
 * 命令特别复杂时，不再强迫模型一轮只做一个动作，
 * 而是允许它直接把多步骤动作压成一个本地计划，再由调度器分步推进。
 */
public final class MaidSoulPlanTool implements ITool<MaidSoulPlanTool.Result> {
    private static final String TOOL_ID = "maidsoul_submit_plan";
    private static final String OBJECTIVE = "objective";
    private static final String PRIORITY = "priority";
    private static final String STEPS_JSON = "steps_json";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf(OBJECTIVE).forGetter(Result::objective),
            Codec.STRING.optionalFieldOf(PRIORITY, "OWNER_COMMAND").forGetter(Result::priority),
            Codec.STRING.fieldOf(STEPS_JSON).forGetter(Result::stepsJson)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return """
                Use this when the master's request contains multiple ordered steps.
                Submit a local plan once, then MaidSoulCore will execute each step in order and emit trace for the whole chain.
                steps_json must be a JSON array of step objects:
                [{"description":"clear nearby undead","action_type":"ENTER_COMBAT_GROUP","action_value":"tag=forge:undead","target_entity_id":-1,"timeout_ticks":600}]
                """.trim();
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        root.addProperties(OBJECTIVE, StringParameter.create()
                .setDescription("Human-readable objective, such as 'first clear rabbits, then attack the pig'."));
        root.addProperties(PRIORITY, StringParameter.create()
                .setDescription("Plan priority: COMPANION / OWNER_COMMAND / CRITICAL."), false);
        root.addProperties(STEPS_JSON, StringParameter.create()
                .setDescription("JSON array of ordered steps. Each step needs description, action_type, action_value, target_entity_id, timeout_ticks. ENTER_COMBAT_GROUP action_value supports type/tag filters."));
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        List<MaidSoulPlanStep> steps = parseSteps(result.stepsJson());
        if (steps.isEmpty()) {
            return callback.addToolResult("plan_submit_failed: no valid steps", toolCallId);
        }
        MaidSoulPlan plan = new MaidSoulPlan(
                "tool_loop",
                result.objective(),
                parsePriority(result.priority()),
                steps
        );
        String submitResult = MaidSoulPlanService.submitPlan(maid, plan);
        if (callback.getWaitingChatBubbleId() >= 0L) {
            maid.getChatBubbleManager().addLLMChatText("收到，我会按顺序处理这些事情的。", callback.getWaitingChatBubbleId());
        }
        return callback.addToolResult(submitResult + " | " + plan.summary(), toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { objective=" + result.objective()
                + ", priority=" + result.priority()
                + ", steps_json=" + result.stepsJson() + " }";
    }

    private static List<MaidSoulPlanStep> parseSteps(String stepsJson) {
        ArrayList<MaidSoulPlanStep> steps = new ArrayList<>();
        if (stepsJson == null || stepsJson.isBlank()) {
            return steps;
        }
        JsonElement root = JsonParser.parseString(stepsJson);
        if (!root.isJsonArray()) {
            return steps;
        }
        JsonArray array = root.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String description = stringValue(object, "description", "");
            String actionType = stringValue(object, "action_type", "NONE");
            String actionValue = stringValue(object, "action_value", "");
            int targetEntityId = intValue(object, "target_entity_id", -1);
            int timeoutTicks = intValue(object, "timeout_ticks", 600);
            if (actionType.isBlank() || "NONE".equalsIgnoreCase(actionType)) {
                continue;
            }
            steps.add(new MaidSoulPlanStep(description, actionType, actionValue, targetEntityId, timeoutTicks));
        }
        return steps;
    }

    private static MaidSoulPlanPriority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return MaidSoulPlanPriority.OWNER_COMMAND;
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> MaidSoulPlanPriority.CRITICAL;
            case "COMPANION" -> MaidSoulPlanPriority.COMPANION;
            default -> MaidSoulPlanPriority.OWNER_COMMAND;
        };
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    public record Result(String objective, String priority, String stepsJson) {
    }
}
