package com.maidsoul.brain.reasoning;

import java.util.List;

/**
 * 回复后处理清洗器。
 *
 * <p>这里只处理“绝不该直接发给玩家”的格式噪声，例如内部标签、工具文本、动作括号和明显口癖复读。
 * 它不负责重写角色台词，避免把运行时变成越来越厚的提示词补丁。</p>
 */
public final class ReplySanitizer {
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "分析：", "分析:", "记忆：", "记忆:", "回复：", "回复:", "台词：", "台词:",
            "理由：", "理由:", "参考：", "参考:", "输出：", "输出:"
    );

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim()
                .replace("```", "")
                .replace("<message>", "")
                .replace("</message>", "")
                .replace("</think>", "")
                .replace("<think>", "")
                .trim();
        while (text.startsWith("---")) {
            text = text.substring(3).trim();
        }
        boolean changed;
        do {
            changed = false;
            String withoutInlineActions = removeBracketActions(text);
            if (!withoutInlineActions.equals(text)) {
                text = withoutInlineActions.trim();
                changed = true;
            }
            for (String prefix : FORBIDDEN_PREFIXES) {
                if (text.startsWith(prefix)) {
                    text = text.substring(prefix.length()).trim();
                    changed = true;
                }
            }
            String withoutLeadingAction = removeLeadingBracketAction(text);
            if (!withoutLeadingAction.equals(text)) {
                text = withoutLeadingAction.trim();
                changed = true;
            }
        } while (changed);

        if (looksLikeInternalText(text)) {
            return "";
        }
        return text.trim();
    }

    private String removeBracketActions(String text) {
        String value = text == null ? "" : text;
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            boolean open = ch == '（' || ch == '(';
            if (!open) {
                builder.append(ch);
                index++;
                continue;
            }
            char close = ch == '（' ? '）' : ')';
            int end = value.indexOf(close, index + 1);
            if (end < 0 || end - index > 80) {
                builder.append(ch);
                index++;
                continue;
            }
            String inside = value.substring(index + 1, end);
            if (looksLikeAction(inside)) {
                index = end + 1;
                continue;
            }
            builder.append(value, index, end + 1);
            index = end + 1;
        }
        return builder.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private String removeLeadingBracketAction(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return value;
        }
        if ((value.startsWith("（") && value.contains("）")) || (value.startsWith("(") && value.contains(")"))) {
            char endChar = value.startsWith("（") ? '）' : ')';
            int end = value.indexOf(endChar);
            if (end > 0 && end < 80) {
                String inside = value.substring(1, end);
                if (looksLikeAction(inside)) {
                    return value.substring(end + 1).trim();
                }
            }
        }
        return value;
    }

    private boolean looksLikeAction(String inside) {
        String text = inside == null ? "" : inside;
        return text.contains("看")
                || text.contains("笑")
                || text.contains("走")
                || text.contains("眨")
                || text.contains("低头")
                || text.contains("抬头")
                || text.contains("凑近")
                || text.contains("靠近")
                || text.contains("抱")
                || text.contains("摸")
                || text.contains("拍拍")
                || text.contains("贴")
                || text.contains("蹭")
                || text.contains("伸手")
                || text.contains("停顿")
                || text.contains("脸")
                || text.contains("耳")
                || text.contains("手")
                || text.contains("裙")
                || text.contains("小声")
                || text.contains("嘀咕")
                || text.contains("别过脸");
    }

    private boolean looksLikeInternalText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return true;
        }
        return normalized.contains("\"action\"")
                || normalized.contains("target_message_id")
                || normalized.contains("wait_seconds")
                || normalized.startsWith("{")
                || normalized.startsWith("[");
    }
}
