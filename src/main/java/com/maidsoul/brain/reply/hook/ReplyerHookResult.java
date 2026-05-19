package com.maidsoul.brain.reply.hook;

/**
 * replyer.after_response hook 输出。
 */
public record ReplyerHookResult(
        String response,
        boolean retry,
        String retryReason,
        String matchedRegex,
        String matchedRegexPattern,
        String matchedRegexDescription
) {
    public static ReplyerHookResult keep() {
        return new ReplyerHookResult("", false, "", "", "", "");
    }

    public static ReplyerHookResult rewrite(String response) {
        return new ReplyerHookResult(response, false, "", "", "", "");
    }

    public static ReplyerHookResult retry(String retryReason, String matchedRegex, String matchedRegexPattern, String matchedRegexDescription) {
        return new ReplyerHookResult("", true, safe(retryReason), safe(matchedRegex), safe(matchedRegexPattern), safe(matchedRegexDescription));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
