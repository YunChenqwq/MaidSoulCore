package com.maidsoul.brain.reply.hook;

/**
 * replyer.after_response hook 输入上下文。
 */
public record ReplyerHookContext(
        String response,
        String sessionId,
        String requestType,
        int attempt,
        int retryCount,
        int maxRetries,
        String replyMessageId,
        String replyReason,
        String referenceInfo,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
