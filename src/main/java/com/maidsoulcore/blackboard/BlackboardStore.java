package com.maidsoulcore.blackboard;

import com.maidsoulcore.mood.MoodState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行时黑板存储。
 * <p>
 * 这里保存的是当前女仆最值得被多个模块共享的状态，
 * 比如最近一次观测结果、策略字段、情绪等。
 * <p>
 * 当前实现是轻量版：
 * 使用线程安全 Map 和版本号满足最基本的读写与快照需求。
 */
public final class BlackboardStore {
    private final AtomicLong version = new AtomicLong();
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private volatile MoodState moodState = MoodState.neutral();

    /**
     * 更新一个黑板键值，并递增版本号。
     */
    public void put(String key, Object value) {
        state.put(key, value);
        version.incrementAndGet();
    }

    /**
     * 更新情绪状态，并递增版本号。
     */
    public void setMood(MoodState moodState) {
        this.moodState = moodState;
        version.incrementAndGet();
    }

    /**
     * 生成当前黑板的只读快照。
     */
    public BlackboardView snapshot(String maidId, String ownerId) {
        return new BlackboardView(maidId, ownerId, version.get(), moodState, Map.copyOf(state));
    }
}
