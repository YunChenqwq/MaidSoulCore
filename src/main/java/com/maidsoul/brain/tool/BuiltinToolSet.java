package com.maidsoul.brain.tool;

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
        if (includeTimingTools) {
            return List.of(reply(), waitTool(), noAction(), finish(), queryMemory());
        }
        return List.of(reply(), finish(), queryMemory());
    }

    public static List<ToolSpec> timingTools() {
        return List.of(continueTool(), waitTool(), noAction());
    }

    public static ToolSpec reply() {
        return new ToolSpec(
                "reply",
                "当现在应该对用户发出一条真正可见的回复时调用。调用后由回复器生成可见发言。",
                objectSchema(Map.of(
                        "target_message_id", stringSchema("要回应的消息 id；没有明确目标时留空。"),
                        "reason", stringSchema("为什么现在应该回复，以及回复方向；只写一句短方向，不超过40个中文字符，不要写长篇分析。"),
                        "reference_info", stringSchema("可选参考信息，只给回复器看，不会直接展示；不要编造现场事实。"),
                        "affect_event_kind", stringSchema("可选结构化情绪事件：OWNER_APOLOGY/OWNER_ATTACK/OWNER_DISTRESS/OWNER_AFFECTION/OWNER_QUESTION/OWNER_SHORT_FEEDBACK；无明确事件留空。"),
                        "affect_event_intensity", numberSchema("可选情绪事件强度，0-100；普通轻微=25，中等=50，强烈=75。"),
                        "affect_event_note", stringSchema("可选事件依据，用一句短话概括，不要复述长聊天。")
                ), List.of("reason")),
                Map.of("stage", "action")
        );
    }

    public static ToolSpec waitTool() {
        return new ToolSpec(
                "wait",
                "等待一段时间后再判断。用于用户可能还没说完，或者当前更适合把发言权交还给用户。",
                objectSchema(Map.of(
                        "seconds", numberSchema("等待秒数。"),
                        "reason", stringSchema("为什么等待。"),
                        "affect_event_kind", stringSchema("可选结构化情绪事件；无明确事件留空。"),
                        "affect_event_intensity", numberSchema("可选情绪事件强度，0-100。"),
                        "affect_event_note", stringSchema("可选事件依据，用一句短话概括。")
                ), List.of()),
                Map.of("stage", "timing")
        );
    }

    public static ToolSpec noAction() {
        return new ToolSpec(
                "no_action",
                "本轮不发言，等待新的外部消息。",
                objectSchema(Map.of(
                        "reason", stringSchema("为什么本轮不发言。"),
                        "affect_event_kind", stringSchema("可选结构化情绪事件；无明确事件留空。"),
                        "affect_event_intensity", numberSchema("可选情绪事件强度，0-100。"),
                        "affect_event_note", stringSchema("可选事件依据，用一句短话概括。")
                ), List.of()),
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

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> numberSchema(String description) {
        return Map.of("type", "number", "description", description);
    }
}
