package com.maidsoulcore.integration.maibot;

import java.util.List;

/**
 * MaiBot 的人格与语言风格摘要。
 */
public record MaiBotPersonalitySettings(
        String nickname,
        String personality,
        String replyStyle,
        String planStyle,
        List<String> aliasNames
) {
    /**
     * 返回空人格配置。
     */
    public static MaiBotPersonalitySettings empty() {
        return new MaiBotPersonalitySettings("", "", "", "", List.of());
    }
}
