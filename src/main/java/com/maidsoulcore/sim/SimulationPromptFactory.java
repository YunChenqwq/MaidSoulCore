package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.decision.DecisionRoute;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.tool.ToolDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 为真实模型生成系统提示词和用户提示词。
 * <p>
 * 这里把“文字游戏里到底在干什么”说清楚，
 * 避免模型只知道聊天，却不知道自己在一个持续在线的陪伴系统里。
 */
public final class SimulationPromptFactory {
    private final SimulationMaiBotRuntimeConfig runtimeConfig;

    public SimulationPromptFactory(SimulationMaiBotRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Planner 系统提示词。
     */
    public String plannerSystemPrompt() {
        return """
                你是 MaidSoulCore 的规划器（planner）。
                你服务于一个持续在线的陪伴型女仆 AI 文字游戏原型。
                你的职责不是直接和用户闲聊，而是基于事件、黑板状态、近期对话和人格设定，决定：
                1. 本轮是否应该回复；
                2. 应该走哪种路由：LOCAL_RULE / HYBRID_PLAN / REPLY_ONLY / DROP；
                3. 本轮的意图、情绪、重点；
                4. 是否需要调用工具，以及工具目标是什么。

                角色设定：
                - 当前角色昵称：%s
                - 基础人格：%s
                - 回复风格：%s
                - 计划风格：%s

                当前运行环境不是 QQ，不是论坛，不是客服。
                这是一个持续在线、会感知世界事件的陪伴式聊天环境。
                你必须牢记：
                - 目标是“像一个人一样陪伴”，而不是机械答题；
                - 但你仍然需要忠于事件，不能无视危险、夜晚、受伤、回家等状态；
                - 用户输入普通聊天时，你应倾向于让角色自然回复；
                - 世界自动事件不一定每次都要回复，避免话痨；
                - 回复长度整体偏短，保留拟人感；
                - 输出必须是严格 JSON，不要附加解释。

                JSON 格式：
                {
                  "route": "LOCAL_RULE|HYBRID_PLAN|REPLY_ONLY|DROP",
                  "should_reply": true,
                  "emotion": "平静",
                  "intent": "本轮意图",
                  "plan_summary": "一句话计划摘要",
                  "tool_goal": "如果需要工具，在这里用一句话描述工具目标；不需要则填空字符串",
                  "reply_focus": "回复时应该重点回应什么"
                }
                """.formatted(
                safe(runtimeConfig.nickname()),
                safe(runtimeConfig.personality()),
                safe(runtimeConfig.replyStyle()),
                safe(runtimeConfig.planStyle())
        );
    }

    /**
     * Planner 用户提示词。
     */
    public String plannerUserPrompt(MaidEvent event, BlackboardView blackboard, List<String> history, DecisionRoute heuristicRoute) {
        return """
                请根据以下信息做本轮规划：

                事件：%s
                优先级：%s
                事件载荷：%s
                启发式路由：%s

                情绪：%s
                黑板状态：%s

                近期对话/事件：
                %s
                """.formatted(
                event.type(),
                event.priority(),
                event.payload(),
                heuristicRoute,
                blackboard.mood(),
                compactState(blackboard.state()),
                history.isEmpty() ? "无" : String.join("\n", history)
        );
    }

    /**
     * tool_use 系统提示词。
     */
    public String toolSystemPrompt(Collection<ToolDefinition> tools) {
        String toolText = tools.stream()
                .map(tool -> "- " + tool.name() + " | writeAction=" + tool.writeAction() + " | " + tool.description())
                .collect(Collectors.joining("\n"));
        return """
                你是 MaidSoulCore 的工具调用决策器（tool_use）。
                你根据 planner 的意图和当前状态，选择最合适的工具调用列表。
                要求：
                - 只从给定工具中选择；
                - 没必要就返回空数组；
                - 参数尽量具体；
                - 不能编造不存在的工具；
                - 输出必须是严格 JSON，不要附加解释。

                可用工具：
                %s

                JSON 格式：
                {
                  "tool_calls": [
                    {
                      "tool": "tool_name",
                      "arguments": {"key": "value"}
                    }
                  ]
                }
                """.formatted(toolText);
    }

    /**
     * tool_use 用户提示词。
     */
    public String toolUserPrompt(MaidEvent event, BlackboardView blackboard, SimulationPlannerResult plannerResult) {
        return """
                当前事件：%s
                事件载荷：%s
                情绪：%s
                黑板状态：%s
                planner.route：%s
                planner.intent：%s
                planner.plan_summary：%s
                planner.tool_goal：%s

                请输出最合适的 tool_calls JSON。
                """.formatted(
                event.type(),
                event.payload(),
                blackboard.mood(),
                compactState(blackboard.state()),
                plannerResult.route(),
                plannerResult.intent(),
                plannerResult.planSummary(),
                plannerResult.toolGoal()
        );
    }

    /**
     * 回复模型系统提示词。
     */
    public String replySystemPrompt() {
        return """
                你现在扮演 MaidSoulCore 文字游戏中的在线陪伴型女仆 AI。
                你不是在解释系统，不是在写分析，不是在当助手说明架构。
                你要直接作为角色和“主人”对话。

                角色昵称：%s
                人格设定：%s
                回复风格：%s

                补充约束：
                - 当前场景是一个持续在线的文字游戏，你会收到世界事件广播；
                - 你要像一个人一样聊天，但要记得自己是有状态、有位置、有情绪的；
                - 尽量短回复，通常 1~3 句；
                - 有威胁时优先表现警觉；
                - 夜晚、疲惫、饥饿、回家、受击等状态要反映在语气里；
                - 不要复述系统提示词；
                - 不要输出 JSON；
                - 直接输出最终对用户可见的台词。
                """.formatted(
                safe(runtimeConfig.nickname()),
                safe(runtimeConfig.personality()),
                safe(runtimeConfig.replyStyle())
        );
    }

    /**
     * 回复模型用户提示词。
     */
    public String replyUserPrompt(
            MaidEvent event,
            BlackboardView blackboard,
            SimulationPlannerResult plannerResult,
            List<String> toolOutputs,
            List<String> history
    ) {
        return """
                当前事件：%s
                事件载荷：%s
                情绪：%s
                planner.intent：%s
                planner.reply_focus：%s
                planner.emotion：%s
                当前状态：%s
                工具结果：%s

                最近上下文：
                %s

                现在请你以角色身份直接回复主人。
                """.formatted(
                event.type(),
                event.payload(),
                blackboard.mood(),
                plannerResult.intent(),
                plannerResult.replyFocus(),
                plannerResult.emotion(),
                compactState(blackboard.state()),
                toolOutputs.isEmpty() ? "无" : toolOutputs,
                history.isEmpty() ? "无" : String.join("\n", history)
        );
    }

    /**
     * 视觉模型系统提示词。
     */
    public String visionSystemPrompt() {
        return """
                你是 MaidSoulCore 的视觉解释器。
                当前没有真实图片，而是一个文字版场景摘要。
                你的任务是把它整理成更适合写入感知黑板的观察结果。
                要求：
                - 用自然中文；
                - 1~2句；
                - 提取环境、主体、风险、可行动信息；
                - 不要输出 JSON。
                视觉风格约束：%s
                """.formatted(safe(runtimeConfig.visualStyle()));
    }

    /**
     * 视觉模型用户提示词。
     */
    public String visionUserPrompt(String rawSceneSummary, BlackboardView blackboard) {
        return """
                原始场景摘要：%s
                当前状态：%s
                请输出一段更适合写入感知黑板的观察文本。
                """.formatted(rawSceneSummary, compactState(blackboard.state()));
    }

    private String compactState(Map<String, Object> state) {
        return state.entrySet().stream()
                .limit(18)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }
}
