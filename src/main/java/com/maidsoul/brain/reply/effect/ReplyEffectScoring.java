package com.maidsoul.brain.reply.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * 上游参考系统 回复效果评分规则的 Java 版本。
 *
 * <p>这层只看“用户后续行为”，不参与写台词，也不往 prompt 里追加人设补丁。
 * MaidSoul 自己的特殊口癖投诉等规则应放在上层风险策略里，避免污染 上游参考系统 原始模式。</p>
 */
public final class ReplyEffectScoring {
    public static final List<String> NEGATIVE_PATTERNS = List.of(
            "你没懂", "没懂", "不是这个意思", "不是", "别这样", "好烦", "烦死",
            "算了", "离谱", "无语", "你在说什么", "听不懂", "看不懂", "错了", "不对"
    );
    public static final List<String> REPAIR_PATTERNS = List.of(
            "我是说", "我说的是", "重新说", "再说一遍", "不是问", "你理解错",
            "你搞错", "我问的是", "纠正"
    );
    public static final List<String> POSITIVE_PATTERNS = List.of(
            "谢谢", "感谢", "懂了", "明白了", "可以", "有用", "不错", "好耶", "太好了"
    );

    private ReplyEffectScoring() {
    }

    public static ReplyEffectScores score(List<FollowupMessageSnapshot> followups, String targetUserId) {
        List<FollowupMessageSnapshot> safeFollowups = followups == null ? List.of() : followups;
        BehaviorSignals behaviorSignals = buildBehaviorSignals(safeFollowups, targetUserId);
        FrictionSignals frictionSignals = buildFrictionSignals(safeFollowups, targetUserId);
        double behaviorScore = calculateBehaviorScore(behaviorSignals);
        // 原型机还没有 上游参考系统 的 judge_runner，关系质量先用中性值，避免假装已经有完整 judge。
        double relationalScore = 0.5;
        double frictionScore = calculateFrictionScore(frictionSignals);
        double asi = round(clamp(0.45 * behaviorScore + 0.35 * relationalScore + 0.20 * (1.0 - frictionScore)) * 100.0, 2);
        return new ReplyEffectScores(
                asi,
                round(behaviorScore, 4),
                round(relationalScore, 4),
                round(frictionScore, 4),
                behaviorSignals,
                frictionSignals
        );
    }

    public static boolean hasExplicitNegativeFeedback(List<FollowupMessageSnapshot> followups, String targetUserId, boolean allowIndirect) {
        for (FollowupMessageSnapshot followup : followups == null ? List.<FollowupMessageSnapshot>of() : followups) {
            if (!allowFollowup(followup, targetUserId, allowIndirect)) {
                continue;
            }
            if (containsAny(followup.plainText(), NEGATIVE_PATTERNS)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRepairLoop(List<FollowupMessageSnapshot> followups, String targetUserId, boolean allowIndirect) {
        int repairCount = 0;
        for (FollowupMessageSnapshot followup : followups == null ? List.<FollowupMessageSnapshot>of() : followups) {
            if (!allowFollowup(followup, targetUserId, allowIndirect)) {
                continue;
            }
            if (containsAny(followup.plainText(), REPAIR_PATTERNS)) {
                repairCount++;
            }
        }
        return repairCount >= 1;
    }

    private static BehaviorSignals buildBehaviorSignals(List<FollowupMessageSnapshot> followups, String targetUserId) {
        List<FollowupMessageSnapshot> targetFollowups = targetFollowups(followups, targetUserId);
        List<FollowupMessageSnapshot> evidence = targetFollowups.isEmpty() ? followups : targetFollowups;
        String source = !targetFollowups.isEmpty()
                ? "target_user_feedback"
                : !followups.isEmpty() ? "indirect_session_feedback" : "no_followup";
        if (evidence.isEmpty()) {
            return new BehaviorSignals(0.0, 0.5, 0.0, 1.0, 0.6, source);
        }

        String combinedText = joinPlainText(evidence);
        int negativeCount = countMatches(combinedText, NEGATIVE_PATTERNS);
        int repairCount = countMatches(combinedText, REPAIR_PATTERNS);
        int positiveCount = countMatches(combinedText, POSITIVE_PATTERNS);
        double averageLength = evidence.stream()
                .mapToInt(item -> item.plainText() == null ? 0 : item.plainText().trim().length())
                .average()
                .orElse(0.0);

        return new BehaviorSignals(
                evidence.size() >= 2 ? 1.0 : 0.5,
                estimateSentiment(positiveCount, negativeCount, repairCount),
                clamp((averageLength - 8.0) / 42.0),
                repairCount > 0 ? 0.0 : 1.0,
                negativeCount >= 2 || combinedText.contains("算了") ? 0.0 : 1.0,
                source
        );
    }

    private static FrictionSignals buildFrictionSignals(List<FollowupMessageSnapshot> followups, String targetUserId) {
        double explicitNegative = 0.0;
        double repairLoop = 0.0;
        List<String> evidenceMessages = new ArrayList<>();
        for (FollowupMessageSnapshot followup : followups) {
            double weight = targetUserId != null && !targetUserId.isBlank() && targetUserId.equals(followup.userId()) ? 1.0 : 0.65;
            if (containsAny(followup.plainText(), NEGATIVE_PATTERNS)) {
                explicitNegative = Math.max(explicitNegative, weight);
                evidenceMessages.add(followup.messageId());
            }
            if (containsAny(followup.plainText(), REPAIR_PATTERNS)) {
                repairLoop = Math.max(repairLoop, weight);
                evidenceMessages.add(followup.messageId());
            }
        }
        return new FrictionSignals(round(clamp(explicitNegative), 4), round(clamp(repairLoop), 4), 0.5, evidenceMessages.stream().distinct().sorted().toList());
    }

    private static double calculateBehaviorScore(BehaviorSignals signals) {
        return clamp(
                0.30 * signals.continue2Turns()
                        + 0.25 * signals.nextUserSentiment()
                        + 0.20 * signals.userExpansion()
                        + 0.15 * signals.noCorrection()
                        + 0.10 * signals.noAbort()
        );
    }

    private static double calculateFrictionScore(FrictionSignals signals) {
        return clamp(0.40 * signals.explicitNegative() + 0.30 * signals.repairLoop() + 0.30 * signals.uncannyRisk());
    }

    private static double estimateSentiment(int positiveCount, int negativeCount, int repairCount) {
        return round(clamp(0.5 + 0.2 * positiveCount - 0.25 * negativeCount - 0.15 * repairCount), 4);
    }

    private static List<FollowupMessageSnapshot> targetFollowups(List<FollowupMessageSnapshot> followups, String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            return List.of();
        }
        return followups.stream().filter(item -> targetUserId.equals(item.userId())).toList();
    }

    private static boolean allowFollowup(FollowupMessageSnapshot followup, String targetUserId, boolean allowIndirect) {
        return allowIndirect || targetUserId == null || targetUserId.isBlank() || targetUserId.equals(followup.userId());
    }

    private static String joinPlainText(List<FollowupMessageSnapshot> followups) {
        StringBuilder builder = new StringBuilder();
        for (FollowupMessageSnapshot followup : followups) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(followup.plainText() == null ? "" : followup.plainText());
        }
        return builder.toString();
    }

    private static int countMatches(String text, List<String> patterns) {
        int count = 0;
        String value = text == null ? "" : text;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && value.contains(pattern)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsAny(String text, List<String> patterns) {
        String value = text == null ? "" : text;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
