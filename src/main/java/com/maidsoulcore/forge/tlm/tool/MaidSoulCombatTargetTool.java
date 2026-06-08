package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.task.MaidSoulGlobalAttackTask;
import com.maidsoulcore.forge.service.MaidSoulActionExecutorService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 战斗锁定专用工具。
 * <p>
 * 相比通用动作工具，这个工具给模型一个更明确的“先查实体 -> 再锁定单目标攻击”的入口，
 * 便于复用 MaidSoulCore 自己的顺序攻击计划与超时切换机制。
 */
public final class MaidSoulCombatTargetTool implements ITool<MaidSoulCombatTargetTool.Result> {
    private static final String TOOL_ID = "maidsoul_lock_combat_target";
    private static final String ENTITY_ID = "entity_id";
    private static final String TASK_ID = "task_id";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf(ENTITY_ID).forGetter(Result::entityId),
            Codec.STRING.optionalFieldOf(TASK_ID, MaidSoulGlobalAttackTask.UID.toString()).forGetter(Result::taskId)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return """
                Use this when you already know the exact entity id to attack.
                It locks one primary target first, then MaidSoulCore continues with its own sequential combat plan.
                Default task is maidsoulcore:global_attack unless a different attack task is explicitly required.
                """.trim();
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        IntegerParameter entityId = IntegerParameter.create()
                .setDescription("Entity id to attack. Must come from nearby entity context.");
        StringParameter taskId = StringParameter.create()
                .setDescription("Optional attack task id. Default is maidsoulcore:global_attack.");
        root.addProperties(ENTITY_ID, entityId);
        root.addProperties(TASK_ID, taskId, false);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        String actionResult = MaidSoulActionExecutorService.enterCombatDirectly(
                callback.getMaid(),
                result.taskId(),
                result.entityId()
        );
        return callback.addToolResult(actionResult, toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { entity_id=" + result.entityId() + ", task_id=" + result.taskId() + " }";
    }

    public record Result(int entityId, String taskId) {
    }
}
