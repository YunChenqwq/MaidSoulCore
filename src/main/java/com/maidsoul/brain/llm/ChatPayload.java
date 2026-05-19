package com.maidsoul.brain.llm;

public record ChatPayload(String role, String content) {
    public static ChatPayload system(String content) {
        return new ChatPayload("system", content);
    }

    public static ChatPayload user(String content) {
        return new ChatPayload("user", content);
    }

    public static ChatPayload assistant(String content) {
        return new ChatPayload("assistant", content);
    }

    public String brief() {
        String text = content == null ? "" : content.replace('\n', ' ').trim();
        if (text.length() > 80) {
            text = text.substring(0, 80) + "...";
        }
        return role + ":" + text;
    }
}
