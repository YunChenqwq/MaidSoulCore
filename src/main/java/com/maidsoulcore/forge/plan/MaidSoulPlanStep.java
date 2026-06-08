package com.maidsoulcore.forge.plan;

/**
 * 计划中的单个步骤。
 * <p>
 * 当前版本故意保持简单：一个步骤对应一个动作。
 * 真正的多阶段计划通过多个 step 串起来，而不是在一个 step 里塞很多逻辑。
 */
public final class MaidSoulPlanStep {
    private final String description;
    private final String actionType;
    private final String actionValue;
    private final int targetEntityId;
    private final int timeoutTicks;
    private MaidSoulPlanStepStatus status = MaidSoulPlanStepStatus.PENDING;
    private long startedAtTick = -1L;
    private String lastResult = "";

    public MaidSoulPlanStep(String description, String actionType, String actionValue, int targetEntityId, int timeoutTicks) {
        this.description = description == null ? "" : description;
        this.actionType = actionType == null ? "NONE" : actionType;
        this.actionValue = actionValue == null ? "" : actionValue;
        this.targetEntityId = targetEntityId;
        this.timeoutTicks = Math.max(20, timeoutTicks);
    }

    /**
     * 返回步骤说明。
     */
    public String description() {
        return description;
    }

    /**
     * 返回动作类型。
     */
    public String actionType() {
        return actionType;
    }

    /**
     * 返回动作值。
     */
    public String actionValue() {
        return actionValue;
    }

    /**
     * 返回目标实体 id。
     */
    public int targetEntityId() {
        return targetEntityId;
    }

    /**
     * 返回超时 tick。
     */
    public int timeoutTicks() {
        return timeoutTicks;
    }

    /**
     * 返回步骤状态。
     */
    public MaidSoulPlanStepStatus status() {
        return status;
    }

    /**
     * 返回步骤启动 tick。
     */
    public long startedAtTick() {
        return startedAtTick;
    }

    /**
     * 返回最近一次执行结果。
     */
    public String lastResult() {
        return lastResult;
    }

    /**
     * 将步骤标记为运行中。
     */
    public void markRunning(long currentTick, String result) {
        this.status = MaidSoulPlanStepStatus.RUNNING;
        this.startedAtTick = currentTick;
        this.lastResult = result == null ? "" : result;
    }

    /**
     * 将步骤标记为完成。
     */
    public void markCompleted(String result) {
        this.status = MaidSoulPlanStepStatus.COMPLETED;
        this.lastResult = result == null ? "" : result;
    }

    /**
     * 将步骤标记为失败。
     */
    public void markFailed(String result) {
        this.status = MaidSoulPlanStepStatus.FAILED;
        this.lastResult = result == null ? "" : result;
    }

    /**
     * 当计划被更高优先级计划抢占时，把当前步骤恢复为待执行，
     * 以便后续恢复计划时重新分发到执行层。
     */
    public void resetToPending(String result) {
        this.status = MaidSoulPlanStepStatus.PENDING;
        this.startedAtTick = -1L;
        this.lastResult = result == null ? "" : result;
    }

    /**
     * 判断是否已经超时。
     */
    public boolean isTimedOut(long currentTick) {
        return this.status == MaidSoulPlanStepStatus.RUNNING
                && this.startedAtTick >= 0L
                && currentTick - this.startedAtTick >= this.timeoutTicks;
    }

    /**
     * 返回简短调试摘要。
     */
    public String summary() {
        return "desc=" + description
                + ", action=" + actionType
                + ", value=" + actionValue
                + ", target=" + targetEntityId
                + ", status=" + status;
    }
}
