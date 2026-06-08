package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 本地命令快路径解析器。
 * <p>
 * 当前版本刻意收紧能力边界，只接管“单句、单动作、无歧义”的高确定性指令：
 * - 跟随 / 停留
 * - 坐下 / 起来
 * - 切换日程
 * <p>
 * 以下命令一律不走本地快路径，而是交回 LLM planner：
 * - 任意战斗命令
 * - 含“然后 / 再 / 先…再…”的多步骤命令
 * - 含复合动作、插入动作、条件动作的命令
 */
public final class MaidSoulLocalCommandParserService {
    private MaidSoulLocalCommandParserService() {
    }

    public static Optional<ParsedCommandPlan> parse(EntityMaid maid, List<LLMMessage> messages) {
        String latest = extractLatestUserMessage(messages);
        if (latest.isBlank()) {
            return Optional.empty();
        }
        return parse(maid, latest);
    }

    public static Optional<ParsedCommandPlan> parse(EntityMaid maid, String rawText) {
        if (maid == null) {
            return Optional.empty();
        }
        String normalized = normalize(rawText);
        if (normalized.isBlank() || isReturnHomeIntent(normalized) || shouldFallbackToLlm(normalized)) {
            return Optional.empty();
        }

        ParsedStep parsedStep = parseSimpleClause(normalized);
        if (parsedStep == null) {
            return Optional.empty();
        }
        return Optional.of(new ParsedCommandPlan(
                parsedStep.summary(),
                formatAcknowledgement(parsedStep.summary()),
                List.of(parsedStep.step())
        ));
    }

    private static String formatAcknowledgement(String summary) {
        String template = MaidSoulCommonConfig.LOCAL_COMMAND_ACK_TEMPLATE.get();
        try {
            return template.formatted(summary);
        } catch (RuntimeException exception) {
            return "好的主人，我这就" + summary + "喔~";
        }
    }

    private static ParsedStep parseSimpleClause(String clause) {
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_ON_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("跟随主人", "FOLLOW_ON", "", -1, immediateStepTimeoutTicks()),
                    "跟着主人"
            );
        }
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_OFF_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("停留原地", "FOLLOW_OFF", "", -1, immediateStepTimeoutTicks()),
                    "待在这里"
            );
        }
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_SIT_ON_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("坐下待命", "SIT_ON", "", -1, immediateStepTimeoutTicks()),
                    "坐下待命"
            );
        }
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_SIT_OFF_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("起身待命", "SIT_OFF", "", -1, immediateStepTimeoutTicks()),
                    "起来待命"
            );
        }
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_DAY_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("切换白天日程", "SET_SCHEDULE", "DAY", -1, immediateStepTimeoutTicks()),
                    "切换白天日程"
            );
        }
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_NIGHT_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("切换夜间日程", "SET_SCHEDULE", "NIGHT", -1, immediateStepTimeoutTicks()),
                    "切换夜间日程"
            );
        }
        if (containsAny(clause, MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_ALL_KEYWORDS.get())) {
            return new ParsedStep(
                    new MaidSoulPlanStep("切换全天日程", "SET_SCHEDULE", "ALL", -1, immediateStepTimeoutTicks()),
                    "切换全天日程"
            );
        }
        return null;
    }

    private static int immediateStepTimeoutTicks() {
        return MaidSoulCommonConfig.LOCAL_COMMAND_IMMEDIATE_STEP_TIMEOUT_TICKS.get();
    }

    /**
     * 只要命令复杂、涉及战斗、或者明显是多步骤，就交给 LLM。
     */
    private static boolean shouldFallbackToLlm(String normalized) {
        return containsAny(normalized, MaidSoulCommonConfig.LOCAL_COMMAND_COMPLEX_FALLBACK_KEYWORDS.get());
    }

    private static boolean isReturnHomeIntent(String normalized) {
        return normalized.contains("回家")
                || normalized.contains("回去")
                || normalized.contains("返回家")
                || normalized.contains("回到家");
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null
                    && message.role() == Role.USER
                    && StringUtils.isNotBlank(message.message())
                    && MaidSoulChatSanitizerService.isRealOwnerMessage(message.message())) {
                return message.message();
            }
        }
        return "";
    }

    private static boolean containsAny(String text, List<? extends String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return StringUtils.trimToEmpty(text)
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('！', ' ')
                .replace('？', ' ')
                .replace(',', ' ')
                .replace('.', ' ')
                .replace('!', ' ')
                .replace('?', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private record ParsedStep(MaidSoulPlanStep step, String summary) {
    }

    public record ParsedCommandPlan(String objective, String acknowledgement, List<MaidSoulPlanStep> steps) {
    }
}
