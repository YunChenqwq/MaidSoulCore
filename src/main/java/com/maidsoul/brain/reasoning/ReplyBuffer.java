package com.maidsoul.brain.reasoning;

/**
 * 回复内部流式缓冲区。
 *
 * <p>对齐 maibotdev 的关键点：底层模型可以流式返回，但 delta 只进入内存缓冲；
 * 运行时不会把半句话直接发给用户。若本轮被新消息中断，整个缓冲会随异常丢弃。</p>
 */
final class ReplyBuffer {
    private final StringBuilder builder = new StringBuilder();

    synchronized void append(String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        builder.append(delta);
    }

    synchronized String content() {
        return builder.toString();
    }
}
