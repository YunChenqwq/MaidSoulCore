package com.maidsoul.brain.expression;

import com.maidsoul.brain.message.ChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * replyer 表达方式选择器。
 *
 * <p>复刻 上游参考系统 的边界：表达方式只是 replyer 的参考信息，不改变身份、不强制口癖。
 * 原型机没有子代理选择时，使用轻量相关度排序；候选太少时不注入，避免模板味。</p>
 */
public final class ExpressionSelector {
    private static final int MIN_CANDIDATES = 10;
    private final ExpressionStore store;

    public ExpressionSelector() {
        this(new ExpressionStore());
    }

    public ExpressionSelector(ExpressionStore store) {
        this.store = store == null ? new ExpressionStore() : store;
    }

    public ExpressionSelectionResult selectForReply(
            String sessionId,
            List<ChatMessage> chatHistory,
            ChatMessage replyTarget,
            String replyReason
    ) {
        List<ExpressionCandidate> candidates = store.loadCandidates(sessionId);
        if (candidates.size() < MIN_CANDIDATES) {
            return ExpressionSelectionResult.empty();
        }
        String evidence = buildEvidence(chatHistory, replyTarget, replyReason);
        List<ScoredExpression> scored = new ArrayList<>();
        for (ExpressionCandidate candidate : candidates) {
            if (!candidate.checked()) {
                continue;
            }
            double score = score(candidate, evidence);
            if (score > 0.0) {
                scored.add(new ScoredExpression(candidate, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredExpression::score).reversed());
        List<ExpressionCandidate> selected = scored.stream()
                .limit(3)
                .map(ScoredExpression::candidate)
                .toList();
        if (selected.isEmpty()) {
            return ExpressionSelectionResult.empty();
        }
        return new ExpressionSelectionResult(buildExpressionHabitsBlock(selected), selected.stream().map(ExpressionCandidate::id).toList());
    }

    private static String buildEvidence(List<ChatMessage> chatHistory, ChatMessage replyTarget, String replyReason) {
        StringBuilder builder = new StringBuilder();
        if (chatHistory != null) {
            int start = Math.max(0, chatHistory.size() - 10);
            for (int i = start; i < chatHistory.size(); i++) {
                builder.append(chatHistory.get(i).content()).append('\n');
            }
        }
        if (replyTarget != null) {
            builder.append(replyTarget.content()).append('\n');
        }
        builder.append(replyReason == null ? "" : replyReason);
        return builder.toString();
    }

    private static double score(ExpressionCandidate candidate, String evidence) {
        String text = evidence == null ? "" : evidence;
        double score = Math.log(candidate.count() + 1.0) * 0.2;
        score += overlapScore(candidate.situation(), text);
        score += overlapScore(candidate.style(), text) * 0.35;
        return score;
    }

    private static double overlapScore(String pattern, String text) {
        if (pattern == null || pattern.isBlank() || text == null || text.isBlank()) {
            return 0.0;
        }
        double score = 0.0;
        for (String token : pattern.split("[\\s，。！？,.!?、；;：:]+")) {
            if (token.length() >= 2 && text.contains(token)) {
                score += 1.0;
            }
        }
        return score;
    }

    private static String buildExpressionHabitsBlock(List<ExpressionCandidate> selectedExpressions) {
        StringBuilder builder = new StringBuilder("【表达习惯参考】\n");
        for (ExpressionCandidate expression : selectedExpressions) {
            builder.append("- 当")
                    .append(expression.situation())
                    .append("时，可以自然地用")
                    .append(expression.style())
                    .append("这种表达习惯。\n");
        }
        return builder.toString().trim();
    }

    private record ScoredExpression(ExpressionCandidate candidate, double score) {
    }
}
