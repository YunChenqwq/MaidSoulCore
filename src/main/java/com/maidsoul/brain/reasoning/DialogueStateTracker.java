package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.message.MessageRole;
import com.maidsoul.brain.reply.effect.ReplyEffectTracker;

import java.util.List;

/**
 * 从一批用户输入与最近上下文中提取短期对话状态。
 *
 * <p>这里做的是“状态归纳”，不是生成回复。它让 planner 知道当前是在正常聊天、修复关系、
 * 冲突冷却还是女仆受伤，避免把所有短句都机械判断成“话题收束”。</p>
 */
final class DialogueStateTracker {
    private DialogueState lastState = DialogueState.normal();

    DialogueState update(List<ChatMessage> pendingMessages, List<ChatMessage> recentContext) {
        return update(pendingMessages, recentContext, ReplyEffectTracker.ReplyEffectSummary.neutral());
    }

    DialogueState update(
            List<ChatMessage> pendingMessages,
            List<ChatMessage> recentContext,
            ReplyEffectTracker.ReplyEffectSummary replyEffectSummary
    ) {
        String pendingText = joinUserText(pendingMessages);
        if (pendingText.isBlank()) {
            return lastState;
        }

        DialogueState next;
        if (containsAny(pendingText, "呜呜", "哭", "难过", "伤心", "崩溃", "想哭", "委屈", "难受", "好累", "撑不住")) {
            // 用户低落和用户投诉是两种不同状态：前者需要陪伴，后者需要修复关系。
            // 如果混成 USER_COMPLAINING，回复器容易进入“傲娇修复/抢情绪”姿态。
            next = new DialogueState(DialogueMode.USER_DISTRESSED, "用户正在表达难过、哭泣或委屈；情绪主体是用户。酒狐需要先安抚和陪伴，不能调侃，不能说自己才该哭或把情绪转回自己。");
        } else if (replyEffectSummary != null && replyEffectSummary.repairLoop()) {
            next = new DialogueState(DialogueMode.REPAIR_NEEDED, "上一条回复触发了用户纠正/修复循环，需要先承认理解偏差。");
        } else if (replyEffectSummary != null && replyEffectSummary.explicitNegative()) {
            next = new DialogueState(DialogueMode.USER_COMPLAINING, "上一条回复后出现明确负反馈，需要降低防御并重新接住。");
        } else if (containsAny(pendingText, "傻逼", "废物", "滚", "蠢货")) {
            next = new DialogueState(DialogueMode.MAID_HURT, "用户出现明显攻击性辱骂。");
        } else if (containsAny(pendingText, "生气", "不可爱", "冷淡", "不理我", "无语", "谁家女仆", "真服了", "不爽", "自己看着办", "乱编", "没根据")) {
            next = new DialogueState(DialogueMode.REPAIR_NEEDED, "用户明确表达不满或失望，需要修复关系。");
        } else if (containsAny(pendingText, "对不起", "抱歉", "说重了", "我错了") && recentlyConflict(recentContext)) {
            next = new DialogueState(DialogueMode.COOLDOWN_AFTER_CONFLICT, "用户正在为冲突道歉，应该接住台阶但不要立刻翻旧账。");
        } else if (isColdShortFeedback(pendingText) && recentlyConflict(recentContext)) {
            next = new DialogueState(DialogueMode.USER_COMPLAINING, "冲突后出现短冷反馈，不能当普通收束处理。");
        } else if (pendingMessages.size() >= 3) {
            next = new DialogueState(DialogueMode.WAITING_USER_FINISH, "用户短时间连续输入多条，优先当作同一轮表达。");
        } else if (lastState.mode() == DialogueMode.REPAIR_NEEDED && isSoftClosure(pendingText)) {
            next = new DialogueState(DialogueMode.COOLDOWN_AFTER_CONFLICT, "用户给出简短收束，冲突刚降温。");
        } else {
            next = DialogueState.normal();
        }

        lastState = next;
        return next;
    }

    private static String joinUserText(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message.role() == MessageRole.USER) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(message.content());
            }
        }
        return builder.toString().trim();
    }

    private static boolean recentlyConflict(List<ChatMessage> recentContext) {
        int checked = 0;
        for (int i = recentContext.size() - 1; i >= 0 && checked < 8; i--, checked++) {
            String text = recentContext.get(i).content();
            if (containsAny(text, "不可爱", "冷淡", "不理我", "无语", "生气", "傻逼", "真服了", "谁家女仆")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isColdShortFeedback(String text) {
        String value = text == null ? "" : text.trim();
        return value.equals("。")
                || value.equals(".")
                || value.equals("呵呵")
                || value.equals("行")
                || value.equals("e")
                || value.equals("额")
                || value.equals("哦");
    }

    private static boolean isSoftClosure(String text) {
        String value = text == null ? "" : text.trim();
        return value.equals("行")
                || value.equals("好吧")
                || value.equals("算了")
                || value.equals("嗯");
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
