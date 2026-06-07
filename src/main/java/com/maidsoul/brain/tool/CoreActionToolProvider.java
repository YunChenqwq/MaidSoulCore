package com.maidsoul.brain.tool;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.reasoning.EnvironmentObservationTool;
import com.maidsoul.brain.reasoning.ViewObservationTool;

import java.util.List;
import java.util.Map;

/**
 * MaidSoulCore 主 action loop 的内置工具提供者。
 *
 * <p>它只持有“工具如何执行”的知识；ReasoningEngine 只负责循环和回复生成。
 * 这样后续 Forge 行为工具、GUI 调试工具、记忆管理工具都能以 provider 形式挂进来。</p>
 */
public final class CoreActionToolProvider implements ToolProvider {
    private final BrainConfig config;
    private final MemoryRuntime memoryRuntime;
    private final ViewObservationTool viewObservationTool;
    private final EnvironmentObservationTool environmentObservationTool;
    private final ToolRegistry registry;
    private final boolean includeTimingTools;

    public CoreActionToolProvider(
            BrainConfig config,
            MemoryRuntime memoryRuntime,
            ViewObservationTool viewObservationTool,
            EnvironmentObservationTool environmentObservationTool,
            ToolRegistry registry,
            boolean includeTimingTools
    ) {
        this.config = config;
        this.memoryRuntime = memoryRuntime;
        this.viewObservationTool = viewObservationTool == null ? ViewObservationTool.NONE : viewObservationTool;
        this.environmentObservationTool = environmentObservationTool == null ? EnvironmentObservationTool.NONE : environmentObservationTool;
        this.registry = registry;
        this.includeTimingTools = includeTimingTools;
    }

    @Override
    public String providerName() {
        return "maidsoul_core";
    }

    @Override
    public List<ToolSpec> listTools(ToolAvailabilityContext context) {
        return BuiltinToolSet.plannerTools(includeTimingTools, viewObservationTool.available(), environmentObservationTool.available()).stream()
                .filter(tool -> config.memory().queryMemoryToolEnabled() || !"query_memory".equals(tool.name()))
                .toList();
    }

    @Override
    public ToolExecutionResult invoke(ToolInvocation invocation, ToolExecutionContext context) {
        return switch (invocation.toolName()) {
            case "query_memory" -> queryMemory(invocation);
            case "scan_environment" -> scanEnvironment(invocation);
            case "observe_view" -> observeView(invocation, context);
            case "tool_search" -> toolSearch(invocation);
            case "wait" -> pauseTool(invocation, "Planner 选择等待。", Map.of("pause_execution", true));
            case "no_action" -> pauseTool(invocation, "Planner 选择本轮不发言，等待新的外部消息。", Map.of("pause_execution", true));
            case "finish" -> pauseTool(invocation, "Planner 结束本轮内部思考。", Map.of("pause_execution", true));
            case "reply" -> pauseTool(invocation, "Planner 选择进入可见回复。", Map.of("pause_execution", true));
            default -> new ToolExecutionResult(invocation.toolName(), false, "", "未知内置工具：" + invocation.toolName(), null, List.of(), Map.of());
        };
    }

    private ToolExecutionResult toolSearch(ToolInvocation invocation) {
        String query = stringArg(invocation.arguments(), "query", "");
        int limit = intArg(invocation.arguments(), "limit", 5);
        if (query.isBlank()) {
            return new ToolExecutionResult(invocation.toolName(), false, "", "tool_search 需要提供非空 query。", null, List.of(), Map.of());
        }
        if (registry == null) {
            return new ToolExecutionResult(invocation.toolName(), false, "", "工具注册表未接入。", null, List.of(), Map.of());
        }
        List<ToolSpec> matched = registry.searchDeferredTools(query, limit);
        List<String> matchedNames = matched.stream().map(ToolSpec::name).toList();
        List<String> newlyDiscovered = registry.discoverDeferredTools(matchedNames);
        if (matchedNames.isEmpty()) {
            return new ToolExecutionResult(
                    invocation.toolName(),
                    true,
                    "未找到匹配的 deferred tools，请尝试更完整的工具名、前缀或其他关键词。",
                    "",
                    Map.of("query", query, "matched_tool_names", matchedNames, "newly_discovered_tool_names", newlyDiscovered),
                    List.of(),
                    Map.of()
            );
        }
        StringBuilder builder = new StringBuilder("已找到 ")
                .append(matchedNames.size())
                .append(" 个 deferred tools，它们会在后续轮次中加入可用工具列表：");
        for (String name : matchedNames) {
            builder.append('\n')
                    .append("- ")
                    .append(name)
                    .append(newlyDiscovered.contains(name) ? "（本次新发现）" : "（此前已发现）");
        }
        return new ToolExecutionResult(
                invocation.toolName(),
                true,
                builder.toString(),
                "",
                Map.of("query", query, "matched_tool_names", matchedNames, "newly_discovered_tool_names", newlyDiscovered),
                List.of(),
                Map.of()
        );
    }

    private ToolExecutionResult queryMemory(ToolInvocation invocation) {
        String query = stringArg(invocation.arguments(), "query", "");
        if (query.isBlank()) {
            query = stringArg(invocation.arguments(), "reason", invocation.reasoning());
        }
        String content;
        if (memoryRuntime == null) {
            content = "[记忆检索] 记忆运行时未接入。";
        } else {
            content = memoryRuntime.queryMemory(query, config.memory().retrievalLimit());
        }
        return new ToolExecutionResult(
                invocation.toolName(),
                true,
                content,
                "",
                Map.of("query", query),
                List.of(),
                Map.of()
        );
    }

    private ToolExecutionResult scanEnvironment(ToolInvocation invocation) {
        if (!environmentObservationTool.available()) {
            return new ToolExecutionResult(invocation.toolName(), false, "", "[现场扫描] Minecraft 结构化现场工具未接入。", null, List.of(), Map.of());
        }
        String reason = stringArg(invocation.arguments(), "reason", invocation.reasoning());
        try {
            String scan = environmentObservationTool.scan(reason);
            String clean = scan == null ? "" : scan.trim();
            String content = clean.isBlank()
                    ? "[现场扫描] 没有返回有效现场状态。"
                    : clean.startsWith("[现场扫描]") ? clean : "[现场扫描] " + clean;
            return new ToolExecutionResult(
                    invocation.toolName(),
                    true,
                    content,
                    "",
                    Map.of("reason", reason),
                    List.of(),
                    Map.of()
            );
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new ToolExecutionResult(invocation.toolName(), false, "", "[现场扫描] 结构化现场扫描失败：" + message, null, List.of(), Map.of());
        }
    }

    private ToolExecutionResult observeView(ToolInvocation invocation, ToolExecutionContext context) {
        if (!viewObservationTool.available()) {
            return new ToolExecutionResult(invocation.toolName(), false, "", "[视角摘要] 视觉观察工具未接入。", null, List.of(), Map.of());
        }
        String reason = stringArg(invocation.arguments(), "reason", invocation.reasoning());
        long timeoutMillis = Math.max(5000L, context == null ? config.model().plannerTimeoutMillis() : context.timeoutMillis());
        try {
            String summary = viewObservationTool.observe(reason, timeoutMillis);
            String clean = summary == null ? "" : summary.trim();
            String content = clean.isBlank()
                    ? "[视角摘要] 视觉观察没有返回有效内容。"
                    : clean.startsWith("[视角摘要]") ? clean : "[视角摘要] " + clean;
            return new ToolExecutionResult(
                    invocation.toolName(),
                    true,
                    content,
                    "",
                    Map.of("reason", reason),
                    List.of(),
                    Map.of()
            );
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new ToolExecutionResult(invocation.toolName(), false, "", "[视角摘要] 视觉观察失败：" + message, null, List.of(), Map.of());
        }
    }

    private static ToolExecutionResult pauseTool(ToolInvocation invocation, String content, Map<String, Object> metadata) {
        return new ToolExecutionResult(invocation.toolName(), true, content, "", null, List.of(), metadata);
    }

    private static String stringArg(Map<String, Object> args, String key, String fallback) {
        Object value = args == null ? null : args.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? (fallback == null ? "" : fallback.trim()) : text;
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
