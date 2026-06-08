package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;

import java.util.List;
import java.util.Locale;

/**
 * 聊天路由判定服务。
 * <p>
 * 当前阶段不引入额外模型做路由判定，而是先使用稳定、可解释的轻量规则：
 * - 明确命令、任务、姿态、跟随、回家、战斗类输入 -> 走 TLM tool loop
 * - 普通陪伴聊天、观察反馈、闲聊 -> 继续走 MaidSoulCore 轻量拟人链
 * <p>
 * 后续如果需要，再把这里升级为“小模型路由器”也不会影响主结构。
 */
public final class MaidSoulChatRouteService {
    private MaidSoulChatRouteService() {
    }

    /**
     * 判断本轮是否应该走命令型 tool loop。
     */
    public static boolean shouldUseToolLoop(List<LLMMessage> messages) {
        String latest = extractLatestUserMessage(messages);
        if (latest.isBlank()) {
            return false;
        }
        String text = MaidSoulChatSanitizerService.sanitizeLatestUserMessage(latest).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }

        return containsAny(text,
                "跟着我", "跟我", "跟随", "别跟着", "留在这里", "待在这里", "待在家里", "回家", "回去",
                "坐下", "坐着", "起来", "起立", "站起来", "站好",
                "切换日程", "白天工作", "夜间工作", "全天工作", "守夜",
                "切换任务", "做任务", "去干活", "工作模式",
                "攻击", "打", "清掉", "消灭", "保护我", "帮我打", "帮我攻击", "锁定", "那只怪", "那只",
                "去", "帮我"
        ) || looksLikeCommandSentence(text);
    }

    /**
     * 生成路由名，便于 trace 输出。
     */
    public static String routeName(List<LLMMessage> messages) {
        return shouldUseToolLoop(messages) ? "tool_loop" : "human_chat";
    }

    private static boolean looksLikeCommandSentence(String text) {
        return (text.startsWith("你") || text.startsWith("给我") || text.startsWith("帮我"))
                && containsAny(text, "去", "把", "帮", "跟", "坐", "回", "打", "切换", "待");
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null
                    && message.role() == Role.USER
                    && message.message() != null
                    && MaidSoulChatSanitizerService.isRealOwnerMessage(message.message())) {
                return message.message();
            }
        }
        return "";
    }
}
