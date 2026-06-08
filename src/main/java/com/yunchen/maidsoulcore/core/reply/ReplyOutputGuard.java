package com.yunchen.maidsoulcore.core.reply;

import java.util.List;

public final class ReplyOutputGuard {
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "对话状态", "回复信息参考", "真实上下文", "现在请", "JSON", "```", "<message", "</message>",
            "target_message", "reference_info", "只输出", "系统提示", "（", "）", "(", ")"
    );

    public boolean isUsable(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.length() > 220) {
            return false;
        }
        for (String marker : FORBIDDEN_MARKERS) {
            if (normalized.contains(marker)) {
                return false;
            }
        }
        return true;
    }
}
