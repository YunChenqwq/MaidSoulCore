package com.maidsoul.brain.memory.v2;

import java.util.List;

/**
 * 统一检索结果。
 */
public record MemorySearchResult(
        boolean success,
        String error,
        String mode,
        List<MemorySearchHit> hits
) {
    public String toPromptText(int limit) {
        if (!success) {
            return "长期记忆检索失败：" + error;
        }
        if (hits == null || hits.isEmpty()) {
            return "未找到匹配的长期记忆。";
        }
        StringBuilder builder = new StringBuilder("长期记忆检索结果：\n");
        int count = Math.min(Math.max(1, limit), hits.size());
        for (int i = 0; i < count; i++) {
            builder.append(hits.get(i).render(i + 1)).append('\n');
        }
        return builder.toString().trim();
    }
}
