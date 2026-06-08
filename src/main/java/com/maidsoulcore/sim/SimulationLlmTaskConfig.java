package com.maidsoulcore.sim;

import java.util.List;

/**
 * 某个任务槽位对应的模型配置。
 */
public record SimulationLlmTaskConfig(
        List<String> modelList,
        double temperature,
        int maxTokens,
        String selectionStrategy
) {
    /**
     * 返回首选模型名。
     */
    public String primaryModelName() {
        return modelList == null || modelList.isEmpty() ? "" : modelList.get(0);
    }
}
