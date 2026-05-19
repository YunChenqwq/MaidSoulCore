package com.maidsoul.brain.reply.effect;

/**
 * 一条回复的效果评分。
 *
 * <p>ASI 分数越高代表后续互动越顺；frictionScore 越高代表摩擦越强。
 * 这里先移植 maibotdev 的确定性行为评分部分，不把本地原型强行塞进 judge 链路。</p>
 */
public record ReplyEffectScores(
        double asi,
        double behaviorScore,
        double relationalScore,
        double frictionScore,
        BehaviorSignals behaviorSignals,
        FrictionSignals frictionSignals
) {
}
