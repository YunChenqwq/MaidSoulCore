package com.maidsoulcore.forge.tlm;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.maidsoulcore.forge.tlm.context.MaiBotModelContext;
import com.maidsoulcore.forge.tlm.context.MaiBotPersonalityContext;
import com.maidsoulcore.forge.tlm.context.MaiBotPlanStyleContext;
import com.maidsoulcore.forge.tlm.context.MaiBotReplyStyleContext;
import com.maidsoulcore.forge.tlm.context.MaidSoulContextFormatter;
import com.maidsoulcore.forge.tlm.context.MaidSoulEntityContext;
import com.maidsoulcore.forge.tlm.context.MaidSoulRuntimeContext;
import com.maidsoulcore.forge.tlm.llm.MaidSoulRuntimeSite;
import com.maidsoulcore.forge.task.MaidSoulGlobalAttackTask;
import com.maidsoulcore.forge.tlm.tool.MaidSoulCapabilitySearchTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulConversationMemoryTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulReturnHomeTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulSetHomeHereTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulActionTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulCombatTargetTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulPlanTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulRuntimeControlTool;
import com.maidsoulcore.forge.tlm.tool.MaidSoulTraceTool;

/**
 * MaidSoulCore 对 TLM 暴露的真正扩展入口。
 * <p>
 * TLM 会在扫描到 {@link LittleMaidExtension} 后自动实例化这个类，
 * 再调用这里的方法把工具和上下文接进它自己的 AI 系统。
 */
@LittleMaidExtension
public final class MaidSoulLittleMaidExtension implements ILittleMaid {
    @Override
    public void addMaidTask(com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager manager) {
        manager.add(new MaidSoulGlobalAttackTask());
    }

    @Override
    public void registerAIChatSerializer(com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister register) {
        register.register(
                com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType.LLM,
                MaidSoulRuntimeSite.API_TYPE,
                new MaidSoulRuntimeSite.Serializer()
        );
    }

    @Override
    public void registerAITool(ToolRegister register) {
        register.register(new MaidSoulTraceTool());
        register.register(new MaidSoulConversationMemoryTool());
        register.register(new MaidSoulCapabilitySearchTool());
        register.register(new MaidSoulReturnHomeTool());
        register.register(new MaidSoulSetHomeHereTool());
        register.register(new MaidSoulActionTool());
        register.register(new MaidSoulCombatTargetTool());
        register.register(new MaidSoulPlanTool());
        register.register(new MaidSoulRuntimeControlTool());
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        // 先声明分类，再往分类里挂上下文项，这和 TLM 自己的注册方式保持一致。
        if (!GameContextRegister.hasCategory("maidsoul_profile")) {
            register.registerCategory("maidsoul_profile", "Imported companion personality and speaking rules from MaiBot config", true);
        }
        if (!GameContextRegister.hasCategory("maidsoul_runtime")) {
            register.registerCategory("maidsoul_runtime", "MaidSoulCore observed runtime state and last events", false);
        }
        if (!GameContextRegister.hasCategory("maidsoul_models")) {
            register.registerCategory("maidsoul_models", "Configured planner, reply, tool and vision model groups", false);
        }
        if (!GameContextRegister.hasCategory("maidsoul_spatial")) {
            register.registerCategory("maidsoul_spatial", "Current position, rotation, owner relation and home anchor", false);
        }
        if (!GameContextRegister.hasCategory("maidsoul_inventory")) {
            register.registerCategory("maidsoul_inventory", "Inventory, hands and carried resources", false);
        }
        if (!GameContextRegister.hasCategory("maidsoul_perception")) {
            register.registerCategory("maidsoul_perception", "Nearby entities and immediate surroundings", false);
        }
        if (!GameContextRegister.hasCategory("maidsoul_relation")) {
            register.registerCategory("maidsoul_relation", "Favorability and companion relation state", false);
        }
        if (!GameContextRegister.hasCategory("maidsoul_combat")) {
            register.registerCategory("maidsoul_combat", "Current combat plan and attack lock state", false);
        }

        register.registerContext("maidsoul_profile", new MaiBotPersonalityContext());
        register.registerContext("maidsoul_profile", new MaiBotReplyStyleContext());
        register.registerContext("maidsoul_profile", new MaiBotPlanStyleContext());

        register.registerContext("maidsoul_runtime", new MaidSoulRuntimeContext("maidsoul_last_event", "Last observed event",
                snapshot -> snapshot.lastEventType() + (snapshot.lastEventDetail().isBlank() ? "" : " | " + snapshot.lastEventDetail())));
        register.registerContext("maidsoul_runtime", new MaidSoulRuntimeContext("maidsoul_runtime_flags", "Companion runtime flags",
                snapshot -> "schedule=%s, task=%s, homeMode=%s, sitting=%s, sleeping=%s"
                        .formatted(snapshot.schedule(), snapshot.task(), snapshot.homeMode(), snapshot.sitting(), snapshot.sleeping())));
        register.registerContext("maidsoul_runtime", new MaidSoulRuntimeContext("maidsoul_health_state", "Health and owner state",
                snapshot -> "maid=%s, owner=%s, health=%.1f, observedEvents=%d"
                        .formatted(snapshot.maidName(), snapshot.ownerName(), snapshot.health(), snapshot.totalObservedEvents())));
        register.registerContext("maidsoul_runtime", new MaidSoulRuntimeContext("maidsoul_owner_view", "Latest owner view summary",
                snapshot -> snapshot.ownerViewInterpretedSummary().isBlank() ? "no owner view summary" : snapshot.ownerViewInterpretedSummary()));
        register.registerContext("maidsoul_runtime", new MaidSoulRuntimeContext("maidsoul_time_weather", "Time and weather",
                snapshot -> "weather=%s, time_phase=%s".formatted(snapshot.weather(), snapshot.timePhase())));

        register.registerContext("maidsoul_models", new MaiBotModelContext("maidsoul_planner_models", "Planner models", settings -> settings.plannerModels()));
        register.registerContext("maidsoul_models", new MaiBotModelContext("maidsoul_reply_models", "Reply models", settings -> settings.replyModels()));
        register.registerContext("maidsoul_models", new MaiBotModelContext("maidsoul_tool_models", "Tool-use models", settings -> settings.toolModels()));
        register.registerContext("maidsoul_models", new MaiBotModelContext("maidsoul_vlm_models", "Vision models", settings -> settings.vlmModels()));

        register.registerContext("maidsoul_spatial", new MaidSoulEntityContext("maidsoul_position", "Current position",
                MaidSoulContextFormatter::formatPosition));
        register.registerContext("maidsoul_spatial", new MaidSoulEntityContext("maidsoul_rotation", "Current rotation",
                MaidSoulContextFormatter::formatRotation));
        register.registerContext("maidsoul_spatial", new MaidSoulEntityContext("maidsoul_home_anchor", "Home anchor and schedule positions",
                MaidSoulContextFormatter::formatHomeState));
        register.registerContext("maidsoul_spatial", new MaidSoulEntityContext("maidsoul_owner_relation", "Owner relation",
                MaidSoulContextFormatter::formatOwnerRelation));

        register.registerContext("maidsoul_inventory", new MaidSoulEntityContext("maidsoul_inventory_summary", "Inventory summary",
                maid -> MaidSoulContextFormatter.formatInventorySummary(maid, 12)));
        register.registerContext("maidsoul_inventory", new MaidSoulEntityContext("maidsoul_hand_items", "Main and offhand items",
                MaidSoulContextFormatter::formatHands));

        register.registerContext("maidsoul_perception", new MaidSoulEntityContext("maidsoul_nearby_entities", "Nearby entities",
                maid -> MaidSoulContextFormatter.formatNearbyEntities(maid, 12.0d, 8)));
        register.registerContext("maidsoul_relation", new MaidSoulEntityContext("maidsoul_favorability", "Favorability summary",
                maid -> "favorability=%d, level=%d".formatted(maid.getFavorability(), maid.getFavorabilityManager().getLevel())));
        register.registerContext("maidsoul_combat", new MaidSoulEntityContext("maidsoul_combat_plan", "Combat lock and sequential attack plan",
                com.maidsoulcore.forge.service.MaidSoulActionExecutorService::describeAttackPlan));
        register.registerContext("maidsoul_runtime", new MaidSoulEntityContext("maidsoul_scheduler_plan", "Current local plan scheduler state",
                com.maidsoulcore.forge.service.MaidSoulPlanService::describePlanState));
        register.registerContext("maidsoul_runtime", new MaidSoulEntityContext("maidsoul_chat_runtime", "Current chat runtime state",
                com.maidsoulcore.forge.service.MaidSoulChatLoopRuntimeService::describeRuntimeState));
    }
}
