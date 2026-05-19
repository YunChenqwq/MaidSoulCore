package com.maidsoul.brain.reply.effect;

/**
 * 用户后续行为信号。
 *
 * <p>字段含义对齐 maibotdev：继续聊、下一条情绪、是否展开、是否纠正、是否中止。</p>
 */
public record BehaviorSignals(
        double continue2Turns,
        double nextUserSentiment,
        double userExpansion,
        double noCorrection,
        double noAbort,
        String evidenceSource
) {
}
