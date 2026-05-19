package com.maidsoul.brain.llm;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一轮模型请求的中断标记。
 *
 * <p>它不是用来“隐藏旧输出”的版本号，而是会被 LLM 客户端轮询。
 * 新消息到达时运行时设置该标记，底层 HTTP future 会尽量取消，避免旧请求继续烧 token 和连接。</p>
 */
public final class InterruptFlag {
    private final AtomicBoolean requested = new AtomicBoolean(false);

    public void requestInterrupt() {
        requested.set(true);
    }

    public boolean isInterrupted() {
        return requested.get();
    }
}
