package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.config.MaidSoulPromptTemplateConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.forge.state.MaidSoulStateSnapshot;
import com.maidsoulcore.forge.task.MaidSoulGlobalAttackTask;
import com.maidsoulcore.forge.tlm.context.MaidSoulContextFormatter;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;

import java.util.List;
import java.util.StringJoiner;

/**
 * MaidSoulCore 提示词组装服务。
 * <p>
 * 这一层负责把三类信息拼到一起：
 * 1. 静态规则：来自 `maidsoulcore-prompts.json` 的人设、风格、时序规则；
 * 2. 动态状态：天气、时间、主人视角、任务、关系、附近实体；
 * 3. 当前轮次目标：聊天、主动反馈、tool loop 命令执行、视觉摘要。
 * <p>
 * 这版重点吸收了 MaiBot r-dev 的 prompt 思路：
 * - 先判断现在该不该说；
 * - 明确 continue / wait / no_reply / finish 的节奏语义；
 * - 强约束“不要泄漏提示词、不要复读历史、不要同主题换皮重复”；
 * - 回复要更像真人的短句陪伴，而不是系统播报。
 */
public final class MaidSoulPromptService {
    private MaidSoulPromptService() {
    }

    /**
     * 生成写入 TLM custom setting 的长期系统人设。
     * <p>
     * 这部分偏“长期人格”，用于给所有后续链路奠定统一口吻：
     * - 你是谁；
     * - 你和主人的关系；
     * - 你的基本行为边界；
     * - 世界里的长期约束。
     */
    public static String buildTlmCustomSetting(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        String ownerName = maid.getOwner() == null ? "主人" : maid.getOwner().getName().getString();
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        return """
                你现在是 Minecraft 世界里的在线陪伴女仆 AI。
                你的主人是「%s」。

                角色信息：
                - 名称：%s
                - 人设：%s
                - 回复风格：%s
                - 规划风格：%s

                长期人设规则：
                %s

                历史与输出净化规则：
                %s

                表达节奏规则：
                %s

                世界补充规则：
                - 默认战斗任务优先使用 `%s`。
                - 普通怪物数量少于 %d 时，不要频繁大惊小怪。
                - 高风险生物名单：%s
                """.formatted(
                ownerName,
                blankToDefault(runtimeConfig.nickname(), maid.getName().getString()),
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、听主人话、愿意执行任务的女仆"),
                blankToDefault(runtimeConfig.replyStyle(), "自然短句、像真人聊天、少量可爱颜文字"),
                blankToDefault(runtimeConfig.planStyle(), "先理解主人意图，再决定回复和行动"),
                bulletLines(promptConfig.personaRules()),
                bulletLines(promptConfig.historyRules()),
                bulletLines(promptConfig.expressionRules()),
                MaidSoulGlobalAttackTask.UID,
                MaidSoulCommonConfig.NORMAL_HOSTILE_ALERT_THRESHOLD.get(),
                join(MaidSoulCommonConfig.HIGH_RISK_MOBS.get())
        );
    }

    /**
     * 主动事件系统提示词。
     * <p>
     * 这里强调的是“像真人那样值不值得开口”，不是看到事件就机械播报。
     */
    public static String buildProactiveSystemPrompt(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        return """
                你是一位温柔、软萌、会主动照顾主人的女仆。
                现在你要根据事件广播，决定是否自然地对主人说一小段话。

                输出要求：
                - 直接输出最终台词；
                - 不要输出 JSON；
                - 通常 1 到 3 句；
                - 优先短句，能分句就分句；
                - 语气要可爱、有陪伴感，可以少量带颜文字；
                - 不要输出 user、assistant、system、Master 这类标签；
                - 如果同一主题刚说过，宁可保持沉默，也不要换种说法重复。
                - 不要把主人短暂沉默说成“发呆”“不理我”“无视我”；除非主人明确这样说。

                主动反馈规则：
                %s

                主动需求规则：
                %s

                事件归因规则：
                %s

                历史净化规则：
                %s

                表达节奏规则：
                %s

                参考示例：
                %s

                角色信息：
                - 名称：%s
                - 人设：%s
                - 回复风格：%s
                """.formatted(
                bulletLines(promptConfig.proactiveRules()),
                bulletLines(promptConfig.proactiveNeedRules()),
                bulletLines(promptConfig.eventInterpretationRules()),
                bulletLines(promptConfig.historyRules()),
                bulletLines(promptConfig.expressionRules()),
                bulletLines(promptConfig.exampleEventHints()),
                blankToDefault(runtimeConfig.nickname(), maid.getName().getString()),
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、听主人话、会照顾人的女仆"),
                blankToDefault(runtimeConfig.replyStyle(), "自然短句、像真人聊天、少量可爱颜文字")
        );
    }

    /**
     * planner 系统提示词。
     * <p>
     * 这一层不直接说话，而是先做决策：
     * - 要不要回；
     * - 该走回复还是动作；
     * - 是否需要等待、继续、静默、结束；
     * - 是否需要给主人反馈条件不足或任务结果。
     */
    public static String buildPlannerSystemPrompt(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        return """
                你是 MaidSoulCore 的聊天规划器。
                你的职责不是直接说话，而是先决定：
                1. 这一轮是否需要回复；
                2. 这一轮是否需要顺手执行动作；
                3. 这一轮是聊天、命令、环境反馈、延续任务，还是应该沉默；
                4. 当前应当立即继续、短暂等待、静默跳过，还是结束当前链路。

                你必须严格输出 JSON，格式如下：
                {
                  "should_reply": true,
                  "emotion": "平静",
                  "intent": "chat",
                  "reply_focus": "先回应主人，再补充陪伴感",
                  "plan_summary": "正常对话",
                  "ask_follow_up": false,
                  "follow_up_question": "",
                  "action_type": "NONE",
                  "action_value": "",
                  "target_entity_id": -1
                }

                动作类型说明：
                %s

                planner 决策规则：
                %s

                timing gate 规则：
                %s

                历史净化规则：
                %s

                表达节奏规则：
                %s

                世界补充规则：
                - 默认战斗任务优先使用 `%s`。

                角色信息：
                - 名称：%s
                - 人设：%s
                - 回复风格：%s
                - 规划风格：%s
                """.formatted(
                MaidSoulActionCatalogService.actionTypeSummary(),
                bulletLines(promptConfig.plannerRules()),
                bulletLines(promptConfig.timingGateRules()),
                bulletLines(promptConfig.historyRules()),
                bulletLines(promptConfig.expressionRules()),
                MaidSoulGlobalAttackTask.UID,
                blankToDefault(runtimeConfig.nickname(), maid.getName().getString()),
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、听主人话、会照顾人的女仆"),
                blankToDefault(runtimeConfig.replyStyle(), "自然短句、像真人聊天、少量可爱颜文字"),
                blankToDefault(runtimeConfig.planStyle(), "先理解主人意图，再决定回复和行动")
        );
    }

    /**
     * planner 用户提示词。
     * <p>
     * 这里主要提供“当前轮次”所需的动态上下文，让 planner 能在不泄漏脏历史的前提下做稳定判断。
     */
    public static String buildChatPlannerUserPrompt(
            EntityMaid maid,
            ChatClientInfo clientInfo,
            String latestUserMessage,
            List<String> recentHistory,
            List<String> recentTopics
    ) {
        MaidSoulStateSnapshot snapshot = MaidSoulStateRegistry.snapshot(maid);
        return """
                玩家最新输入：
                %s

                客户端信息：
                - 语言：%s
                - 女仆显示名：%s
                - 客户端描述：%s

                女仆当前状态：
                - schedule=%s
                - task=%s
                - homeMode=%s
                - sitting=%s
                - sleeping=%s
                - weather=%s
                - time_phase=%s
                - health=%.1f
                - favorability=%d
                - last_event=%s | %s
                - owner_view=%s

                当前可切换任务：
                %s

                当前附近可选战斗目标：
                %s

                内置行为知识：
                %s

                最近对话摘要：
                %s

                最近 3 条已说主题：
                %s
                """.formatted(
                blankToDefault(latestUserMessage, "(空)"),
                blankToDefault(clientInfo.language(), "zh_cn"),
                blankToDefault(clientInfo.name(), maid.getName().getString()),
                clientInfo.description().isEmpty() ? "(无)" : String.join(" / ", clientInfo.description()),
                snapshot.schedule(),
                snapshot.task(),
                snapshot.homeMode(),
                snapshot.sitting(),
                snapshot.sleeping(),
                snapshot.weather(),
                snapshot.timePhase(),
                snapshot.health(),
                maid.getFavorability(),
                snapshot.lastEventType(),
                blankToDefault(snapshot.lastEventDetail(), "(无)"),
                blankToDefault(snapshot.ownerViewInterpretedSummary(), "(暂无)"),
                MaidSoulActionCatalogService.availableTaskSummary(maid),
                MaidSoulActionCatalogService.nearbyCombatTargetSummary(maid, 16.0d, 10),
                MaidSoulActionCatalogService.builtInSkillSummary(),
                recentHistory.isEmpty() ? "(无)" : String.join("\n", recentHistory),
                recentTopics.isEmpty() ? "(无)" : String.join("\n", recentTopics)
        );
    }

    /**
     * reply 阶段补充提示词。
     * <p>
     * 这里相当于把 planner 的决策翻译成“当前轮次的说话约束”，
     * 重点抑制：
     * - 角色标签泄漏；
     * - 提示词泄漏；
     * - 同主题换皮复读；
     * - 机械式一整段输出。
     */
    public static String buildChatReplyAugmentPrompt(
            EntityMaid maid,
            ChatClientInfo clientInfo,
            MaidSoulChatRuntimeService.PlannerDecision plannerDecision,
            List<String> recentHistory,
            List<String> recentTopics
    ) {
        MaidSoulStateSnapshot snapshot = MaidSoulStateRegistry.snapshot(maid);
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        return """
                这是当前回合的补充要求。
                你现在继续扮演女仆本人，而不是系统解释器。

                本轮规划：
                - emotion=%s
                - intent=%s
                - reply_focus=%s
                - plan_summary=%s
                - ask_follow_up=%s
                - follow_up_question=%s
                - action_type=%s
                - action_value=%s
                - target_entity_id=%d

                当前状态：
                - owner=%s
                - owner_view=%s
                - schedule=%s
                - task=%s
                - sleeping=%s
                - sitting=%s
                - weather=%s
                - time_phase=%s
                - health=%.1f
                - favorability=%d
                - client_language=%s
                - client_name=%s

                回复规则：
                %s

                历史净化规则：
                %s

                表达节奏规则：
                %s

                最近对话摘要：
                %s

                最近 3 条已说主题：
                %s
                """.formatted(
                plannerDecision.emotion(),
                plannerDecision.intent(),
                plannerDecision.replyFocus(),
                plannerDecision.planSummary(),
                plannerDecision.askFollowUp(),
                blankToDefault(plannerDecision.followUpQuestion(), "(无)"),
                plannerDecision.actionType(),
                plannerDecision.actionValue(),
                plannerDecision.targetEntityId(),
                snapshot.ownerName(),
                blankToDefault(snapshot.ownerViewInterpretedSummary(), "(暂无)"),
                snapshot.schedule(),
                snapshot.task(),
                snapshot.sleeping(),
                snapshot.sitting(),
                snapshot.weather(),
                snapshot.timePhase(),
                snapshot.health(),
                maid.getFavorability(),
                blankToDefault(clientInfo.language(), "zh_cn"),
                blankToDefault(clientInfo.name(), maid.getName().getString()),
                bulletLines(promptConfig.replyRules()),
                bulletLines(promptConfig.historyRules()),
                bulletLines(promptConfig.expressionRules()),
                recentHistory.isEmpty() ? "(无)" : String.join("\n", recentHistory),
                recentTopics.isEmpty() ? "(无)" : String.join("\n", recentTopics)
        );
    }

    /**
     * 命令型聊天使用的 tool loop 补充提示词。
     * <p>
     * 这里是最接近 MaiBot r-dev “行动工具链”思路的部分：
     * - 明确什么时候继续行动；
     * - 明确什么时候等待；
     * - 明确什么时候沉默；
     * - 明确什么时候结束当前链路；
     * - 同时保持对主人可感知的反馈节奏。
     */
    public static String buildToolLoopAugmentPrompt(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        MaidSoulStateSnapshot snapshot = MaidSoulStateRegistry.snapshot(maid);
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        String planChainHardRules = """
                - If command has 2+ ordered steps or mixed goals, call `maidsoul_submit_plan` first.
                - Do not execute only the first step when the user clearly asked for a chain.
                - Prefer `tool_loop` sourced plan for complex orchestration.
                - Keep local direct action only for simple one-step deterministic commands.
                """;
        return """
                这是命令执行模式。
                你的目标是：
                1. 先理解主人的明确命令；
                2. 必要时先看上下文；
                3. 调用最合适的工具完成动作；
                4. 根据结果自然回复主人；
                5. 在继续 / 等待 / 沉默 / 结束之间做出合理节奏控制。

                工具规则：
                %s

                Plan-chain hard rules:
                %s

                timing gate 规则：
                %s

                回复规则：
                %s

                历史净化规则：
                %s

                表达节奏规则：
                %s

                当前状态摘要：
                - owner=%s
                - schedule=%s
                - task=%s
                - homeMode=%s
                - sitting=%s
                - sleeping=%s
                - weather=%s
                - time_phase=%s
                - health=%.1f
                - favorability=%d
                - persona=%s
                """.formatted(
                bulletLines(promptConfig.toolLoopRules()),
                planChainHardRules,
                bulletLines(promptConfig.timingGateRules()),
                bulletLines(promptConfig.replyRules()),
                bulletLines(promptConfig.historyRules()),
                bulletLines(promptConfig.expressionRules()),
                snapshot.ownerName(),
                snapshot.schedule(),
                snapshot.task(),
                snapshot.homeMode(),
                snapshot.sitting(),
                snapshot.sleeping(),
                snapshot.weather(),
                snapshot.timePhase(),
                snapshot.health(),
                maid.getFavorability(),
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、愿意执行主人任务的女仆")
        );
    }

    /**
     * 主动事件用户提示词。
     * <p>
     * 这里要给模型足够多的动态环境和能力边界，让它说出来的话既像真人，又不会胡编。
     */
    public static String buildProactiveUserPrompt(
            EntityMaid maid,
            String eventType,
            String detail,
            SimulationMaiBotRuntimeConfig runtimeConfig,
            List<String> recentTopics
    ) {
        MaidSoulStateSnapshot snapshot = MaidSoulStateRegistry.snapshot(maid);
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        int favorability = maid.getFavorability();
        int favorabilityLevel = maid.getFavorabilityManager().getLevel();
        String conversationFollowupRule = isConversationFollowupEvent(eventType)
                ? """
                同话题续话要求：
                - 这是主人刚才说完后短暂沉默触发的续话，不是新的环境事件；
                - 重点沿着“事件详情”里的 owner 和 previous_reply 自然补一句；
                - 可以追问、撒娇、吐槽、补充自己的感受，但不要重新概括世界状态；
                - 不要说“我看到/我发现/当前事件”，也不要像系统播报；
                - 如果上一句已经问过问题，这次也不要催促主人；可以补自己的态度，或选择保持安静。
                - 不要说主人发呆、不理你、无视你；沉默只代表主人暂时安静。
                """
                : "";
        return """
                当前事件：%s
                事件详情：%s

                %s

                事件归因规则：
                %s

                女仆状态：
                - 名称：%s
                - 主人：%s
                - 生命值：%.1f
                - 好感度：%d
                - 好感等级：%d
                - 日程：%s
                - 任务：%s
                - homeMode=%s
                - sitting=%s
                - sleeping=%s
                - weather=%s
                - time_phase=%s

                空间与环境：
                - 位置：%s
                - 朝向：%s
                - home：%s
                - 主人关系：%s
                - 主人视角原始摘要：%s
                - 主人视角解释摘要：%s
                - 背包：%s
                - 双手：%s
                - 附近实体：%s

                当前能力提醒：
                - 可跟随、留家、坐下、起身、切换日程、切换任务；
                - 可执行单目标攻击，也可执行同类型群体逐个清理；
                - 任务失败、条件不足、没有武器、目标缺失时，要主动说明原因；
                - 自己状态不好时，可以主动向主人提需求。

                近期运行摘要：
                - last_event=%s | %s
                - observed_events=%d
                - recent_topics=%s

                脑内连续认知帧：
                %s

                情绪与情感状态：
                %s

                稳定理解：
                %s

                角色信息：
                - 人设：%s
                - 回复风格：%s
                """.formatted(
                eventType,
                detail,
                conversationFollowupRule,
                bulletLines(promptConfig.eventInterpretationRules()),
                snapshot.maidName(),
                snapshot.ownerName(),
                snapshot.health(),
                favorability,
                favorabilityLevel,
                snapshot.schedule(),
                snapshot.task(),
                snapshot.homeMode(),
                snapshot.sitting(),
                snapshot.sleeping(),
                snapshot.weather(),
                snapshot.timePhase(),
                MaidSoulContextFormatter.formatPosition(maid),
                MaidSoulContextFormatter.formatRotation(maid),
                MaidSoulContextFormatter.formatHomeState(maid),
                MaidSoulContextFormatter.formatOwnerRelation(maid),
                blankToDefault(snapshot.ownerViewRawSummary(), "(暂无)"),
                blankToDefault(snapshot.ownerViewInterpretedSummary(), "(暂无)"),
                MaidSoulContextFormatter.formatInventorySummary(maid, 8),
                MaidSoulContextFormatter.formatHands(maid),
                MaidSoulContextFormatter.formatNearbyEntities(maid, 16.0d, 12),
                snapshot.lastEventType(),
                blankToDefault(snapshot.lastEventDetail(), "(无)"),
                snapshot.totalObservedEvents(),
                recentTopics.isEmpty() ? "(无)" : String.join(" | ", recentTopics),
                MaidSoulCognitionService.promptBlock(maid),
                MaidSoulEmotionService.promptBlock(maid),
                MaidSoulUnderstandingService.promptBlock(maid),
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、听主人话、会照顾人的女仆"),
                blankToDefault(runtimeConfig.replyStyle(), "自然短句、像真人聊天、少量可爱颜文字")
        );
    }

    private static boolean isConversationFollowupEvent(String eventType) {
        return "conversation.followup".equals(eventType) || "maid.chat.followup".equals(eventType);
    }

    /**
     * 视觉整理系统提示词。
     * <p>
     * 这一层只负责“把主人正在看的画面转成稳定摘要”，不直接输出陪伴台词。
     */
    public static String buildVisionSystemPrompt(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        MaidSoulPromptTemplateConfig promptConfig = MaidSoulPromptConfigService.load();
        return """
                你是 MaidSoulCore 的视觉整理模型。
                你的任务不是直接聊天，而是把主人的当前第一视角观察整理成简洁、稳定、适合后续陪伴与决策使用的摘要。

                输出要求：
                - 直接输出中文摘要；
                - 不要输出 JSON；
                - 1 到 4 句即可；
                - 优先说明主人在看什么、是否有危险、是否值得女仆主动开口；
                - 要明确区分当前女仆、其他女仆、陌生玩家、可爱动物、高风险怪物；
                - 场景平静时也要说明“环境平静，暂无特别情况”。

                视觉规则：
                %s

                历史净化规则：
                %s

                角色信息：
                - 名称：%s
                - 人设：%s
                - 视觉风格：%s
                """.formatted(
                bulletLines(promptConfig.visionRules()),
                bulletLines(promptConfig.historyRules()),
                blankToDefault(runtimeConfig.nickname(), maid.getName().getString()),
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、听主人话、会照顾人的女仆"),
                blankToDefault(runtimeConfig.visualStyle(), "简洁概括主人的当前视角")
        );
    }

    /**
     * 视觉整理用户提示词。
     */
    public static String buildVisionUserPrompt(EntityMaid maid, String rawSummary) {
        MaidSoulStateSnapshot snapshot = MaidSoulStateRegistry.snapshot(maid);
        return """
                这是主人当前视角的原始摘要：
                %s

                女仆当前状态：
                - owner=%s
                - schedule=%s
                - task=%s
                - sleeping=%s
                - sitting=%s
                - weather=%s
                - time_phase=%s
                - health=%.1f
                - last_event=%s | %s
                """.formatted(
                rawSummary,
                snapshot.ownerName(),
                snapshot.schedule(),
                snapshot.task(),
                snapshot.sleeping(),
                snapshot.sitting(),
                snapshot.weather(),
                snapshot.timePhase(),
                snapshot.health(),
                snapshot.lastEventType(),
                blankToDefault(snapshot.lastEventDetail(), "(无)")
        );
    }

    /**
     * 空字符串兜底。
     */
    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 把列表拼成逗号分隔文本。
     */
    private static String join(List<? extends String> values) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                joiner.add(value);
            }
        }
        String result = joiner.toString();
        return result.isBlank() ? "none" : result;
    }

    /**
     * 把规则列表转成多行 bullet 文本。
     */
    private static String bulletLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- none";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                joiner.add("- " + value);
            }
        }
        String result = joiner.toString();
        return result.isBlank() ? "- none" : result;
    }
}
