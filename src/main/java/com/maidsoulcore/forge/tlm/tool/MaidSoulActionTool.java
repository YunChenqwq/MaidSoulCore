package com.maidsoulcore.forge.tlm.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulActionCatalogService;
import com.maidsoulcore.forge.service.MaidSoulActionExecutorService;
import com.maidsoulcore.forge.service.MaidSoulChatRuntimeService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * 直接把 MaidSoulCore 自己的动作执行链暴露成 TLM 工具。
 * <p>
 * 命令型对话走 tool loop 时，可以稳定复用同一套执行器。
 */
public final class MaidSoulActionTool implements ITool<MaidSoulActionTool.Result> {
    private static final String TOOL_ID = "maidsoul_execute_action";
    private static final String ACTION_TYPE = "action_type";
    private static final String ACTION_VALUE = "action_value";
    private static final String TARGET_ENTITY_ID = "target_entity_id";

    private static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf(ACTION_TYPE).forGetter(Result::actionType),
            Codec.STRING.optionalFieldOf(ACTION_VALUE, "").forGetter(Result::actionValue),
            Codec.INT.optionalFieldOf(TARGET_ENTITY_ID, -1).forGetter(Result::targetEntityId)
    ).apply(instance, Result::new));

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return """
                Use this to execute MaidSoulCore native actions directly.
                ENTER_COMBAT locks one exact entity first.
                ENTER_COMBAT_GROUP clears nearby entities by filter in sequence.
                action_value supports `type=` and `tag=` filters, such as `type=minecraft:zombie`, `tag=forge:undead`, or `#forge:skeletons`.
                """.trim();
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        StringParameter actionType = StringParameter.create()
                .setDescription(MaidSoulActionCatalogService.actionTypeSummary());
        actionType.addEnumValues("NONE");
        actionType.addEnumValues("FOLLOW_ON");
        actionType.addEnumValues("FOLLOW_OFF");
        actionType.addEnumValues("SIT_ON");
        actionType.addEnumValues("SIT_OFF");
        actionType.addEnumValues("SET_SCHEDULE");
        actionType.addEnumValues("SET_TASK");
        actionType.addEnumValues("ENTER_COMBAT");
        actionType.addEnumValues("ENTER_COMBAT_GROUP");

        StringParameter actionValue = StringParameter.create()
                .setDescription("Optional action value. Used for schedule name, task id, or combat group filter. ENTER_COMBAT_GROUP supports `type=` / `tag=` like `type=minecraft:zombie` or `tag=forge:undead`.");
        IntegerParameter targetEntityId = IntegerParameter.create()
                .setDescription("Optional target entity id. Required for precise single-target combat lock.")
                .setMinimum(-1);

        root.addProperties(ACTION_TYPE, actionType);
        root.addProperties(ACTION_VALUE, actionValue, false);
        root.addProperties(TARGET_ENTITY_ID, targetEntityId, false);
        return root;
    }

    @Override
    public Codec<Result> codec() {
        return CODEC;
    }

    @Override
    public LLMCallback onCall(String toolCallId, Result result, LLMCallback callback) {
        emitActionStartFeedback(callback.getMaid(), callback.getWaitingChatBubbleId(), result);
        MaidSoulChatRuntimeService.PlannerDecision plannerDecision = new MaidSoulChatRuntimeService.PlannerDecision(
                true,
                "tool_call",
                "tool_call",
                "execute native action",
                "direct tool execution",
                false,
                "",
                result.actionType(),
                result.actionValue(),
                result.targetEntityId()
        );
        String actionResult = MaidSoulActionExecutorService.execute(callback.getMaid(), plannerDecision);
        return callback.addToolResult(actionResult, toolCallId);
    }

    @Override
    public String invocationSummary(Result result) {
        return TOOL_ID + " { action_type=" + result.actionType()
                + ", action_value=" + result.actionValue()
                + ", target_entity_id=" + result.targetEntityId() + " }";
    }

    /**
     * 在真正执行动作前先给一个即时反馈，避免主人长时间只看到等待状态。
     */
    private static void emitActionStartFeedback(EntityMaid maid, long waitingBubbleId, Result result) {
        String preview = previewText(maid, result);
        if (preview.isBlank()) {
            return;
        }
        maid.getChatBubbleManager().addLLMChatText(preview, waitingBubbleId);
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.tool_loop.action_start",
                result.actionType() + "|" + result.actionValue() + "|" + result.targetEntityId()
        );
    }

    /**
     * 为动作生成一条简短、即时、偏口语的开始反馈。
     */
    private static String previewText(EntityMaid maid, Result result) {
        String actionType = result.actionType() == null ? "" : result.actionType().toUpperCase(Locale.ROOT);
        return switch (actionType) {
            case "FOLLOW_ON" -> "好呀，我马上跟紧主人喔 (๑•̀ㅂ•́)و✧";
            case "FOLLOW_OFF" -> "嗯呀，我先留在这里等主人回来～";
            case "SIT_ON" -> "好的，我先乖乖坐好哦。";
            case "SIT_OFF" -> "我起来啦，随时可以继续行动呀。";
            case "SET_SCHEDULE" -> "收到，我这就调整一下接下来的安排。";
            case "SET_TASK" -> "明白啦，我先切换到对应的任务模式。";
            case "ENTER_COMBAT" -> "收到，我先锁定这一个目标，马上过去处理。";
            case "ENTER_COMBAT_GROUP" -> {
                String value = result.actionValue() == null ? "" : result.actionValue();
                if (!value.isBlank()) {
                    yield "收到，我先把附近这一类目标按顺序处理掉。";
                }
                yield "收到，我会按顺序清理这一批目标。";
            }
            default -> {
                if (result.targetEntityId() >= 0) {
                    Entity entity = maid.level().getEntity(result.targetEntityId());
                    if (entity != null) {
                        yield "好哦，我先去处理 " + entity.getName().getString() + "。";
                    }
                }
                yield "";
            }
        };
    }

    public record Result(String actionType, String actionValue, int targetEntityId) {
    }
}
