package com.yunchen.maidsoulcore.core.message;

import java.time.Instant;
import java.util.UUID;

public final class RuntimeMessage {
    private final String id;
    private final DialogueRole role;
    private final String speaker;
    private final String content;
    private final Instant time;
    private final String sourceKind;
    private final boolean countInContext;

    public RuntimeMessage(String id, DialogueRole role, String speaker, String content, Instant time, String sourceKind, boolean countInContext) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.role = role;
        this.speaker = speaker == null ? "" : speaker;
        this.content = content == null ? "" : content;
        this.time = time == null ? Instant.now() : time;
        this.sourceKind = sourceKind == null ? "chat" : sourceKind;
        this.countInContext = countInContext;
    }

    public static RuntimeMessage user(String speaker, String content) {
        return new RuntimeMessage(UUID.randomUUID().toString(), DialogueRole.USER, speaker, content, Instant.now(), "user", true);
    }

    public static RuntimeMessage assistant(String speaker, String content) {
        return new RuntimeMessage(UUID.randomUUID().toString(), DialogueRole.ASSISTANT, speaker, content, Instant.now(), "guided_reply", true);
    }

    public static RuntimeMessage system(String sourceKind, String content) {
        return new RuntimeMessage(UUID.randomUUID().toString(), DialogueRole.SYSTEM, "system", content, Instant.now(), sourceKind, false);
    }

    public static RuntimeMessage thought(String content) {
        return new RuntimeMessage(UUID.randomUUID().toString(), DialogueRole.THOUGHT, "planner", content, Instant.now(), "planner_thought", false);
    }

    public String id() { return id; }
    public DialogueRole role() { return role; }
    public String speaker() { return speaker; }
    public String content() { return content; }
    public Instant time() { return time; }
    public String sourceKind() { return sourceKind; }
    public boolean countInContext() { return countInContext; }
}
