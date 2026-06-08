package com.maidsoulcore.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MaidSoulCore ???????
 * <p>
 * MaiBot ?????????????????
 * ????????????
 * 1. ??????
 * 2. ???????????
 * 3. ???????????????
 */
public final class MaidSoulReplyPostProcessor {
    /**
     * ??????????????????????
     */
    private static final String SAFE_FALLBACK_REPLY = "I am here, Master.";

    private MaidSoulReplyPostProcessor() {
    }

    /**
     * ???????????????
     *
     * @param rawReply ????
     * @param plannerDecision planner ??
     * @return ????????
     */
    public static String process(String rawReply, MaidSoulChatRuntimeService.PlannerDecision plannerDecision) {
        String sanitized = MaidSoulChatSanitizerService.sanitizeModelOutput(rawReply);
        if (sanitized.isBlank()) {
            return SAFE_FALLBACK_REPLY;
        }

        List<String> sentences = new ArrayList<>(deduplicateSentences(MaidSoulSentenceSplitter.split(sanitized)));
        if (sentences.isEmpty()) {
            return SAFE_FALLBACK_REPLY;
        }
        if (looksLikeHistoryEcho(rawReply, sentences)) {
            String firstNaturalSentence = firstNaturalSentence(sentences);
            if (!firstNaturalSentence.isBlank()) {
                return firstNaturalSentence;
            }
            return SAFE_FALLBACK_REPLY;
        }

        maybeAppendFollowUp(sentences, plannerDecision);
        String finalReply = String.join(" ", sentences).trim();
        return finalReply.isBlank() ? SAFE_FALLBACK_REPLY : finalReply;
    }

    /**
     * ?? planner ????????????????????????
     * ?????????????
     */
    private static void maybeAppendFollowUp(
            List<String> sentences,
            MaidSoulChatRuntimeService.PlannerDecision plannerDecision
    ) {
        if (!plannerDecision.askFollowUp()) {
            return;
        }
        if (plannerDecision.followUpQuestion() == null || plannerDecision.followUpQuestion().isBlank()) {
            return;
        }
        if (sentences.size() >= 2) {
            return;
        }

        String first = sentences.get(0);
        if (first.contains("？") || first.contains("?")) {
            return;
        }

        String normalizedQuestion = MaidSoulChatSanitizerService.sanitizeModelOutput(plannerDecision.followUpQuestion()).trim();
        if (normalizedQuestion.isBlank()) {
            return;
        }
        if (!normalizedQuestion.endsWith("？") && !normalizedQuestion.endsWith("?")) {
            normalizedQuestion = normalizedQuestion + "？";
        }
        sentences.add(normalizedQuestion);
    }

    /**
     * ???????????????????????
     */
    private static boolean looksLikeHistoryEcho(String rawReply, List<String> sentences) {
        if (MaidSoulChatSanitizerService.looksLikePromptLeak(rawReply)) {
            return true;
        }
        if (sentences.size() >= 2 && sentences.get(0).equals(sentences.get(1))) {
            return true;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String sentence : sentences) {
            String compact = sentence.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (!compact.isBlank() && !normalized.add(compact)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ????????????????????????
     */
    private static List<String> deduplicateSentences(List<String> sentences) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        ArrayList<String> result = new ArrayList<>();
        for (String sentence : sentences) {
            String cleaned = MaidSoulChatSanitizerService.sanitizeModelOutput(sentence);
            if (cleaned.isBlank()) {
                continue;
            }
            String key = cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (unique.add(key)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    /**
     * ???????????????????????????
     */
    private static String firstNaturalSentence(List<String> sentences) {
        for (String sentence : sentences) {
            String cleaned = MaidSoulChatSanitizerService.sanitizeModelOutput(sentence);
            if (!cleaned.isBlank() && !MaidSoulChatSanitizerService.looksLikePromptLeak(cleaned)) {
                return cleaned;
            }
        }
        return "";
    }
}
