package com.yunchen.maidsoulcore.core.context;

import com.yunchen.maidsoulcore.core.message.RuntimeMessage;
import com.yunchen.maidsoulcore.core.runtime.MessageBuffer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ContextBuilder {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public ContextPack build(MessageBuffer buffer, int historyWindow, String relationText, String memoryText) {
        List<RuntimeMessage> history = buffer.latestCounted(historyWindow);
        RuntimeMessage latest = buffer.latestUserMessage();
        StringBuilder builder = new StringBuilder();
        builder.append("真实聊天历史：\n");
        for (RuntimeMessage message : history) {
            builder.append("<message id=\"").append(message.id()).append("\"")
                    .append(" time=\"").append(TIME_FORMATTER.format(message.time())).append("\"")
                    .append(" role=\"").append(message.role().name().toLowerCase()).append("\"")
                    .append(" speaker=\"").append(message.speaker()).append("\"")
                    .append(" source=\"").append(message.sourceKind()).append("\">\n")
                    .append(message.content()).append("\n")
                    .append("</message>\n");
        }
        if (relationText != null && !relationText.isBlank()) {
            builder.append("\n关系和实时状态：\n").append(relationText).append("\n");
        }
        if (memoryText != null && !memoryText.isBlank()) {
            builder.append("\n相关本地记忆证据：\n").append(memoryText).append("\n");
        }
        return new ContextPack(builder.toString().trim(), latest == null ? "" : latest.id());
    }
}
