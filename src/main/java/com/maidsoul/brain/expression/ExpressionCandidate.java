package com.maidsoul.brain.expression;

/**
 * 一条可选表达方式。
 */
public record ExpressionCandidate(
        int id,
        String sessionId,
        String situation,
        String style,
        int count,
        boolean checked
) {
    public ExpressionCandidate {
        sessionId = sessionId == null ? "" : sessionId.trim();
        situation = situation == null ? "" : situation.trim();
        style = style == null ? "" : style.trim();
        count = Math.max(1, count);
    }
}
