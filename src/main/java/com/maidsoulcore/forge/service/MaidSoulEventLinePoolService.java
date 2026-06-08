package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pre-generated short line pool for standard proactive events.
 */
public final class MaidSoulEventLinePoolService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_PATH = Path.of("config", "maidsoulcore-event-lines.json");
    private static final int MAX_RECENT_LINES = 8;
    private static final int GENERATED_LINE_COUNT = 12;

    private static final ConcurrentMap<String, List<String>> TEMPLATE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Deque<String>> RECENT_LINES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> ROUND_ROBIN = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Boolean> WARMING = new ConcurrentHashMap<>();
    private static final ExecutorService WARMUP_EXECUTOR = Executors.newFixedThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "maidsoulcore-event-line-warmup");
        thread.setDaemon(true);
        return thread;
    });

    private MaidSoulEventLinePoolService() {
    }

    public static String pickLine(EntityMaid maid, String eventType, String detail) {
        ensureConfigLoaded();
        String normalized = normalizeEvent(eventType);
        List<String> lines = TEMPLATE_CACHE.getOrDefault(normalized, List.of());
        if (lines.isEmpty()) {
            return "";
        }
        Deque<String> recent = RECENT_LINES.computeIfAbsent(maid.getUUID(), id -> new ArrayDeque<>());
        int start = Math.floorMod(ROUND_ROBIN.merge(normalized, 1, Integer::sum), lines.size());
        for (int offset = 0; offset < lines.size(); offset++) {
            String candidate = lines.get((start + offset) % lines.size());
            String rendered = renderLine(candidate, maid, detail);
            if (rendered.isBlank() || recent.contains(rendered)) {
                continue;
            }
            recent.addLast(rendered);
            while (recent.size() > MAX_RECENT_LINES) {
                recent.removeFirst();
            }
            ensureWarmupAsync(maid, normalized, detail);
            return rendered;
        }
        String fallback = renderLine(lines.get(start), maid, detail);
        ensureWarmupAsync(maid, normalized, detail);
        return fallback;
    }

    public static void ensureWarmupAsync(EntityMaid maid, String eventType, String detail) {
        String normalized = normalizeEvent(eventType);
        if (!MaidSoulRuntimeRouterService.supportsEventLinePool(normalized)) {
            return;
        }
        List<String> existing = TEMPLATE_CACHE.get(normalized);
        if (existing != null && existing.size() >= GENERATED_LINE_COUNT) {
            return;
        }
        if (WARMING.putIfAbsent(normalized, true) != null) {
            return;
        }
        CompletableFuture.runAsync(() -> warmupFromLlm(maid, normalized, detail), WARMUP_EXECUTOR)
                .whenComplete((unused, throwable) -> WARMING.remove(normalized));
    }

    private static void warmupFromLlm(EntityMaid maid, String eventType, String detail) {
        try {
            SimulationMaiBotRuntimeConfig runtimeConfig = SimulationMaiBotConfigLoader.loadFromDirectory(
                    Path.of(MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get())
            );
            if (!runtimeConfig.available()) {
                return;
            }
            SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(runtimeConfig);
            String system = """
                    You generate short cute maid lines.
                    Output plain text only.
                    Return one line per row, no numbering.
                    Each line <= 20 Chinese characters.
                    Keep tone soft and cute, can include emoticons.
                    """;
            String user = "event=" + eventType + "\n"
                    + "detail=" + (detail == null ? "" : detail) + "\n"
                    + "Generate " + GENERATED_LINE_COUNT + " distinct short lines.";
            String raw = client.completeText(runtimeConfig.replyTask(), system, user);
            List<String> generated = raw.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.replaceAll("^[-*\\d.\\s]+", "").trim())
                    .filter(line -> !line.isBlank())
                    .distinct()
                    .limit(GENERATED_LINE_COUNT)
                    .toList();
            if (generated.isEmpty()) {
                return;
            }
            mergeTemplateLines(eventType, generated);
            flushConfig();
        } catch (Exception ignored) {
        }
    }

    private static void ensureConfigLoaded() {
        if (!TEMPLATE_CACHE.isEmpty()) {
            return;
        }
        synchronized (MaidSoulEventLinePoolService.class) {
            if (!TEMPLATE_CACHE.isEmpty()) {
                return;
            }
            try {
                if (!Files.exists(CONFIG_PATH)) {
                    Files.createDirectories(CONFIG_PATH.getParent());
                    writeDefaultConfig();
                }
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root == null || !root.has("event_line_pool")) {
                    writeDefaultConfig();
                    root = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), JsonObject.class);
                }
                JsonObject pool = root.getAsJsonObject("event_line_pool");
                for (Map.Entry<String, JsonElement> entry : pool.entrySet()) {
                    if (!entry.getValue().isJsonArray()) {
                        continue;
                    }
                    JsonArray array = entry.getValue().getAsJsonArray();
                    ArrayList<String> lines = new ArrayList<>();
                    for (JsonElement element : array) {
                        if (element != null && !element.isJsonNull()) {
                            String line = element.getAsString().trim();
                            if (!line.isBlank()) {
                                lines.add(line);
                            }
                        }
                    }
                    if (!lines.isEmpty()) {
                        TEMPLATE_CACHE.put(normalizeEvent(entry.getKey()), List.copyOf(lines));
                    }
                }
                if (TEMPLATE_CACHE.isEmpty()) {
                    writeDefaultConfig();
                    ensureConfigLoaded();
                }
            } catch (Exception exception) {
                loadDefaultIntoMemory();
            }
        }
    }

    private static void writeDefaultConfig() throws Exception {
        JsonObject root = new JsonObject();
        JsonObject pool = new JsonObject();
        defaultLines().forEach((event, lines) -> {
            JsonArray arr = new JsonArray();
            lines.forEach(arr::add);
            pool.add(event, arr);
        });
        root.add("event_line_pool", pool);
        Files.writeString(CONFIG_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        loadDefaultIntoMemory();
    }

    private static void loadDefaultIntoMemory() {
        TEMPLATE_CACHE.clear();
        defaultLines().forEach((event, lines) -> TEMPLATE_CACHE.put(normalizeEvent(event), List.copyOf(lines)));
    }

    private static Map<String, List<String>> defaultLines() {
        return Map.of(
                "maid.attacked.by_owner", List.of(
                        "主人轻点嘛，我会乖乖的呀(>_<)",
                        "呜，主人打疼我啦…我会听话的",
                        "欸嘿，别生气嘛，我在呢(｡•́︿•̀｡)"
                ),
                "maid.attacked", List.of(
                        "有点疼呀，我会注意躲避的！",
                        "我被打到了，主人要小心哦",
                        "唔，受到攻击了，我还能坚持！"
                ),
                "maid.action.follow", List.of(
                        "收到，我会紧紧跟着主人哒~",
                        "好哒主人，我跟上啦(๑•̀ㅂ•́)و✧",
                        "我在这里，随时跟着你~"
                ),
                "maid.action.sit", List.of(
                        "我坐好啦，等主人吩咐~",
                        "乖乖坐下啦(｡･ω･｡)ﾉ♡",
                        "收到，我先坐着等你哦"
                ),
                "maid.action.schedule", List.of(
                        "日程切换好啦，我会按计划做事~",
                        "明白啦，新的安排我记住了",
                        "好的主人，作息已经更新啦"
                ),
                "maid.action.task", List.of(
                        "任务开始啦，我这就去做！",
                        "收到任务，我会认真完成的~",
                        "好的主人，正在执行任务中"
                ),
                "world.weather.changed", List.of(
                        "天气变了呢，主人要注意保暖呀~",
                        "哇，天气有变化，我会陪着你",
                        "天气切换啦，我会一直在你身边"
                ),
                "world.time_phase.changed", List.of(
                        "时间变啦，我们继续一起行动吧~",
                        "昼夜切换了，主人要注意安全哦",
                        "新的时间段到啦，我会守着主人"
                )
        );
    }

    private static void mergeTemplateLines(String eventType, List<String> generated) {
        String key = normalizeEvent(eventType);
        ArrayList<String> merged = new ArrayList<>(TEMPLATE_CACHE.getOrDefault(key, List.of()));
        merged.addAll(generated);
        List<String> compact = merged.stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length))
                .limit(24)
                .toList();
        TEMPLATE_CACHE.put(key, compact);
    }

    private static void flushConfig() {
        try {
            JsonObject root = new JsonObject();
            JsonObject pool = new JsonObject();
            TEMPLATE_CACHE.forEach((event, lines) -> {
                JsonArray arr = new JsonArray();
                lines.forEach(arr::add);
                pool.add(event, arr);
            });
            root.add("event_line_pool", pool);
            Files.writeString(CONFIG_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String renderLine(String template, EntityMaid maid, String detail) {
        String owner = maid.getOwner() == null ? "主人" : maid.getOwner().getName().getString();
        String text = template
                .replace("{owner}", owner)
                .replace("{maid}", maid.getName().getString())
                .replace("{detail}", detail == null ? "" : detail);
        return text.trim();
    }

    private static String normalizeEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "";
        }
        return eventType.trim().toLowerCase(Locale.ROOT);
    }
}

