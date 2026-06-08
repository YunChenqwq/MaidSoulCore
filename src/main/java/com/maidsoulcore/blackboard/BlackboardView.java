package com.maidsoulcore.blackboard;

import com.maidsoulcore.mood.MoodState;

import java.util.Map;

/**
 * 黑板的不可变快照。
 * <p>
 * Planner 和其他只读模块不应直接操作可变存储，
 * 而是拿到一份快照后进行推理或展示。
 */
public record BlackboardView(
        String maidId,
        String ownerId,
        long version,
        MoodState mood,
        Map<String, Object> state
) {
}
