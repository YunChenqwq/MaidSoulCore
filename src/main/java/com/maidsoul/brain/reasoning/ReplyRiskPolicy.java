package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.message.ChatMessage;

/**
 * 回复风险策略。
 *
 * <p>本地质量守门只在容易翻车的场景启用：关系修复、冲突、辱骂、口癖投诉、主动追话。
 * 普通低风险聊天走快速路径，避免把所有回复都变成“生成后再洗一遍”的笨重链路。</p>
 */
final class ReplyRiskPolicy {
    boolean shouldInspect(ChatMessage target, String context, String replyReason, String referenceInfo) {
        String text = (target == null ? "" : target.content()) + "\n"
                + (context == null ? "" : context) + "\n"
                + (replyReason == null ? "" : replyReason) + "\n"
                + (referenceInfo == null ? "" : referenceInfo);
        return containsAny(text,
                "模式=关系修复", "模式=冲突冷却", "模式=女仆受伤", "模式=用户低落",
                "生气", "不可爱", "冷淡", "不理我", "无语", "真服了", "谁家女仆",
                "难过", "伤心", "呜呜", "想哭", "委屈", "崩溃", "撑不住",
                "哼", "啧", "口癖", "乱编", "没根据", "傻逼", "废物",
                "[现场观察]", "主动", "沉默", "topic_push", "light_followup"
        );
    }

    private static boolean containsAny(String text, String... needles) {
        String value = text == null ? "" : text;
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
