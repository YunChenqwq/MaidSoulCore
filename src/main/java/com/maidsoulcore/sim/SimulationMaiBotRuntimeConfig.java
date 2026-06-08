package com.maidsoulcore.sim;

import java.nio.file.Path;
import java.util.Map;

/**
 * 文字游戏运行期使用的完整 MaiBot 配置快照。
 * <p>
 * 这里比之前的轻量快照更完整，包含：
 * 1. API 提供商；
 * 2. 模型注册表；
 * 3. planner/replyer/tool_use/vlm 任务配置；
 * 4. 人设与回复风格；
 * 5. 视觉提示词。
 */
public record SimulationMaiBotRuntimeConfig(
        Path configDirectory,
        String nickname,
        String personality,
        String replyStyle,
        String planStyle,
        String visualStyle,
        Map<String, SimulationLlmProviderConfig> providers,
        Map<String, SimulationLlmModelConfig> models,
        SimulationLlmTaskConfig plannerTask,
        SimulationLlmTaskConfig replyTask,
        SimulationLlmTaskConfig toolTask,
        SimulationLlmTaskConfig vlmTask,
        boolean available,
        String status
) {
    /**
     * 按模型别名解析任务槽位实际使用的模型。
     */
    public SimulationResolvedModel resolveTask(SimulationLlmTaskConfig taskConfig) {
        if (taskConfig == null || taskConfig.primaryModelName().isBlank()) {
            throw new IllegalStateException("No model configured for task");
        }
        SimulationLlmModelConfig modelConfig = models.get(taskConfig.primaryModelName());
        if (modelConfig == null) {
            throw new IllegalStateException("Model not found: " + taskConfig.primaryModelName());
        }
        SimulationLlmProviderConfig providerConfig = providers.get(modelConfig.apiProvider());
        if (providerConfig == null) {
            throw new IllegalStateException("Provider not found: " + modelConfig.apiProvider());
        }
        return new SimulationResolvedModel(taskConfig, modelConfig, providerConfig);
    }

    /**
     * 返回不可用占位配置。
     */
    public static SimulationMaiBotRuntimeConfig unavailable(Path configDirectory, String status) {
        SimulationLlmTaskConfig emptyTask = new SimulationLlmTaskConfig(java.util.List.of(), 0.3D, 512, "random");
        return new SimulationMaiBotRuntimeConfig(
                configDirectory,
                "女仆",
                "",
                "",
                "",
                "",
                java.util.Map.of(),
                java.util.Map.of(),
                emptyTask,
                emptyTask,
                emptyTask,
                emptyTask,
                false,
                status
        );
    }
}
