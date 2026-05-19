package com.maidsoul.brain.llm.message;

/**
 * 模型消息片段。
 *
 * <p>文本、图片等多模态内容都先抽象成消息片段，避免后续在请求构造里到处判断字符串和图片。</p>
 */
public sealed interface MessagePart permits TextMessagePart, ImageMessagePart {
    String debugText();
}

