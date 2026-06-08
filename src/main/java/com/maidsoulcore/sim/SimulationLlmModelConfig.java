package com.maidsoulcore.sim;

import java.util.Map;

/**
 * 单个模型定义。
 */
public record SimulationLlmModelConfig(
        String name,
        String modelIdentifier,
        String apiProvider,
        Map<String, Object> extraParams,
        boolean forceStreamMode
) {
}
