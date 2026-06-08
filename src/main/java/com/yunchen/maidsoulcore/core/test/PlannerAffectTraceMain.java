package com.yunchen.maidsoulcore.core.test;

import com.yunchen.maidsoulcore.core.affect.AffectProfileStore;
import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.config.DialogueModelConfig;
import com.yunchen.maidsoulcore.core.llm.OpenAiCompatibleClient;
import com.yunchen.maidsoulcore.core.memory.LifeMemoryStore;
import com.yunchen.maidsoulcore.core.prompt.PromptCatalog;
import com.yunchen.maidsoulcore.core.reasoning.PlannerRunner;
import com.yunchen.maidsoulcore.core.reasoning.TimingGateRunner;
import com.yunchen.maidsoulcore.core.reply.ReplyGenerator;
import com.yunchen.maidsoulcore.core.runtime.MaidSoulRuntime;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实 planner 情绪链路 trace。
 *
 * <p>这个 main 不给情绪系统手动塞 affection/apology/fight 等结构事件，
 * 只像真实玩家一样发送自然语言。情绪事件必须由 PlannerRunner 调 LLM 后
 * 输出 affect_event，再由 MaidSoulRuntime 写入 AffectEngine。</p>
 */
public final class PlannerAffectTraceMain {
    private static final Path DEFAULT_MC_CONFIG = Path.of(
            "C:/Users/Administrator/Desktop/.minecraft/versions/1.20.1-Forge_47.4.18/config/maidsoulcore/dialogue-config.json"
    );
    private static final Path DEFAULT_MC_LLM_PROPERTIES = Path.of(
            "C:/Users/Administrator/Desktop/.minecraft/versions/1.20.1-Forge_47.4.18/config/maidsoulcore/model/llm.properties"
    );
    private static final List<String> SCRIPT = List.of(
            "你好呀，我回来了。",
            "今天想让你陪我走一会儿。",
            "我喜欢你温柔一点、粘人一点的样子。",
            "你还记得我第一次把灵魂核心交给你的时候吗？",
            "今天下雨了，我突然有点想你。",
            "刚才我没有回你，不是故意晾着你。",
            "过来一点，我想抱抱你。",
            "如果附近有危险，你要第一时间提醒我。",
            "没事，我在，你不用怕。",
            "刚刚那句话我说重了。",
            "对不起，我不该凶你。",
            "你要是还委屈，可以直接告诉我。",
            "我还是最喜欢你了。",
            "晚上也可以主动找我说话，不用一直等我开口。",
            "我现在有点累，想安静待一会儿。",
            "你坐近一点陪我就好。",
            "如果我去新的世界，也会把你一起带上。",
            "这件事你要记住，因为你对我很重要。",
            "今天辛苦你了。",
            "明天也陪我，好吗？"
    );
    private static final List<String> QUICK_SCRIPT = List.of(
            "你好呀，我回来了。",
            "刚才我对你发火了，说话很重。",
            "对不起，我刚才不该凶你。",
            "你要是还委屈，可以直接告诉我。",
            "我现在有点累，想安静待一会儿。",
            "明天也陪我，好吗？"
    );

    private PlannerAffectTraceMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        DialogueCoreConfig config = loadConfig(args);
        List<String> script = useQuickScript(args) ? QUICK_SCRIPT : SCRIPT;
        ModelPair models = loadRuntimeModels(config);
        if (config.model == null || config.model.apiKey == null || config.model.apiKey.isBlank()) {
            throw new IllegalStateException("No API key found in dialogue config.");
        }
        config.enableIndependentTimingGate = false;
        config.messageDebounceMillis = 10L;
        config.maxInternalRounds = 3;
        config.replyRetryCount = 0;
        config.llmTimeoutMillis = Math.max(config.llmTimeoutMillis, 90_000L);

        Path root = Path.of("build", "tmp", "planner-affect-trace", DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
        Path maidDir = root.resolve("memory").resolve(UUID.randomUUID().toString());
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts"));
        OpenAiCompatibleClient plannerLlm = new OpenAiCompatibleClient(models.planner());
        OpenAiCompatibleClient replyerLlm = new OpenAiCompatibleClient(models.replyer());
        LinkedBlockingQueue<String> replies = new LinkedBlockingQueue<>();
        List<String> traceLines = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger currentTurn = new AtomicInteger(0);

        MaidSoulRuntime runtime = new MaidSoulRuntime(
                config,
                prompts,
                new TimingGateRunner(config, prompts, plannerLlm),
                new PlannerRunner(config, prompts, plannerLlm),
                new ReplyGenerator(config, prompts, replyerLlm),
                new LifeMemoryStore(maidDir.resolve("life.json")),
                new AffectProfileStore(maidDir.resolve("affect.json")),
                reply -> {
                    String line = "turn=" + currentTurn.get() + " output " + reply;
                    traceLines.add(line);
                    replies.offer(reply);
                },
                (stage, detail) -> traceLines.add("turn=" + currentTurn.get() + " " + stage + " " + detail)
        );

        StringBuilder report = new StringBuilder();
        append(report, "# Planner Affect Trace Report");
        append(report, "");
        append(report, "- time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        append(report, "- planner_model: " + models.planner().model);
        append(report, "- replyer_model: " + models.replyer().model);
        append(report, "- base_url: " + config.model.baseUrl);
        append(report, "- timing_gate: disabled for trace isolation");
        append(report, "- event_source: planner structured event");
        append(report, "");

        try {
            for (int i = 0; i < script.size(); i++) {
                currentTurn.set(i + 1);
                String owner = script.get(i);
                System.out.println("[" + (i + 1) + "] owner: " + owner);
                runtime.receiveOwnerMessage(config.ownerName, owner);
                String firstReply = replies.poll(120, TimeUnit.SECONDS);
                List<String> turnReplies = new ArrayList<>();
                if (firstReply == null) {
                    String plannedSilence = plannedSilenceLabel(traceLines, i + 1);
                    turnReplies.add(plannedSilence);
                    System.out.println("[" + (i + 1) + "] maid : " + plannedSilence);
                } else {
                    turnReplies.add(firstReply);
                    Thread.sleep(900L);
                    replies.drainTo(turnReplies);
                    System.out.println("[" + (i + 1) + "] maid : " + String.join(" / ", turnReplies));
                }

                append(report, "## Turn " + (i + 1));
                append(report, "");
                append(report, "- owner: " + owner);
                append(report, "- maid: " + String.join(" / ", turnReplies));
                append(report, "- affect_snapshot:");
                append(report, codeBlock(runtime.affectProfile().brief()));
                append(report, "- trace:");
                append(report, codeBlock(extractTurnTrace(traceLines, i + 1)));
                append(report, "");
            }
        } finally {
            runtime.close();
        }

        append(report, "## Final Affect");
        append(report, "");
        append(report, codeBlock(runtime.affectProfile().brief()));

        Path output = Path.of("build", "reports", "maidsoulcore", "planner-affect-trace.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static DialogueCoreConfig loadConfig(String[] args) {
        for (String arg : args) {
            if (arg != null && !arg.isBlank() && !arg.startsWith("--")) {
                return DialogueConfigLoader.loadOrCreate(Path.of(arg));
            }
        }
        if (Files.exists(DEFAULT_MC_CONFIG)) {
            return DialogueConfigLoader.loadOrCreate(DEFAULT_MC_CONFIG);
        }
        return DialogueConfigLoader.loadOrCreate(Path.of("config", "maidsoulcore", "dialogue-config.json"));
    }

    private static boolean useQuickScript(String[] args) {
        for (String arg : args) {
            if ("--quick".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static ModelPair loadRuntimeModels(DialogueCoreConfig config) throws Exception {
        if (config.model == null) {
            config.model = new DialogueModelConfig();
        }
        if (!Files.exists(DEFAULT_MC_LLM_PROPERTIES)) {
            DialogueModelConfig shared = copyModel(config.model);
            return new ModelPair(shared, copyModel(shared));
        }

        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(DEFAULT_MC_LLM_PROPERTIES, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String apiKey = properties.getProperty("apiKey", "").trim();
        String baseUrl = properties.getProperty("baseUrl", "").trim();
        String plannerModel = properties.getProperty("plannerModel", "").trim();
        String replyerModel = properties.getProperty("replyerModel", "").trim();

        if (!apiKey.isBlank()) {
            config.model.apiKey = apiKey;
        }
        if (!baseUrl.isBlank()) {
            config.model.baseUrl = baseUrl;
        }

        DialogueModelConfig planner = copyModel(config.model);
        DialogueModelConfig replyer = copyModel(config.model);
        if (!plannerModel.isBlank()) {
            planner.model = plannerModel;
            config.model.model = plannerModel;
        }
        if (!replyerModel.isBlank()) {
            replyer.model = replyerModel;
        }
        return new ModelPair(planner, replyer);
    }

    private static DialogueModelConfig copyModel(DialogueModelConfig source) {
        DialogueModelConfig target = new DialogueModelConfig();
        target.baseUrl = source.baseUrl;
        target.apiKey = source.apiKey;
        target.model = source.model;
        target.temperature = source.temperature;
        target.maxTokens = source.maxTokens;
        return target;
    }

    private static String extractTurnTrace(List<String> traceLines, int turn) {
        String prefix = "turn=" + turn + " ";
        List<String> selected = new ArrayList<>();
        synchronized (traceLines) {
            for (String line : traceLines) {
                if (line.startsWith(prefix)) {
                    selected.add(line.substring(prefix.length()));
                }
            }
        }
        return selected.isEmpty() ? "(none)" : String.join("\n", selected);
    }

    private static String plannedSilenceLabel(List<String> traceLines, int turn) {
        String trace = extractTurnTrace(traceLines, turn);
        if (trace.contains("planner.no_action")) {
            return "(planned no_action: planner chose silence)";
        }
        if (trace.contains("runtime.wait")) {
            return "(planned wait: planner entered wait state)";
        }
        return "(timeout: no reply in 120s)";
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String codeBlock(String text) {
        return "```text\n" + text + "\n```";
    }

    private record ModelPair(DialogueModelConfig planner, DialogueModelConfig replyer) {
    }
}
