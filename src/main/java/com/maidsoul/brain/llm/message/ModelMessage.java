package com.maidsoul.brain.llm.message;

import com.maidsoul.brain.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一模型消息。
 *
 * <p>它保留 tool call、tool result 所需字段，后续工具式 Planner 不能再退化成纯文本 JSON。</p>
 */
public record ModelMessage(
        RoleType role,
        List<MessagePart> parts,
        String toolCallId,
        String toolName,
        List<ToolCall> toolCalls
) {
    public ModelMessage {
        parts = List.copyOf(parts == null ? List.of() : parts);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (parts.isEmpty() && !(role == RoleType.ASSISTANT && !toolCalls.isEmpty())) {
            throw new IllegalArgumentException("模型消息内容不能为空");
        }
        if (role == RoleType.TOOL && (toolCallId == null || toolCallId.isBlank())) {
            throw new IllegalArgumentException("Tool 消息必须带 toolCallId");
        }
        if (toolName != null && !toolName.isBlank() && role != RoleType.TOOL) {
            throw new IllegalArgumentException("只有 Tool 消息可以设置 toolName");
        }
    }

    public String textContent() {
        StringBuilder builder = new StringBuilder();
        for (MessagePart part : parts) {
            if (part instanceof TextMessagePart text) {
                builder.append(text.text());
            }
        }
        return builder.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RoleType role = RoleType.USER;
        private final List<MessagePart> parts = new ArrayList<>();
        private String toolCallId = "";
        private String toolName = "";
        private final List<ToolCall> toolCalls = new ArrayList<>();

        public Builder role(RoleType role) {
            this.role = role == null ? RoleType.USER : role;
            return this;
        }

        public Builder text(String text) {
            parts.add(new TextMessagePart(text));
            return this;
        }

        public Builder image(String format, String base64) {
            parts.add(new ImageMessagePart(format, base64));
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId == null ? "" : toolCallId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName == null ? "" : toolName;
            return this;
        }

        public Builder toolCall(ToolCall call) {
            if (call != null) {
                toolCalls.add(call);
            }
            return this;
        }

        public ModelMessage build() {
            return new ModelMessage(role, parts, toolCallId, toolName, toolCalls);
        }
    }
}

