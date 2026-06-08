package com.yunchen.maidsoulcore.core.affect;

import java.util.List;

public record ReplyStyleGuide(
        String tone,
        String emotion,
        String repairMode,
        String intimacyLevel,
        List<String> topicBias,
        List<String> avoid,
        List<String> must,
        String reason
) {
    public String toPromptBlock() {
        return """
                reply_style:
                  tone=%s
                  emotion=%s
                  repair_mode=%s
                  intimacy_level=%s
                  topic_bias=%s
                  avoid=%s
                  must=%s
                  reason=%s
                """.formatted(
                tone,
                emotion,
                repairMode,
                intimacyLevel,
                String.join(",", topicBias),
                String.join(",", avoid),
                String.join(",", must),
                reason
        ).trim();
    }
}
