package com.maidsoulcore.forge.execution;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗执行会话。
 * <p>
 * 这是执行层持有的最小运行时对象，用来把“持续推进的动作任务”
 * 从聊天链路和 planner 链路里拆出来。
 * 首版只处理战斗，因此字段聚焦在：
 * - 当前战斗任务使用哪个攻击任务 id
 * - 当前锁定的主目标是谁
 * - 后续待处理目标队列是什么
 * - 会话从何时开始、何时开始锁当前目标
 * - 当前是单体模式还是群体模式
 */
public final class MaidSoulExecutionSession {
    private final long sessionId;
    private final String taskId;
    private final String driverId;
    private final String entityTypeFilter;
    private final List<TargetEntry> targets = new ArrayList<>();
    private MaidSoulExecutionStatus status = MaidSoulExecutionStatus.RUNNING;
    private int currentIndex = -1;
    private int currentTargetId = -1;
    private long startedAtMillis;
    private long currentTargetLockAtMillis;
    private String lastResult = "running";

    public MaidSoulExecutionSession(long sessionId, String taskId, String driverId, String entityTypeFilter, List<TargetEntry> targets) {
        this.sessionId = sessionId;
        this.taskId = taskId;
        this.driverId = driverId;
        this.entityTypeFilter = entityTypeFilter;
        this.targets.addAll(targets);
        this.startedAtMillis = System.currentTimeMillis();
    }

    /**
     * 锁定当前会话的主目标。
     */
    public void lockCurrent(int index, int entityId) {
        this.currentIndex = index;
        this.currentTargetId = entityId;
        this.currentTargetLockAtMillis = System.currentTimeMillis();
    }

    public long sessionId() {
        return sessionId;
    }

    public String taskId() {
        return taskId;
    }

    public String driverId() {
        return driverId;
    }

    public String entityTypeFilter() {
        return entityTypeFilter;
    }

    public List<TargetEntry> targets() {
        return targets;
    }

    public MaidSoulExecutionStatus status() {
        return status;
    }

    public void status(MaidSoulExecutionStatus status) {
        this.status = status;
    }

    public int currentIndex() {
        return currentIndex;
    }

    public int currentTargetId() {
        return currentTargetId;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long currentTargetLockAtMillis() {
        return currentTargetLockAtMillis;
    }

    public String lastResult() {
        return lastResult;
    }

    public void lastResult(String lastResult) {
        this.lastResult = lastResult;
    }

    /**
     * 计算当前锁定目标是否已超时。
     */
    public boolean isCurrentTargetTimedOut(long timeoutMillis) {
        return currentTargetId >= 0 && System.currentTimeMillis() - currentTargetLockAtMillis >= timeoutMillis;
    }

    /**
     * 返回当前目标后面还剩多少待处理目标。
     */
    public int remainingCount() {
        return Math.max(0, targets.size() - Math.max(currentIndex, 0));
    }

    /**
     * 战斗执行目标条目。
     */
    public record TargetEntry(int entityId, String source) {
    }
}
