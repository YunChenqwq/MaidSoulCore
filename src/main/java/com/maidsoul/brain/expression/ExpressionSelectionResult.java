package com.maidsoul.brain.expression;

import java.util.List;

/**
 * replyer 表达方式选择结果。
 */
public record ExpressionSelectionResult(
        String expressionHabits,
        List<Integer> selectedExpressionIds
) {
    public ExpressionSelectionResult {
        expressionHabits = expressionHabits == null ? "" : expressionHabits.trim();
        selectedExpressionIds = selectedExpressionIds == null ? List.of() : List.copyOf(selectedExpressionIds);
    }

    public static ExpressionSelectionResult empty() {
        return new ExpressionSelectionResult("", List.of());
    }
}
