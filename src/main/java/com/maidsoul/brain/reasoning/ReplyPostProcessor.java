package com.maidsoul.brain.reasoning;

/**
 * 可见回复后处理器。
 *
 * <p>它对应 上游参考系统 的 post_process_reply_text / post_process_reply_message_sequences：
 * 只做“模型输出格式到可发送文本”的收束，例如去掉内部标签、动作括号和空白。
 * 它不负责把角色写得更可爱，也不负责往回复里塞补丁台词。</p>
 */
final class ReplyPostProcessor {
    private final ReplySanitizer sanitizer = new ReplySanitizer();

    String process(String raw) {
        return sanitizer.clean(raw);
    }
}
