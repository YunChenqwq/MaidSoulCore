package com.maidsoulcore.forge.service;

import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.plan.MaidSoulPlanPriority;
import com.maidsoulcore.forge.plan.MaidSoulPlanStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地命令到执行计划的构建服务。
 * <p>
 * 这里对齐 MaiBot r-dev 的多动作规划结果思路：
 * 命令解析阶段先输出“动作列表”，再统一压成 MaidSoul 本地计划。
 */
public final class MaidSoulPlanBuilderService {
    private MaidSoulPlanBuilderService() {
    }

    /**
     * 把本地解析出的动作列表压成一条计划。
     */
    public static MaidSoulPlan buildOwnerCommandPlan(String objective, List<MaidSoulPlanStep> steps) {
        return new MaidSoulPlan(
                "local_command_parser",
                objective,
                MaidSoulPlanPriority.OWNER_COMMAND,
                new ArrayList<>(steps == null ? List.of() : steps)
        );
    }
}
