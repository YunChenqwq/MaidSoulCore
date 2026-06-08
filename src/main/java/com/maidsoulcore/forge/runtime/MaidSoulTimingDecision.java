package com.maidsoulcore.forge.runtime;

/**
 * 本地时序门返回值。
 *
 * @param action 当前时序动作
 * @param reason 当前决策原因
 * @param waitMillis 若进入等待态，需要等待的毫秒数
 */
public record MaidSoulTimingDecision(MaidSoulTimingAction action, String reason, long waitMillis) {
    /**
     * 构造一个继续执行的决策。
     */
    public static MaidSoulTimingDecision continueNow(String reason) {
        return new MaidSoulTimingDecision(MaidSoulTimingAction.CONTINUE, reason, 0L);
    }

    /**
     * 构造一个等待决策。
     */
    public static MaidSoulTimingDecision waitFor(String reason, long waitMillis) {
        return new MaidSoulTimingDecision(MaidSoulTimingAction.WAIT, reason, Math.max(0L, waitMillis));
    }

    /**
     * 构造一个不回复决策。
     */
    public static MaidSoulTimingDecision noReply(String reason) {
        return new MaidSoulTimingDecision(MaidSoulTimingAction.NO_REPLY, reason, 0L);
    }

    /**
     * 构造一个结束当前轮次的决策。
     */
    public static MaidSoulTimingDecision finishNow(String reason) {
        return new MaidSoulTimingDecision(MaidSoulTimingAction.FINISH, reason, 0L);
    }
}
