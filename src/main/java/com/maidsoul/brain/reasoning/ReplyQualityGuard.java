package com.maidsoul.brain.reasoning;

import java.util.List;

/**
 * 回复质量守门器。
 *
 * <p>这里不负责“写台词”，只负责在台词发给用户之前拦住明显坏味道：
 * 内部文本泄漏、括号动作、口癖堆叠、连续复用用户刚投诉过的表达方式。
 * 这属于生成链路的质量关，不是在人设 prompt 里继续堆临时补丁。</p>
 */
public final class ReplyQualityGuard {
    private static final List<String> WATCHED_CATCHPHRASES = List.of(
            "啧", "哼", "本狐", "嗷呜", "笨蛋主人", "才不是"
    );

    public QualityResult inspect(String reply, String context) {
        String text = reply == null ? "" : reply.trim();
        if (text.isBlank()) {
            return QualityResult.bad("回复为空，需要重新生成一条真实可见回复。");
        }
        if (text.startsWith("{") || text.startsWith("[") || text.contains("target_message_id")) {
            return QualityResult.bad("回复像内部工具或结构化文本，不能直接展示。");
        }
        if (startsWithAction(text)) {
            return QualityResult.bad("回复以动作描写开头，需要改成自然聊天文本。");
        }
        if (containsBracketAction(text)) {
            return QualityResult.bad("回复包含动作描写，需要改成自然聊天文本。");
        }
        String catchphraseProblem = catchphraseProblem(text, context == null ? "" : context);
        if (!catchphraseProblem.isBlank()) {
            return QualityResult.bad(catchphraseProblem);
        }
        String repairProblem = repairProblem(text, context == null ? "" : context);
        if (!repairProblem.isBlank()) {
            return QualityResult.bad(repairProblem);
        }
        return QualityResult.pass();
    }

    private static boolean startsWithAction(String text) {
        String value = text == null ? "" : text.trim();
        return containsAny(value, "（小声", "（别过脸", "（低头", "（抬头", "（眨", "（笑", "（凑近", "（靠近", "（拍拍")
                || value.startsWith("(");
    }

    private static boolean containsBracketAction(String text) {
        String value = text == null ? "" : text;
        return containsAny(value,
                "（小声", "（别过脸", "（低头", "（抬头", "（眨", "（笑",
                "（凑近", "（靠近", "（抱", "（摸", "（拍拍", "（贴", "（蹭"
        );
    }

    private static String catchphraseProblem(String reply, String context) {
        for (String catchphrase : WATCHED_CATCHPHRASES) {
            int replyCount = count(reply, catchphrase);
            if (replyCount >= 2) {
                return "同一回复里口癖“" + catchphrase + "”出现过多，需要换成更自然的表达。";
            }
            if (replyCount >= 1 && recentlyRepeated(context, catchphrase)) {
                return "最近上下文已经出现过口癖“" + catchphrase + "”，本轮需要避开重复。";
            }
        }
        return "";
    }

    private static String repairProblem(String reply, String context) {
        if (context.contains("模式=关系修复") && !containsAny(reply, "没接好", "对不起", "抱歉", "我会", "我先", "认真听", "不会糊弄", "收敛", "别生气")) {
            return "关系修复场景需要先承认没接住或软下来，不能只把问题推回给用户。";
        }
        if (context.contains("模式=冲突冷却") && containsAny(reply, "你自己之前", "还不是你", "谁让你", "下次说我")) {
            return "冲突冷却场景不能翻旧账或继续顶回去，需要先接住道歉。";
        }
        return "";
    }

    private static boolean containsAny(String text, String... needles) {
        String value = text == null ? "" : text;
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean recentlyRepeated(String context, String catchphrase) {
        if (context == null || context.isBlank()) {
            return false;
        }
        String[] lines = context.split("\\R");
        int checked = 0;
        for (int i = lines.length - 1; i >= 0 && checked < 8; i--) {
            String line = lines[i];
            if (line.contains(catchphrase)) {
                return true;
            }
            checked++;
        }
        return false;
    }

    private static int count(String text, String needle) {
        if (text == null || needle == null || needle.isBlank()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int index = text.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
    }

    public record QualityResult(boolean ok, String reason) {
        static QualityResult pass() {
            return new QualityResult(true, "");
        }

        static QualityResult bad(String reason) {
            return new QualityResult(false, reason == null ? "" : reason);
        }
    }
}
