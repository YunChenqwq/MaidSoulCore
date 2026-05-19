package com.maidsoul.brain;

import com.maidsoul.brain.expression.ExpressionSelectionResult;
import com.maidsoul.brain.expression.ExpressionSelector;
import com.maidsoul.brain.expression.ExpressionStore;
import com.maidsoul.brain.message.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 表达方式选择器测试。
 */
public final class ExpressionSelectorSmokeTest {
    private ExpressionSelectorSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempFile("maidsoul-expressions", ".jsonl");
        StringBuilder jsonl = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            String situation = i == 7 ? "用户难过" : "普通聊天" + i;
            String style = i == 7 ? "先轻轻安慰再问原因" : "自然接话" + i;
            jsonl.append("{\"id\":").append(i)
                    .append(",\"session_id\":\"prototype-session\"")
                    .append(",\"situation\":\"").append(situation).append("\"")
                    .append(",\"style\":\"").append(style).append("\"")
                    .append(",\"count\":").append(i)
                    .append(",\"checked\":true}\n");
        }
        Files.writeString(temp, jsonl.toString(), StandardCharsets.UTF_8);

        ExpressionSelector selector = new ExpressionSelector(new ExpressionStore(temp));
        ExpressionSelectionResult result = selector.selectForReply(
                "prototype-session",
                List.of(),
                ChatMessage.user("用户", "我今天有点难过"),
                "接住用户难过情绪"
        );
        if (!result.expressionHabits().contains("用户难过") || !result.selectedExpressionIds().contains(7)) {
            throw new IllegalStateException("表达选择器没有选中匹配当前语境的表达方式: " + result);
        }
        Files.deleteIfExists(temp);
        System.out.println("EXPRESSION_SELECTOR_SMOKE_OK");
    }
}
