package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.message.ChatMessage;

/**
 * 高风险回复兜底策略。
 *
 * <p>只有模型重试后仍然明显不合格时才会走到这里。它不是常态写作器，
 * 只负责在用户不满、冲突冷却、口癖投诉等场景下给出不继续伤害关系的最低可用回复。</p>
 */
final class RiskFallbackPolicy {
    String fallback(ChatMessage target, String context, String reason) {
        if (context != null && context.contains("模式=关系修复")) {
            return "刚才是我没接好，让你不舒服了。\n我先不把问题推回给你，这次认真听。";
        }
        if (context != null && context.contains("模式=冲突冷却")) {
            return "嗯，我听到了。\n刚才那点别扭先放一边，我们慢慢把话说回来。";
        }
        String content = target == null ? "" : target.content();
        if (reason != null && reason.contains("口癖")) {
            return "好，我知道了，这种说法听着烦我就收起来。\n我会换成更自然的说法，不拿口癖糊弄你。";
        }
        if (content.contains("不爽") || content.contains("生气") || content.contains("无语")) {
            return "我听见了，这次是我没接好。\n你先别急，我会认真把话接回来。";
        }
        if (content.contains("？") || content.contains("?")) {
            return "你这样问，我当然听见了。先别急，把话说清楚一点嘛。";
        }
        return "嗯，我在听。你刚才这句，我没有打算糊弄过去。";
    }
}
