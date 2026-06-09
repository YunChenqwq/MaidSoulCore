package com.yunchen.maidsoulcore.core.reply;

import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.context.ContextPack;
import com.yunchen.maidsoulcore.core.llm.LlmClient;
import com.yunchen.maidsoulcore.core.llm.LlmMessage;
import com.yunchen.maidsoulcore.core.message.RuntimeMessage;
import com.yunchen.maidsoulcore.core.prompt.PromptCatalog;
import com.yunchen.maidsoulcore.core.prompt.PromptRenderer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ReplyGenerator {
    private final DialogueCoreConfig config;
    private final PromptCatalog prompts;
    private final LlmClient llm;
    private final ReplyOutputGuard guard = new ReplyOutputGuard();

    public ReplyGenerator(DialogueCoreConfig config, PromptCatalog prompts, LlmClient llm) {
        this.config = config;
        this.prompts = prompts;
        this.llm = llm;
    }

    public String generate(ContextPack context, RuntimeMessage target, String identity, String referenceInfo) {
        return generate(context, target, identity, referenceInfo, null);
    }

    public String generate(ContextPack context, RuntimeMessage target, String identity, String referenceInfo, Consumer<String> deltaConsumer) {
        String targetText = target == null
                ? "没有明确目标消息，请基于最新消息自然接话。"
                : "- msg_id：" + target.id() + "\n- 用户名：" + target.speaker() + "\n- 发言内容：" + target.content();
        String prompt = PromptRenderer.render(prompts.load("replyer"), Map.of(
                "identity", identity,
                "target_message", targetText,
                "reference_info", referenceInfo == null ? "" : referenceInfo,
                "context", context == null ? "" : context.text()
        ));

        String last = "";
        for (int attempt = 0; attempt <= Math.max(1, config.replyRetryCount); attempt++) {
            String effectivePrompt = attempt == 0 ? prompt : prompt + "\n\n上一版回复不符合可见聊天格式，请重新生成一句自然发言，只输出台词。";
            last = attempt == 0 && deltaConsumer != null
                    ? llm.chatStream(List.of(new LlmMessage("system", effectivePrompt)), config.llmTimeoutMillis, deltaConsumer).content().trim()
                    : llm.chat(List.of(new LlmMessage("system", effectivePrompt)), config.llmTimeoutMillis).content().trim();
            if (deltaConsumer != null) {
                return last.isBlank() ? fallback(target) : last;
            }
            if (guard.isUsable(last)) {
                return last;
            }
        }
        return fallback(target);
    }

    private String fallback(RuntimeMessage target) {
        if (target != null && target.content().contains("？")) {
            return "你先别急，我听见了。";
        }
        return "笨蛋，我在听啦。";
    }
}
