package com.maidsoul.brain.affect;

/**
 * 情绪事件类型。
 *
 * <p>这里描述“发生了什么”，而不是描述“某个词命中了什么”。自然语言到事件的判断应放在
 * planner/语义分析/工具层，不能塞进情绪积分器。</p>
 */
public enum AffectEventKind {
    OWNER_MESSAGE,
    ASSISTANT_REPLY,
    OWNER_APOLOGY,
    OWNER_ATTACK,
    OWNER_DISTRESS,
    OWNER_AFFECTION,
    OWNER_QUESTION,
    OWNER_SHORT_FEEDBACK,
    MAID_HURT_BY_OWNER,
    MAID_HURT_BY_WORLD,
    QUIET_RECOVERY
}
