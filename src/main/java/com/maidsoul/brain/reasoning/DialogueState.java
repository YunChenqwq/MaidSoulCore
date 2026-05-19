package com.maidsoul.brain.reasoning;

/**
 * 会话短期状态快照。
 */
record DialogueState(DialogueMode mode, String evidence) {
    static DialogueState normal() {
        return new DialogueState(DialogueMode.NORMAL_CHAT, "没有明显冲突或等待信号。");
    }

    String renderForPrompt() {
        return "模式=" + mode.label()
                + "\n依据=" + evidence
                + "\n回复原则=" + mode.guidance();
    }
}
