package com.maidsoulcore.forge.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 女仆本地执行计划。
 * <p>
 * 一条计划通常来自：
 * - 主人的明确命令
 * - 高优先级事件触发的紧急插队
 * - 后续可能扩展的自动策略
 */
public final class MaidSoulPlan {
    private final String planId;
    private final String source;
    private final String objective;
    private final MaidSoulPlanPriority priority;
    private final List<MaidSoulPlanStep> steps;
    private MaidSoulPlanStatus status = MaidSoulPlanStatus.QUEUED;
    private int currentStepIndex = 0;
    private String lastEventType = "";
    private String lastDetail = "";

    public MaidSoulPlan(String source, String objective, MaidSoulPlanPriority priority, List<MaidSoulPlanStep> steps) {
        this.planId = UUID.randomUUID().toString();
        this.source = source == null ? "unknown" : source;
        this.objective = objective == null ? "" : objective;
        this.priority = priority == null ? MaidSoulPlanPriority.OWNER_COMMAND : priority;
        this.steps = new ArrayList<>(steps == null ? List.of() : steps);
    }

    public String planId() {
        return planId;
    }

    public String source() {
        return source;
    }

    public String objective() {
        return objective;
    }

    public MaidSoulPlanPriority priority() {
        return priority;
    }

    public MaidSoulPlanStatus status() {
        return status;
    }

    public void setStatus(MaidSoulPlanStatus status) {
        this.status = status;
    }

    public int currentStepIndex() {
        return currentStepIndex;
    }

    public void advanceStep() {
        this.currentStepIndex++;
    }

    public boolean hasMoreSteps() {
        return this.currentStepIndex < this.steps.size();
    }

    public MaidSoulPlanStep currentStep() {
        return hasMoreSteps() ? this.steps.get(this.currentStepIndex) : null;
    }

    public List<MaidSoulPlanStep> steps() {
        return List.copyOf(this.steps);
    }

    public void updateLastEvent(MaidSoulEventEnvelope event) {
        if (event == null) {
            return;
        }
        this.lastEventType = event.eventType();
        this.lastDetail = event.detail();
    }

    public String lastEventType() {
        return lastEventType;
    }

    public String lastDetail() {
        return lastDetail;
    }

    /**
     * 返回扫描友好的调试摘要。
     */
    public String summary() {
        MaidSoulPlanStep step = currentStep();
        return "id=" + planId
                + ", source=" + source
                + ", objective=" + objective
                + ", priority=" + priority
                + ", status=" + status
                + ", step=" + (step == null ? "none" : (currentStepIndex + 1) + "/" + steps.size() + " " + step.summary());
    }
}
