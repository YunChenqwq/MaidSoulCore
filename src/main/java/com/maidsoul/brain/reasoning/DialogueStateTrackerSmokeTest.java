package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.message.ChatMessage;

import java.util.List;

/**
 * 短期对话状态测试。
 *
 * <p>这里固定“用户低落”和“用户投诉”必须分流，避免回复器把外部伤心误当成关系修复。</p>
 */
public final class DialogueStateTrackerSmokeTest {
    private DialogueStateTrackerSmokeTest() {
    }

    public static void main(String[] args) {
        DialogueStateTracker tracker = new DialogueStateTracker();
        DialogueState distressed = tracker.update(List.of(ChatMessage.user("用户", "呜呜呜")), List.of());
        if (distressed.mode() != DialogueMode.USER_DISTRESSED) {
            throw new IllegalStateException("呜呜呜应进入用户低落状态，实际=" + distressed.mode());
        }

        DialogueState repair = tracker.update(List.of(ChatMessage.user("用户", "你一点都不可爱")), List.of());
        if (repair.mode() != DialogueMode.REPAIR_NEEDED) {
            throw new IllegalStateException("不可爱反馈应进入关系修复状态，实际=" + repair.mode());
        }

        System.out.println("DIALOGUE_STATE_TRACKER_SMOKE_OK");
    }
}
