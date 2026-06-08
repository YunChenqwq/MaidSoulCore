package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MaidSoulCore ???????
 * <p>
 * ???????????????????? reply ????????????
 * ??????????????????????????????????
 */
public final class MaidSoulChatSanitizerService {
    /**
     * ????????????????????????????
     */
    private static final List<String> PROMPT_LEAK_FRAGMENTS = List.of(
            "请根据以上所有信息输出你的回复",
            "请根据以上信息回复",
            "严格遵守输出格式",
            "输出格式要求",
            "最近对话：",
            "这是 MaidSoulCore 对当前回合的补充要求",
            "你现在继续扮演女仆本人",
            "玩家最新输入：",
            "recent chat:",
            "reply requirements"
    );
    /**
     * ???????????????????????????
     */
    private static final Pattern ROLE_PREFIX_PATTERN = Pattern.compile(
            "(?im)(^|\\s)(user|assistant|system|developer|master)\\s*[:：]\\s*"
    );
    /**
     * ????????????????????????????????
     */
    private static final Pattern DENSE_ROLE_TRANSCRIPT_PATTERN = Pattern.compile(
            "(?is).*(?:user\\s*[:：]|assistant\\s*[:：]|system\\s*[:：]|master\\s*[:：]).*"
                    + "(?:user\\s*[:：]|assistant\\s*[:：]|system\\s*[:：]|master\\s*[:：]).*"
    );
    private static final Pattern STAGE_DIRECTION_PATTERN = Pattern.compile("[（(]([^（）()]{1,40})[）)]");
    /**
     * ????????????
     */
    private static final Set<Role> DROPPED_ROLES = Set.of(Role.TOOL, Role.SYSTEM, Role.DEVELOPER);

    private MaidSoulChatSanitizerService() {
    }

    /**
     * ??????????????????????????????
     *
     * @param raw ????
     * @return ???? reply ?????????
     */
    public static String sanitizeLatestUserMessage(String raw) {
        String sanitized = normalizeText(stripPromptFragments(stripRolePrefixes(raw)));
        return sanitized;
    }

    /**
     * 判断一段 USER role 文本是否真的是玩家发言。
     *
     * <p>TLM 会把环境、时间、天气等 `<context>` 块也塞成 USER 消息。
     * 这些东西只能作为 reference，绝不能当成“主人最新说的话”，否则女仆会完全答偏。</p>
     */
    public static boolean isRealOwnerMessage(String raw) {
        String text = normalizeText(raw);
        if (text.isBlank()) {
            return false;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("<context>") || lowered.startsWith("<context")) {
            return false;
        }
        if (lowered.contains("- time:") && lowered.contains("- weather:")) {
            return false;
        }
        if (looksLikePromptLeak(text)) {
            return false;
        }
        String sanitized = sanitizeLatestUserMessage(text);
        return !sanitized.isBlank() && !looksLikePromptLeak(sanitized);
    }

    /**
     * ??????????????????????
     * <p>
     * ???????? role transcript????????/???????????
     *
     * @param messages ??????
     * @param limit ???????????
     * @return ??????????????????
     */
    public static List<String> sanitizeHistoryForReply(List<LLMMessage> messages, int limit) {
        if (messages == null || messages.isEmpty() || limit <= 0) {
            return List.of();
        }
        ArrayList<String> sanitized = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message == null || DROPPED_ROLES.contains(message.role())) {
                continue;
            }
            if (message.role() == Role.USER && !isRealOwnerMessage(message.message())) {
                continue;
            }
            String text = normalizeText(stripPromptFragments(stripRolePrefixes(message.message())));
            if (text.isBlank() || looksLikePromptLeak(message.message()) || looksLikePromptLeak(text)) {
                continue;
            }
            String line = switch (message.role()) {
                case USER -> "USER: " + text;
                case ASSISTANT -> "ASSISTANT: " + text;
                default -> text;
            };
            sanitized.add(line);
            if (sanitized.size() >= limit) {
                break;
            }
        }
        Collections.reverse(sanitized);
        return List.copyOf(sanitized);
    }

    /**
     * ?????????????????????????????
     *
     * @param raw ??????
     * @return ?????????????????????
     */
    public static String sanitizeModelOutput(String raw) {
        String sanitized = normalizeText(stripStageDirections(stripPromptFragments(stripRolePrefixes(raw))));
        if (sanitized.isBlank()) {
            return "";
        }
        if (looksLikePromptLeak(sanitized)) {
            List<String> sentences = MaidSoulSentenceSplitter.split(sanitized);
            for (String sentence : sentences) {
                String candidate = normalizeText(stripPromptFragments(stripRolePrefixes(sentence)));
                if (!candidate.isBlank() && !looksLikePromptLeak(candidate)) {
                    return candidate;
                }
            }
            return "";
        }
        return sanitized;
    }

    /**
     * ???????????????????????
     *
     * @param text ?????
     * @return ??????????? true
     */
    public static boolean looksLikePromptLeak(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
        if (DENSE_ROLE_TRANSCRIPT_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        for (String fragment : PROMPT_LEAK_FRAGMENTS) {
            if (normalized.contains(fragment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        int roleHitCount = 0;
        java.util.regex.Matcher matcher = Pattern.compile("(?i)(user|assistant|system|master)\\s*[:：]").matcher(normalized);
        while (matcher.find()) {
            roleHitCount++;
            if (roleHitCount >= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * ?????????????????? transcript ???????
     *
     * @param text ????
     * @return ???????
     */
    public static String stripRolePrefixes(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = text;
        String previous;
        do {
            previous = stripped;
            stripped = ROLE_PREFIX_PATTERN.matcher(stripped).replaceAll("$1");
        } while (!previous.equals(stripped));
        return stripped;
    }

    /**
     * ?????????????????????????
     *
     * @param text ????
     * @return ???????????
     */
    private static String stripPromptFragments(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = text;
        for (String fragment : PROMPT_LEAK_FRAGMENTS) {
            stripped = stripped.replace(fragment, " ");
        }
        return stripped;
    }

    private static String stripStageDirections(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = STAGE_DIRECTION_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            String replacement = looksLikeStageDirection(content) ? " " : matcher.group();
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static boolean looksLikeStageDirection(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String compact = content.replaceAll("\\s+", "");
        if (!compact.matches(".*\\p{IsHan}.*")) {
            return false;
        }
        return containsAny(compact,
                "轻轻", "小声", "低头", "抬头", "歪头", "点头", "摇头", "看去", "看着",
                "拉住", "捂住", "揉了揉", "别过脸", "耳朵", "眼睛", "脸红", "缩了一下",
                "抱着", "埋进", "嘟囔", "叹气", "靠近", "后退", "声音");
    }

    /**
     * ???????????????????????????
     *
     * @param text ????
     * @return ?????????
     */
    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
