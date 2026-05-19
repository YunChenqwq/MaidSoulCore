package com.maidsoul.brain.reasoning;

/**
 * 当前会话的短期互动模式。
 *
 * <p>它解决的是“同一句短反馈在不同关系上下文里含义不同”的问题。
 * 例如普通场景里的句号可能只是收束，但冲突场景里的句号更像冷淡或不满残留。</p>
 */
enum DialogueMode {
    NORMAL_CHAT("正常聊天", "自然接话，可以轻轻推进话题。"),
    WAITING_USER_FINISH("等待用户说完", "用户可能还在连续输入，优先等一小段静默窗口。"),
    USER_DISTRESSED("用户低落", "用户正在难过、哭泣或委屈。先陪伴和安抚，再用一个很轻的问题帮助倾诉；不要调侃，不要把哭或委屈转移到酒狐自己身上。"),
    USER_COMPLAINING("用户不满", "用户在指出没被接住、不可爱、冷淡或失望，需要先承认感受。"),
    REPAIR_NEEDED("关系修复", "刚发生明显误解或用户生气，需要先认错和软下来，不要继续嘴硬。"),
    MAID_HURT("女仆受伤", "用户有明显攻击或辱骂，酒狐可以委屈和设边界，但不要秒讨好。"),
    COOLDOWN_AFTER_CONFLICT("冲突冷却", "冲突刚收束，需要放慢节奏，少追问，给彼此台阶。");

    private final String label;
    private final String guidance;

    DialogueMode(String label, String guidance) {
        this.label = label;
        this.guidance = guidance;
    }

    String label() {
        return label;
    }

    String guidance() {
        return guidance;
    }
}
