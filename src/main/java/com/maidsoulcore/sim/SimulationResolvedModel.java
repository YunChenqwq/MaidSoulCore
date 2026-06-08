package com.maidsoulcore.sim;

/**
 * 已经从任务槽位解析出的模型与提供商。
 */
public record SimulationResolvedModel(
        SimulationLlmTaskConfig taskConfig,
        SimulationLlmModelConfig modelConfig,
        SimulationLlmProviderConfig providerConfig
) {
}
