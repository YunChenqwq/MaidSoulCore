package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.ContextCategory;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * TLM Agent 协议桥接服务。
 * <p>
 * 这个类不改动 MaidSoulCore 现有的 planner / reply / companion 主流程，
 * 只负责把已经注册到 TLM 的上下文分类与工具摘要，重新整理成可直接注入提示词的共享文本。
 * 这样可以保证：
 * 1. TLM 原生 tool/query_game_context 和 MaidSoulCore 自己的提示词看到的是同一套能力定义；
 * 2. 后续继续迁移到更原生的 tool loop 时，不需要再重复维护另一套文案；
 * 3. 现阶段就能减少 PromptService 里手写状态块和工具说明的漂移风险。
 */
public final class MaidSoulTlmBridgeService {
    private MaidSoulTlmBridgeService() {
    }

    /**
     * 构造 planner 阶段使用的共享桥接文本。
     */
    public static String buildPlannerBridgeBlock(EntityMaid maid) {
        StringJoiner joiner = new StringJoiner("\n\n");
        String promptContexts = buildPromptContextBlock(maid);
        if (!promptContexts.isBlank()) {
            joiner.add("Shared registered prompt contexts:\n" + promptContexts);
        }
        String toolCategories = buildToolCategoryBlock(maid);
        if (!toolCategories.isBlank()) {
            joiner.add("Available query_game_context categories:\n" + toolCategories);
        }
        String tools = buildToolSummaryBlock(maid);
        if (!tools.isBlank()) {
            joiner.add("Registered callable tools:\n" + tools);
        }
        return joiner.toString();
    }

    /**
     * 构造 reply 阶段使用的共享桥接文本。
     * <p>
     * reply 阶段不需要把全部工具说明塞得太重，因此只保留 prompt context 和最近可查询的分类摘要。
     */
    public static String buildReplyBridgeBlock(EntityMaid maid) {
        StringJoiner joiner = new StringJoiner("\n\n");
        String promptContexts = buildPromptContextBlock(maid);
        if (!promptContexts.isBlank()) {
            joiner.add("Shared registered prompt contexts:\n" + promptContexts);
        }
        String toolCategories = buildToolCategoryBlock(maid);
        if (!toolCategories.isBlank()) {
            joiner.add("Queryable live categories:\n" + toolCategories);
        }
        return joiner.toString();
    }

    private static String buildPromptContextBlock(EntityMaid maid) {
        List<ContextCategory> categories = GameContextRegister.allPromptCategories();
        if (categories.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (ContextCategory category : categories) {
            List<String> lines = GameContextRegister.getContext(category.id(), maid);
            if (lines.isEmpty()) {
                continue;
            }
            joiner.add("[" + category.id() + "] " + category.summary());
            lines.forEach(joiner::add);
        }
        return joiner.toString();
    }

    private static String buildToolCategoryBlock(EntityMaid maid) {
        List<ContextCategory> categories = GameContextRegister.allToolCategories();
        if (categories.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (ContextCategory category : categories) {
            List<String> lines = GameContextRegister.getContext(category.id(), maid);
            String firstLine = lines.isEmpty() ? "(currently empty)" : lines.get(0);
            joiner.add("- " + category.id() + ": " + category.summary() + " | sample=" + firstLine);
        }
        return joiner.toString();
    }

    private static String buildToolSummaryBlock(EntityMaid maid) {
        Map<String, ITool<?>> tools = ToolRegister.getAllTools();
        if (tools.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<String, ITool<?>> entry : tools.entrySet()) {
            ITool<?> tool = entry.getValue();
            if (tool == null) {
                continue;
            }
            joiner.add("- " + entry.getKey() + ": " + tool.summary(maid));
        }
        return joiner.toString();
    }
}
