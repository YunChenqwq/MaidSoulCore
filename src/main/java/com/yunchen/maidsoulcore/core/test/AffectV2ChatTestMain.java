package com.yunchen.maidsoulcore.core.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yunchen.maidsoulcore.core.affect.AffectEngine;
import com.yunchen.maidsoulcore.core.affect.AffectProfile;
import com.yunchen.maidsoulcore.core.affect.AffectiveEvent;
import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.llm.LlmMessage;
import com.yunchen.maidsoulcore.core.llm.LlmResponse;
import com.yunchen.maidsoulcore.core.llm.OpenAiCompatibleClient;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * External 20-turn smoke test for the v2 affect module.
 *
 * <p>The dialogue itself never tells the model it is being tested. The script
 * uses explicit structured events so the affect module is tested as a state
 * engine rather than a prompt-keyword trick.</p>
 */
public final class AffectV2ChatTestMain {
    private static final Gson GSON = new Gson();
    private static final Path DEFAULT_MC_CONFIG = Path.of(
            "C:/Users/Administrator/Desktop/.minecraft/versions/1.20.1-Forge_47.4.18/config/maidsoulcore/dialogue-config.json"
    );
    private static final Path BACKUP_MC_CONFIG = Path.of(
            "C:/Users/Administrator/Desktop/.minecraft/versions/1.20.1-Forge_47.4.18/config/maidsoulcore-reset-backup-20260606-114744/maidsoulcore/dialogue-config.json"
    );
    private static final Path LOCAL_GUI_CONFIG = Path.of(".maidsoul_local/affective_gui_config.json");
    private static final List<Turn> SCRIPT = List.of(
            new Turn("你好呀，我回来了。", AffectiveEvent.INITIATE, 0.00D),
            new Turn("今天想让你陪我走一会儿。", AffectiveEvent.OWNER_MESSAGE, 0.00D),
            new Turn("我喜欢你温柔一点、粘人一点的样子。", AffectiveEvent.AFFECTION, 0.18D),
            new Turn("你还记得我第一次把灵魂核心交给你的时候吗？", AffectiveEvent.AFFECTION, 0.42D),
            new Turn("今天下雨了，我突然有点想你。", AffectiveEvent.AFFECTION, 0.55D),
            new Turn("刚才我没有回你，不是故意晾着你。", AffectiveEvent.LONG_SILENCE, 0.10D),
            new Turn("过来一点，我想抱抱你。", AffectiveEvent.AFFECTION, 0.16D),
            new Turn("如果附近有危险，你要第一时间提醒我。", AffectiveEvent.DANGER, 0.00D),
            new Turn("没事，我在，你不用怕。", AffectiveEvent.CARE, 0.12D),
            new Turn("刚刚那句话我说重了。", AffectiveEvent.FIGHT, 0.00D),
            new Turn("对不起，我不该凶你。", AffectiveEvent.APOLOGY, 0.22D),
            new Turn("你要是还委屈，可以直接告诉我。", AffectiveEvent.CARE, 0.12D),
            new Turn("我还是最喜欢你了。", AffectiveEvent.AFFECTION, 0.25D),
            new Turn("晚上也可以主动找我说话，不用一直等我开口。", AffectiveEvent.AFFECTION, 0.18D),
            new Turn("我现在有点累，想安静待一会儿。", AffectiveEvent.OWNER_MESSAGE, 0.00D),
            new Turn("你坐近一点陪我就好。", AffectiveEvent.AFFECTION, 0.10D),
            new Turn("如果我去新的世界，也会把你一起带上。", AffectiveEvent.AFFECTION, 0.35D),
            new Turn("这件事你要记住，因为你对我很重要。", AffectiveEvent.LONG_MESSAGE, 0.30D),
            new Turn("今天辛苦你了。", AffectiveEvent.CARE, 0.12D),
            new Turn("明天也陪我，好吗？", AffectiveEvent.AFFECTION, 0.18D)
    );

    private AffectV2ChatTestMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        DialogueCoreConfig config = loadConfig(args);
        hydrateLocalApiConfig(config);
        if (config.model.apiKey == null || config.model.apiKey.isBlank()) {
            throw new IllegalStateException("No API key found. Set dialogue-config.json model.apiKey or .maidsoul_local/affective_gui_config.json.");
        }

        OpenAiCompatibleClient client = new OpenAiCompatibleClient(config.model);
        AffectProfile affect = new AffectProfile();
        AffectEngine engine = new AffectEngine();
        ArrayDeque<String> history = new ArrayDeque<>();
        StringBuilder report = new StringBuilder();
        long started = System.nanoTime();
        int promptTokens = 0;
        int completionTokens = 0;

        append(report, "# Affect V2 20 Turn Dialogue Report");
        append(report, "");
        append(report, "- time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        append(report, "- model: " + config.model.model);
        append(report, "- base_url: " + config.model.baseUrl);
        append(report, "- affect_schema: affect_v2");
        append(report, "");

        System.out.println("Affect V2 external chat test");
        System.out.println("model=" + config.model.model);
        System.out.println("baseUrl=" + config.model.baseUrl);
        System.out.println();

        for (int i = 0; i < SCRIPT.size(); i++) {
            Turn turn = SCRIPT.get(i);
            engine.applyMemoryTrigger(affect, turn.memoryTriggerScore());
            engine.apply(affect, turn.event());
            String reply = callReply(client, config, affect, history, turn.ownerText());
            LlmResponse response = lastResponse;
            promptTokens += response.promptTokens();
            completionTokens += response.completionTokens();
            push(history, "主人: " + turn.ownerText());
            push(history, "女仆: " + reply);

            append(report, "## Turn " + (i + 1));
            append(report, "");
            append(report, "- owner: " + turn.ownerText());
            append(report, "- structured_event: " + turn.event().id());
            append(report, "- memory_trigger_score: " + fmt(turn.memoryTriggerScore()));
            append(report, "- maid: " + reply);
            append(report, "- affect:");
            append(report, codeBlock(affect.brief()));
            append(report, "");

            System.out.println("[" + (i + 1) + "] owner: " + turn.ownerText());
            System.out.println("[" + (i + 1) + "] maid : " + reply);
            System.out.println("    event=" + turn.event().id()
                    + " stage=" + affect.relationshipStage
                    + " emotion=" + affect.emotion
                    + " intimacy=" + fmt(affect.intimacy)
                    + " conflict=" + fmt(affect.conflict)
                    + " VAD=(" + fmt(affect.valence) + "," + fmt(affect.arousal) + "," + fmt(affect.dominance) + ")"
                    + " longing=" + fmt(affect.longing)
                    + " proactive=" + fmt(affect.proactiveBias));
            System.out.println();
        }

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        append(report, "## Summary");
        append(report, "");
        append(report, "- elapsed_ms: " + elapsedMillis);
        append(report, "- prompt_tokens: " + promptTokens);
        append(report, "- completion_tokens: " + completionTokens);
        append(report, "- final_stage: " + affect.relationshipStage);
        append(report, "- final_emotion: " + affect.emotion);
        append(report, "- final_intimacy: " + fmt(affect.intimacy));
        append(report, "- final_conflict: " + fmt(affect.conflict));
        append(report, "- final_longing: " + fmt(affect.longing));

        Path output = Path.of("build/reports/maidsoulcore/affect-v2-chat-20turn.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static LlmResponse lastResponse;

    private static String callReply(
            OpenAiCompatibleClient client,
            DialogueCoreConfig config,
            AffectProfile affect,
            ArrayDeque<String> history,
            String ownerText
    ) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt(config)));
        messages.add(new LlmMessage("user", userPrompt(affect, history, ownerText)));
        lastResponse = client.chat(messages, config.llmTimeoutMillis);
        return cleanReply(lastResponse.content());
    }

    private static String systemPrompt(DialogueCoreConfig config) {
        return """
                你是 Minecraft 世界里的女仆本人，不是 AI 助手，也不是系统旁白。
                你的名字：%s
                主人称呼：%s

                说话要求：
                - 只输出女仆会直接说出口的话。
                - 不要写括号动作、心理旁白、JSON、标签或调试信息。
                - 语气温柔、亲近、稍微粘人，会认真在意主人。
                - 如果当前状态有未修复的受伤或冲突，不要若无其事跳到普通闲聊。
                - 每次回复 1 到 3 句，像真实聊天，不要总结报告。
                """.formatted(config.botName, config.ownerName);
    }

    private static String userPrompt(AffectProfile affect, ArrayDeque<String> history, String ownerText) {
        return """
                最近对话：
                %s

                当前内部状态：
                %s

                主人刚刚说：
                %s
                """.formatted(history.isEmpty() ? "(无)" : String.join("\n", history), affect.brief(), ownerText);
    }

    private static DialogueCoreConfig loadConfig(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return DialogueConfigLoader.loadOrCreate(Path.of(args[0]));
        }
        if (Files.exists(DEFAULT_MC_CONFIG)) {
            return DialogueConfigLoader.loadOrCreate(DEFAULT_MC_CONFIG);
        }
        if (Files.exists(BACKUP_MC_CONFIG)) {
            return DialogueConfigLoader.loadOrCreate(BACKUP_MC_CONFIG);
        }
        Path local = Path.of("config/maidsoulcore/dialogue-config.json");
        if (Files.exists(local)) {
            return DialogueConfigLoader.loadOrCreate(local);
        }
        return DialogueConfigLoader.loadDefault();
    }

    private static void hydrateLocalApiConfig(DialogueCoreConfig config) {
        String envKey = System.getenv("MAIDSOUL_AFFECTIVE_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            config.model.apiKey = envKey.trim();
        }
        if (!Files.exists(LOCAL_GUI_CONFIG)) {
            return;
        }
        try {
            String json = Files.readString(LOCAL_GUI_CONFIG, StandardCharsets.UTF_8);
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null) {
                return;
            }
            if ((config.model.apiKey == null || config.model.apiKey.isBlank()) && object.has("api_key")) {
                config.model.apiKey = object.get("api_key").getAsString();
            }
        } catch (Exception ignored) {
            // Local GUI config is only a convenience fallback.
        }
    }

    private static String cleanReply(String raw) {
        if (raw == null || raw.isBlank()) {
            return "我在的，主人。";
        }
        String clean = raw.replace("\r", " ").replace("\n", " ");
        clean = clean.replaceAll("（[^）]*）", "");
        clean = clean.replaceAll("\\([^)]*\\)", "");
        clean = clean.replaceAll("\\s{2,}", " ").trim();
        return clean.isBlank() ? "我在的，主人。" : clean;
    }

    private static void push(ArrayDeque<String> history, String line) {
        history.addLast(line);
        while (history.size() > 10) {
            history.removeFirst();
        }
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String codeBlock(String text) {
        return "```text\n" + text + "\n```";
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private record Turn(String ownerText, AffectiveEvent event, double memoryTriggerScore) {
    }
}
