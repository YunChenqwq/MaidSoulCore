package com.maidsoulcore.integration.maibot;

import java.nio.file.Path;

/**
 * MaiBot 配置在当前时刻的完整只读快照。
 */
public record MaiBotConfigSnapshot(
        Path configDirectory,
        MaiBotModelSettings modelSettings,
        MaiBotPersonalitySettings personalitySettings,
        boolean available,
        String status
) {
    /**
     * 构造不可用状态的占位快照。
     */
    public static MaiBotConfigSnapshot unavailable(Path configDirectory, String status) {
        return new MaiBotConfigSnapshot(
                configDirectory,
                MaiBotModelSettings.empty(),
                MaiBotPersonalitySettings.empty(),
                false,
                status
        );
    }
}
