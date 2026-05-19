package com.maidsoul.brain.reply.effect;

/**
 * 回复效果记录状态。
 *
 * <p>对应 maibotdev 的 ReplyEffectStatus：回复发出后先进入 pending，
 * 观察到足够的后续反馈或窗口超时后转为 finalized。</p>
 */
public enum ReplyEffectStatus {
    PENDING("pending"),
    FINALIZED("finalized");

    private final String value;

    ReplyEffectStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
