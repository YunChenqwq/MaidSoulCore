package com.maidsoul.brain.llm.message;

import java.util.Set;

public record ImageMessagePart(String imageFormat, String imageBase64) implements MessagePart {
    private static final Set<String> SUPPORTED_FORMATS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    public ImageMessagePart {
        String normalized = imageFormat == null ? "" : imageFormat.toLowerCase();
        if (!SUPPORTED_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("不受支持的图片格式: " + imageFormat);
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("图片 Base64 内容不能为空");
        }
    }

    public String normalizedImageFormat() {
        String lower = imageFormat.toLowerCase();
        return lower.equals("jpg") ? "jpeg" : lower;
    }

    @Override
    public String debugText() {
        return "[image:" + normalizedImageFormat() + "]";
    }
}

