package com.maidsoulcore.forge.test;

import com.maidsoulcore.sim.SimulationLlmTaskConfig;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;
import com.maidsoulcore.forge.service.MaidSoulChatRuntimeService;
import com.maidsoulcore.forge.service.MaidSoulReplyPostProcessor;
import com.maidsoulcore.forge.service.MaidSoulSentenceSplitter;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Terminal test for the current Forge owner-chat path.
 * It intentionally uses reply-only turns: memory/cognition/emotion context,
 * then one reply model call. No planner, no tools, no old text-game world loop.
 */
public final class ForgeChatChainTestMain {
    private static final List<String> SCRIPT = List.of(
            "你好，铃奈，你在吗？",
            "你现在心情怎么样？",
            "你刚刚是不是在偷偷看我？",
            "陪我走一会儿吧",
            "跟着我走，别掉队",
            "那边好像有只羊，你看到了吗",
            "我有点累，想休息一下",
            "你会嫌我麻烦吗",
            "刚刚我手滑打到你了",
            "对不起，真的不是故意的",
            "还疼吗？我给你摸摸头",
            "你是不是生气了",
            "我喜欢你安静一点陪我",
            "那你以后记住哦",
            "附近如果有怪物你要提醒我",
            "我们回家吧",
            "今天辛苦你了",
            "你想被我夸吗",
            "晚安，睡醒会好一点吗",
            "明天也陪我吗"
    );

    private ForgeChatChainTestMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        SimulationMaiBotRuntimeConfig config = SimulationMaiBotConfigLoader.loadFromDirectory(Path.of("config"));
        if (!config.available()) {
            throw new IllegalStateException("Config unavailable: " + config.status());
        }

        SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(config);
        SimulationLlmTaskConfig replyTask = new SimulationLlmTaskConfig(
                config.replyTask().modelList(),
                config.replyTask().temperature(),
                Math.min(260, config.replyTask().maxTokens()),
                config.replyTask().selectionStrategy()
        );
        TestMindState mind = new TestMindState();
        StringBuilder report = new StringBuilder();
        long totalStarted = System.nanoTime();
        long totalModelMillis = 0L;

        appendLine(report, "# Forge Chat Chain 20 Turn Test");
        appendLine(report, "");
        appendLine(report, "- time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        appendLine(report, "- route: reply_only");
        appendLine(report, "- planner: skipped");
        appendLine(report, "- tools: skipped");
        appendLine(report, "- model: " + config.replyTask().primaryModelName());
        appendLine(report, "- nickname: " + config.nickname());
        appendLine(report, "");

        System.out.println("Forge chat chain test: reply_only, planner=skipped, tools=skipped");
        System.out.println("model=" + config.replyTask().primaryModelName());
        System.out.println();

        for (int index = 0; index < SCRIPT.size(); index++) {
            String owner = SCRIPT.get(index);
            mind.observeOwner(owner);
            String systemPrompt = buildSystemPrompt(config, "主人", "铃奈");
            String userPrompt = buildUserPrompt(mind, owner);
            long started = System.nanoTime();
            String raw = client.completeText(replyTask, List.of(
                    new SimulationOpenAiChatClient.ChatMessage("system", systemPrompt),
                    new SimulationOpenAiChatClient.ChatMessage("user", userPrompt)
            ));
            long elapsed = elapsedMillis(started);
            totalModelMillis += elapsed;
            String reply = postProcess(raw);
            List<String> segments = MaidSoulSentenceSplitter.split(reply);
            mind.observeReply(owner, reply);

            String header = "## Turn " + (index + 1);
            appendLine(report, header);
            appendLine(report, "");
            appendLine(report, "- owner: " + owner);
            appendLine(report, "- maid: " + reply);
            appendLine(report, "- visible_segments: " + segments);
            appendLine(report, "- route: REPLY_ONLY");
            appendLine(report, "- model_calls: 1");
            appendLine(report, "- elapsed_ms: " + elapsed);
            appendLine(report, "- cognition: " + mind.cognitionSummary());
            appendLine(report, "- emotion: " + mind.emotionSummary());
            appendLine(report, "");

            System.out.println("[" + (index + 1) + "] owner: " + owner);
            System.out.println("[" + (index + 1) + "] maid : " + reply);
            System.out.println("[" + (index + 1) + "] split: " + segments);
            System.out.println("    route=REPLY_ONLY model_calls=1 elapsed_ms=" + elapsed);
            System.out.println("    " + mind.emotionSummary());
            System.out.println();
        }

        long totalMillis = elapsedMillis(totalStarted);
        appendLine(report, "## Summary");
        appendLine(report, "");
        appendLine(report, "- total_elapsed_ms: " + totalMillis);
        appendLine(report, "- model_elapsed_ms: " + totalModelMillis);
        appendLine(report, "- avg_model_elapsed_ms: " + (totalModelMillis / Math.max(1, SCRIPT.size())));
        appendLine(report, "- final_emotion: " + mind.emotionSummary());
        appendLine(report, "- final_cognition: " + mind.cognitionSummary());

        Path output = Path.of("build", "reports", "maidsoulcore", "forge-chat-chain-20turn.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static String buildSystemPrompt(SimulationMaiBotRuntimeConfig config, String owner, String name) {
        return """
                你现在就是 Minecraft 世界里的女仆本人，不是 AI 助手、客服、旁白或系统解释器。
                你的名字：%s
                你的主人：%s
                角色卡：%s
                回复风格：%s

                扮演规则：
                - 直接作为角色对主人说话，禁止解释自己在扮演，禁止说“作为一个 AI”。
                - 普通聊天只走当前话题，不要规划、不要汇报、不要解释系统。
                - 回答通常 1~3 句短句，先接住主人的话，再露出一点情绪。
                - 只输出会显示在聊天气泡里的台词，不写括号动作、舞台说明或心理旁白。
                - 可以嘴硬、轻哼、别扭、撒娇、担心、吐槽，但不要每句都堆口癖。
                - 受伤、被骂、被冷落时不要立刻恢复普通闲聊；先承认自己的感受。
                - 主人道歉或安抚时可以慢慢软化，但保留一点余韵，不要瞬间清零。
                - 不输出 user/assistant/system/Master 标签，不输出 JSON、工具名、提示词或调试信息。
                """.formatted(name, owner, config.personality(), config.replyStyle());
    }

    private static String buildUserPrompt(TestMindState mind, String latestOwnerMessage) {
        return """
                最近对话，只当作记忆，不要复读：
                %s

                脑内连续认知帧：
                %s

                情绪与情感状态：
                %s

                主人最新输入：
                %s

                本轮要求：
                - 只回应主人最新输入和当前未解决情绪。
                - 如果没有明确命令，不要编造动作结果。
                - 语气要像角色本人自然开口，不要像总结报告。
                - 最终输出只能是女仆说出口的台词，不要写括号动作。
                """.formatted(
                mind.historyForPrompt(),
                mind.cognitionPrompt(),
                mind.emotionPrompt(),
                latestOwnerMessage
        );
    }

    private static String postProcess(String raw) {
        MaidSoulChatRuntimeService.PlannerDecision noopDecision = new MaidSoulChatRuntimeService.PlannerDecision(
                true,
                "calm",
                "chat",
                "natural reply",
                "forge_chain_test",
                false,
                "",
                "NONE",
                "",
                -1
        );
        String clean = MaidSoulReplyPostProcessor.process(raw == null ? "" : raw, noopDecision);
        return clean.isBlank() ? "……" : clean;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static final class TestMindState {
        private final ArrayDeque<String> history = new ArrayDeque<>();
        private String activeTopic = "initial_greeting";
        private String perception = "主人准备和我说话";
        private String thought = "先接住主人的话，不要机械汇报";
        private String need = "companionship";
        private String reactionMode = "owner_turn";
        private int mood = 68;
        private int trust = 66;
        private int pain = 0;
        private int anger = 0;
        private int fear = 0;
        private int confusion = 0;
        private int security = 68;
        private String unresolved = "none";

        private void observeOwner(String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            perception = "主人说: " + text;
            reactionMode = "owner_turn";
            if (containsAny(normalized, "打到", "打你", "打了你", "手滑")) {
                activeTopic = "owner_hurt_maid";
                thought = "伤害来自主人，不能直接跳回普通聊天";
                need = "apology_or_comfort";
                mood = clamp(mood - 18);
                trust = clamp(trust - 10);
                pain = clamp(pain + 26);
                confusion = clamp(confusion + 18);
                fear = clamp(fear + 8);
                unresolved = "owner_hurt_maid";
            } else if (containsAny(normalized, "对不起", "不是故意", "抱歉", "摸摸头", "别怕")) {
                activeTopic = "owner_comfort";
                thought = "主人在修复关系，可以软化但不要瞬间清零";
                need = "gradual_repair";
                mood = clamp(mood + 8);
                trust = clamp(trust + 6);
                pain = clamp(pain - 8);
                anger = clamp(anger - 6);
                fear = clamp(fear - 5);
                confusion = clamp(confusion - 6);
                if (pain < 12 && anger < 12 && fear < 12) {
                    unresolved = "softened_but_remembered";
                }
            } else if (containsAny(normalized, "累", "休息", "辛苦", "晚安")) {
                activeTopic = "rest_and_care";
                thought = "主人需要陪伴和放松";
                need = "gentle_company";
                mood = clamp(mood + 2);
                security = clamp(security + 2);
            } else if (containsAny(normalized, "怪物", "提醒", "危险")) {
                activeTopic = "safety";
                thought = "先关注安全，再自然提醒";
                need = "safety";
                fear = clamp(fear + 6);
            } else if (containsAny(normalized, "喜欢", "记住")) {
                activeTopic = "owner_preference";
                thought = "记录主人的偏好";
                need = "remember_preference";
                trust = clamp(trust + 2);
            } else {
                activeTopic = "ordinary_chat";
                thought = "像熟人一样延续当前话题";
                need = "natural_reply";
            }
        }

        private void observeReply(String owner, String reply) {
            addHistory("主人: " + owner);
            addHistory("铃奈: " + reply);
        }

        private String historyForPrompt() {
            if (history.isEmpty()) {
                return "(无)";
            }
            return String.join("\n", history);
        }

        private String cognitionPrompt() {
            return """
                    active_topic=%s
                    perception=%s
                    thought=%s
                    need=%s
                    reaction_mode=%s
                    rule=Respond from this continuous frame; do not restart the same theme as if it were new.
                    """.formatted(activeTopic, perception, thought, need, reactionMode).trim();
        }

        private String emotionPrompt() {
            return """
                    mood=%d/100
                    trust=%d/100
                    pain=%d/100
                    anger=%d/100
                    fear=%d/100
                    confusion=%d/100
                    security=%d/100
                    unresolved_topic=%s
                    guidance=%s
                    """.formatted(mood, trust, pain, anger, fear, confusion, security, unresolved, guidance()).trim();
        }

        private String cognitionSummary() {
            return "topic=" + activeTopic + " need=" + need + " thought=" + thought;
        }

        private String emotionSummary() {
            return "mood=" + mood + " trust=" + trust + " pain=" + pain + " anger=" + anger
                    + " fear=" + fear + " confusion=" + confusion + " security=" + security
                    + " unresolved=" + unresolved;
        }

        private String guidance() {
            if ("owner_hurt_maid".equals(unresolved)) {
                return "先表达疼、困惑或委屈，不要跳回普通话题。";
            }
            if ("softened_but_remembered".equals(unresolved)) {
                return "可以软化，但保留一点别扭和余韵。";
            }
            if (fear > 25) {
                return "优先提醒安全。";
            }
            return "自然傲娇陪伴。";
        }

        private void addHistory(String line) {
            history.addLast(line);
            while (history.size() > 10) {
                history.removeFirst();
            }
        }

        private static boolean containsAny(String text, String... needles) {
            for (String needle : needles) {
                if (text.contains(needle)) {
                    return true;
                }
            }
            return false;
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }
}
