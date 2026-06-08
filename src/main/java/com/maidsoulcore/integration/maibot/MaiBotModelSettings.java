package com.maidsoulcore.integration.maibot;

import java.util.List;

/**
 * MaiBot 模型分工配置摘要。
 */
public record MaiBotModelSettings(
        List<String> plannerModels,
        List<String> replyModels,
        List<String> toolModels,
        List<String> vlmModels
) {
    /**
     * 返回空配置，避免上层出现 null 判断。
     */
    public static MaiBotModelSettings empty() {
        return new MaiBotModelSettings(List.of(), List.of(), List.of(), List.of());
    }
}
