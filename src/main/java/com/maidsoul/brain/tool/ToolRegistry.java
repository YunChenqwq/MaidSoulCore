package com.maidsoul.brain.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一工具注册表。
 *
 * <p>按 provider 注册顺序收集工具，同名工具保留先注册者。这样主循环可以稳定地
 * list/invoke，而不会把 query_memory、observe_view 等工具写死在 ReasoningEngine。</p>
 */
public final class ToolRegistry {
    private final List<ToolProvider> providers = new ArrayList<>();
    private final Map<String, ToolSpec> deferredToolSpecsByName = new LinkedHashMap<>();
    private final java.util.Set<String> discoveredDeferredToolNames = new java.util.LinkedHashSet<>();

    public synchronized void registerProvider(ToolProvider provider) {
        if (provider == null) {
            return;
        }
        providers.removeIf(existing -> existing.providerName().equals(provider.providerName()));
        providers.add(provider);
    }

    public synchronized List<ToolSpec> listTools(ToolAvailabilityContext context) {
        Map<String, ToolSpec> specs = new LinkedHashMap<>();
        for (ToolProvider provider : providers) {
            for (ToolSpec spec : provider.listTools(context)) {
                if (!spec.enabled()) {
                    continue;
                }
                specs.putIfAbsent(spec.name(), spec);
            }
        }
        return List.copyOf(specs.values());
    }

    public synchronized void updateDeferredToolSpecs(List<ToolSpec> specs) {
        deferredToolSpecsByName.clear();
        if (specs == null) {
            return;
        }
        for (ToolSpec spec : specs) {
            if (spec != null && spec.enabled()) {
                deferredToolSpecsByName.put(spec.name(), spec);
            }
        }
        discoveredDeferredToolNames.removeIf(name -> !deferredToolSpecsByName.containsKey(name));
    }

    public synchronized List<ToolSpec> searchDeferredTools(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.toLowerCase().replace('_', ' ').replace('-', ' ').trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        List<String> terms = java.util.Arrays.stream(normalizedQuery.split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
        List<ScoredTool> scored = new ArrayList<>();
        for (ToolSpec spec : deferredToolSpecsByName.values()) {
            String lowerName = spec.name().toLowerCase();
            String searchableName = lowerName.replace('_', ' ').replace('-', ' ');
            String lowerDescription = spec.description().toLowerCase();
            int score = 0;
            if (normalizedQuery.equals(searchableName) || normalizedQuery.equals(lowerName)) score += 1000;
            if (searchableName.startsWith(normalizedQuery) || lowerName.startsWith(normalizedQuery)) score += 300;
            if (searchableName.contains(normalizedQuery) || lowerName.contains(normalizedQuery)) score += 200;
            if (lowerDescription.contains(normalizedQuery)) score += 100;
            for (String term : terms) {
                if (searchableName.contains(term) || lowerName.contains(term)) score += 25;
                if (lowerDescription.contains(term)) score += 10;
            }
            if (score > 0) {
                scored.add(new ScoredTool(score, spec));
            }
        }
        scored.sort((left, right) -> {
            int byScore = Integer.compare(right.score(), left.score());
            return byScore != 0 ? byScore : left.spec().name().compareTo(right.spec().name());
        });
        return scored.stream().limit(Math.max(1, limit)).map(ScoredTool::spec).toList();
    }

    public synchronized List<String> discoverDeferredTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        List<String> newlyDiscovered = new ArrayList<>();
        for (String rawName : toolNames) {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isBlank() || !deferredToolSpecsByName.containsKey(name) || discoveredDeferredToolNames.contains(name)) {
                continue;
            }
            discoveredDeferredToolNames.add(name);
            newlyDiscovered.add(name);
        }
        return newlyDiscovered;
    }

    public synchronized List<ToolSpec> discoveredDeferredTools() {
        return discoveredDeferredToolNames.stream()
                .map(deferredToolSpecsByName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public synchronized ToolExecutionResult invoke(ToolInvocation invocation, ToolExecutionContext context) {
        if (invocation == null) {
            return failure("unknown", "工具调用为空。");
        }
        ToolAvailabilityContext availabilityContext = new ToolAvailabilityContext(
                context == null ? "" : context.sessionId(),
                context == null ? "action" : context.stage(),
                context == null ? Map.of() : context.metadata()
        );
        for (ToolProvider provider : providers) {
            boolean owned = provider.listTools(availabilityContext).stream()
                    .anyMatch(spec -> spec.enabled() && spec.name().equals(invocation.toolName()));
            if (!owned) {
                continue;
            }
            try {
                return provider.invoke(invocation, context);
            } catch (RuntimeException e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                return failure(invocation.toolName(), "工具 " + invocation.toolName() + " 调用失败：" + message);
            }
        }
        return failure(invocation.toolName(), "未找到工具：" + invocation.toolName());
    }

    private static ToolExecutionResult failure(String toolName, String message) {
        return new ToolExecutionResult(toolName, false, "", message, null, List.of(), Map.of());
    }

    private record ScoredTool(int score, ToolSpec spec) {
    }
}
