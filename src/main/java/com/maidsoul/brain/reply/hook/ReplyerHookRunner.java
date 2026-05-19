package com.maidsoul.brain.reply.hook;

import java.util.ArrayList;
import java.util.List;

/**
 * replyer.after_response hook 执行器。
 *
 * <p>它复刻 maibotdev 的语义：hook 可以改写 response，也可以要求 retry；
 * retry 的具体约束会追加到下一次 replyer 的参考信息中。</p>
 */
public final class ReplyerHookRunner {
    private final ReplyerHookRegistry registry;

    public ReplyerHookRunner() {
        this(new ReplyerHookRegistry());
    }

    public ReplyerHookRunner(ReplyerHookRegistry registry) {
        this.registry = registry == null ? new ReplyerHookRegistry() : registry;
    }

    public HookOutcome invoke(ReplyerHookContext context) {
        String response = context == null ? "" : safe(context.response());
        List<HookEvent> events = new ArrayList<>();
        boolean retry = false;
        String retryReason = "";
        String matchedRegex = "";
        String matchedRegexPattern = "";
        String matchedRegexDescription = "";

        for (ReplyerAfterResponseHook hook : registry.hooks()) {
            ReplyerHookResult result;
            try {
                result = hook.afterResponse(context);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (result == null) {
                continue;
            }
            String rewritten = safe(result.response());
            if (!rewritten.isBlank() && !rewritten.equals(response)) {
                events.add(new HookEvent("rewrite", response, rewritten, "", "", "", ""));
                response = rewritten;
            }
            if (result.retry()) {
                retry = true;
                retryReason = safe(result.retryReason());
                matchedRegex = safe(result.matchedRegex());
                matchedRegexPattern = safe(result.matchedRegexPattern());
                matchedRegexDescription = safe(result.matchedRegexDescription());
                events.add(new HookEvent("retry", response, response, retryReason, matchedRegex, matchedRegexPattern, matchedRegexDescription));
                break;
            }
        }
        return new HookOutcome(response, retry, retryReason, matchedRegex, matchedRegexPattern, matchedRegexDescription, events);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record HookOutcome(
            String response,
            boolean retry,
            String retryReason,
            String matchedRegex,
            String matchedRegexPattern,
            String matchedRegexDescription,
            List<HookEvent> events
    ) {
    }

    public record HookEvent(
            String type,
            String before,
            String after,
            String retryReason,
            String matchedRegex,
            String matchedRegexPattern,
            String matchedRegexDescription
    ) {
    }
}
