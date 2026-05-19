package com.maidsoul.brain.llm.message;

public record TextMessagePart(String text) implements MessagePart {
    public TextMessagePart {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("文本消息片段不能为空");
        }
    }

    @Override
    public String debugText() {
        return text;
    }
}

