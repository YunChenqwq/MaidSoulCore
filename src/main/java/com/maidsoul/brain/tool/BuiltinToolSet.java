package com.maidsoul.brain.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天核心内置工具声明。
 *
 * <p>这些工具只覆盖聊天主链路最小闭环：回复、等待、暂停、结束、查询记忆。
 * Forge 行为、战斗工具、视角工具后续应作为额外 ToolProvider 注入，而不是写死在核心里。</p>
 */
public final class BuiltinToolSet {
    private BuiltinToolSet() {
    }

    public static List<ToolSpec> plannerTools(boolean includeTimingTools) {
        return plannerTools(includeTimingTools, false);
    }

    public static List<ToolSpec> plannerTools(boolean includeTimingTools, boolean includeViewTool) {
        return plannerTools(includeTimingTools, includeViewTool, false);
    }

    public static List<ToolSpec> plannerTools(boolean includeTimingTools, boolean includeViewTool, boolean includeEnvironmentTool) {
        java.util.ArrayList<ToolSpec> tools = new java.util.ArrayList<>();
        tools.add(reply());
        if (includeTimingTools) {
            tools.add(waitTool());
            tools.add(noAction());
        }
        tools.add(finish());
        tools.add(queryMemory());
        if (includeEnvironmentTool) {
            tools.add(scanEnvironment());
        }
        if (includeViewTool) {
            tools.add(observeView());
        }
        tools.add(toolSearch());
        return List.copyOf(tools);
    }

    private static List<ToolSpec> legacyPlannerTools(boolean includeTimingTools, boolean includeViewTool) {
        if (includeTimingTools) {
            return includeViewTool
                    ? List.of(reply(), waitTool(), noAction(), finish(), queryMemory(), observeView(), toolSearch())
                    : List.of(reply(), waitTool(), noAction(), finish(), queryMemory(), toolSearch());
        }
        return includeViewTool
                ? List.of(reply(), finish(), queryMemory(), observeView(), toolSearch())
                : List.of(reply(), finish(), queryMemory(), toolSearch());
    }

    public static List<ToolSpec> timingTools() {
        return List.of(continueTool(), waitTool(), noAction());
    }

    public static ToolSpec reply() {
        return new ToolSpec(
                "reply",
                "当现在应该对用户发出一条真正可见的回复时调用。调用后由回复器生成可见发言。",
                objectSchema(properties(
                        "target_message_id", stringSchema("要回应的消息 id；没有明确目标时留空。"),
                        "reason", stringSchema("为什么现在应该回复，以及回复方向；只写一句短方向，不超过40个中文字符，不要写长篇分析。"),
                        "reference_info", stringSchema("可选参考信息，只给回复器看，不会直接展示；不要编造现场事实。"),
                        "affect_event_kind", stringSchema("结构化情绪事件：OWNER_APOLOGY/OWNER_ATTACK/OWNER_DISTRESS/OWNER_AFFECTION/OWNER_QUESTION/OWNER_SHORT_FEEDBACK；无明确事件填空字符串。"),
                        "affect_event_intensity", numberSchema("情绪事件强度，0-100；无明确事件填0，普通轻微=25，中等=50，强烈=75。"),
                        "affect_event_note", stringSchema("可选事件依据，用一句短话概括，不要复述长聊天。"),
                        "memory_event_type", stringSchema("可选结构化记忆类型：DIALOGUE/PREFERENCE/PROMISE/RELATION/EMOTION/WORLD/SUMMARY；没有明确长期价值填空字符串。"),
                        "memory_event_layer", stringSchema("可选结构化记忆层：user_profile/relationship_event/self_memory/world_fact/repair_debt/summary；不要从关键词猜，只有语义明确时填写。"),
                        "memory_event_content", stringSchema("可选结构化记忆内容，用稳定事实句概括；没有明确长期价值填空字符串。"),
                        "memory_event_tags", stringSchema("可选结构化标签，用英文逗号分隔，如 preference,boundary；没有则空。"),
                        "memory_event_importance", numberSchema("可选记忆重要度 1-5；没有明确长期价值填0。")
                ), List.of("reason", "affect_event_kind", "affect_event_intensity")),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec waitTool() {
        return new ToolSpec(
                "wait",
                "等待一段时间后再判断。用于用户可能还没说完，或者当前更适合把发言权交还给用户。",
                objectSchema(properties(
                        "seconds", numberSchema("等待秒数。"),
                        "reason", stringSchema("为什么等待。"),
                        "affect_event_kind", stringSchema("结构化情绪事件；无明确事件填空字符串。"),
                        "affect_event_intensity", numberSchema("情绪事件强度，0-100；无明确事件填0。"),
                        "affect_event_note", stringSchema("可选事件依据，用一句短话概括。"),
                        "memory_event_type", stringSchema("可选结构化记忆类型；没有明确长期价值填空字符串。"),
                        "memory_event_layer", stringSchema("可选结构化记忆层；没有明确长期价值填空字符串。"),
                        "memory_event_content", stringSchema("可选结构化记忆内容；没有明确长期价值填空字符串。"),
                        "memory_event_tags", stringSchema("可选结构化标签，用英文逗号分隔。"),
                        "memory_event_importance", numberSchema("可选记忆重要度 1-5；没有明确长期价值填0。")
                ), List.of("affect_event_kind", "affect_event_intensity")),
                Map.of("stage", "timing")
        );
    }

    public static ToolSpec noAction() {
        return new ToolSpec(
                "no_action",
                "本轮不发言，等待新的外部消息。",
                objectSchema(properties(
                        "reason", stringSchema("为什么本轮不发言。"),
                        "affect_event_kind", stringSchema("结构化情绪事件；无明确事件填空字符串。"),
                        "affect_event_intensity", numberSchema("情绪事件强度，0-100；无明确事件填0。"),
                        "affect_event_note", stringSchema("可选事件依据，用一句短话概括。"),
                        "memory_event_type", stringSchema("可选结构化记忆类型；没有明确长期价值填空字符串。"),
                        "memory_event_layer", stringSchema("可选结构化记忆层；没有明确长期价值填空字符串。"),
                        "memory_event_content", stringSchema("可选结构化记忆内容；没有明确长期价值填空字符串。"),
                        "memory_event_tags", stringSchema("可选结构化标签，用英文逗号分隔。"),
                        "memory_event_importance", numberSchema("可选记忆重要度 1-5；没有明确长期价值填0。")
                ), List.of("affect_event_kind", "affect_event_intensity")),
                Map.of("stage", "timing")
        );
    }

    public static ToolSpec finish() {
        return new ToolSpec(
                "finish",
                "结束本轮内部思考，不继续执行更多工具。",
                objectSchema(Map.of("reason", stringSchema("为什么结束本轮。")), List.of()),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec queryMemory() {
        return new ToolSpec(
                "query_memory",
                "当回复依赖过去约定、偏好、长期信息或之前聊过的内容时调用。",
                objectSchema(Map.of(
                        "query", stringSchema("要检索的记忆查询。"),
                        "reason", stringSchema("为什么需要查询记忆。")
                ), List.of("query")),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec observeView() {
        return new ToolSpec(
                "observe_view",
                "当回复需要主人当前第一人称视角、附近危险、可见实体/方块或现场状态时调用。工具会返回一段文字视觉摘要。",
                objectSchema(Map.of(
                        "reason", stringSchema("为什么本轮需要观察当前视角，只写一句短理由。")
                ), List.of("reason")),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec scanEnvironment() {
        return new ToolSpec(
                "scan_environment",
                "读取 Minecraft 服务端结构化现场状态：主人视线焦点、是否正在看当前女仆、附近实体/怪物、时间天气、生命值、距离。它不截图、不调用视觉模型，适合先确认现场事实。",
                objectSchema(Map.of(
                        "reason", stringSchema("为什么本轮需要扫描结构化现场，只写一句短理由。")
                ), List.of("reason")),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec toolSearch() {
        return new ToolSpec(
                "tool_search",
                "在 deferred tools 列表中按名称或关键词搜索工具，并将命中的工具加入后续轮次的可用工具列表。",
                objectSchema(properties(
                        "query", stringSchema("要搜索的工具名、前缀或关键词。"),
                        "limit", numberSchema("最多返回多少个匹配工具。")
                ), List.of("query")),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec continueTool() {
        return new ToolSpec(
                "continue",
                "立刻进入下一轮完整思考、搜集信息、回复或工具执行。",
                objectSchema(Map.of("reason", stringSchema("为什么继续。")), List.of()),
                Map.of("stage", "timing")
        );
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        );
    }

    private static Map<String, Object> properties(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("properties requires key/value pairs");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.put((String) keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> numberSchema(String description) {
        return Map.of("type", "number", "description", description);
    }
}
