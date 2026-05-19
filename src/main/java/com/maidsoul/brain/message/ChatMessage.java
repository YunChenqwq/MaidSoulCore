package com.maidsoul.brain.message;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        String id,
        MessageRole role,
        String speaker,
        String content,
        Instant timestamp,
        boolean countInContext
) {
    public static ChatMessage user(String speaker, String content) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, speaker, content, Instant.now(), true);
    }

    public static ChatMessage assistant(String speaker, String content) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.ASSISTANT, speaker, content, Instant.now(), true);
    }

    public static ChatMessage internal(String content) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.INTERNAL, "system", content, Instant.now(), false);
    }

    public static ChatMessage reference(String content) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.INTERNAL, "system", content, Instant.now(), true);
    }
}
