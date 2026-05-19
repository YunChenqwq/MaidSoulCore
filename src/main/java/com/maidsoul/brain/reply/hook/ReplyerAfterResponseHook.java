package com.maidsoul.brain.reply.hook;

/**
 * replyer.after_response 扩展点。
 *
 * <p>对齐 maibotdev 的 maisaka.replyer.after_response hook。核心链路只提供扩展协议，
 * 是否重写或重生成由外部规则/插件决定，避免把具体中文坏例子写死进 replyer。</p>
 */
@FunctionalInterface
public interface ReplyerAfterResponseHook {
    ReplyerHookResult afterResponse(ReplyerHookContext context);
}
