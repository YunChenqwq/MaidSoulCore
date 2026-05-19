package com.maidsoul.brain;

import com.maidsoul.brain.config.FlowConfig;
import com.maidsoul.brain.runtime.MessageTurnScheduler;
import com.maidsoul.brain.runtime.ProactiveScheduler;

/**
 * 节奏调度器离线测试。
 *
 * <p>不调用模型，只验证 maibotdev 风格消息流控频和主动候选调度的职责边界。</p>
 */
public final class SchedulerSmokeTest {
    private SchedulerSmokeTest() {
    }

    public static void main(String[] args) {
        testMessageFrequencyThreshold();
        testIdleCompensation();
        testProactiveCandidateDoesNotForceReply();
        System.out.println("SCHEDULER_SMOKE_OK");
    }

    private static void testMessageFrequencyThreshold() {
        MessageTurnScheduler scheduler = new MessageTurnScheduler();
        if (scheduler.messageTriggerThreshold(1.0) != 1) {
            throw new IllegalStateException("talkFrequency=1.0 应该每条消息都可触发。");
        }
        if (scheduler.messageTriggerThreshold(0.5) != 2) {
            throw new IllegalStateException("talkFrequency=0.5 应该约两条消息触发一次。");
        }
        MessageTurnScheduler.Decision decision = scheduler.decide(1, 0.5, false, 0, null);
        if (decision.triggerNow() || decision.delayMillis() >= 0) {
            throw new IllegalStateException("没有平均回复耗时时，低频单条消息不应立即触发。");
        }
    }

    private static void testIdleCompensation() {
        MessageTurnScheduler scheduler = new MessageTurnScheduler();
        MessageTurnScheduler.Decision decision = scheduler.decide(1, 0.5, false, 1_000, 1_000L);
        if (!decision.triggerNow()) {
            throw new IllegalStateException("空窗补偿达到阈值后应触发主循环。");
        }
    }

    private static void testProactiveCandidateDoesNotForceReply() {
        ProactiveScheduler scheduler = new ProactiveScheduler(flow());
        if (!scheduler.shouldScheduleAfterSilentDecision(80, 1)) {
            throw new IllegalStateException("主动好奇较高时可以继续排候选。");
        }
        if (scheduler.shouldScheduleAfterSilentDecision(40, 2)) {
            throw new IllegalStateException("主动好奇较低且已连续沉默时不应继续排候选。");
        }
        String event = scheduler.buildEvent(45, ProactiveScheduler.STAGE_RELATION_CANDIDATE, 82, "主动好奇较高。");
        if (!event.contains("planner 可以 reply、wait 或 no_action")) {
            throw new IllegalStateException("主动候选事件必须声明最终动作由 planner 决定。");
        }
    }

    private static FlowConfig flow() {
        return new FlowConfig(
                36,
                800,
                4,
                false,
                8,
                1.0,
                2,
                3000,
                false,
                true,
                12,
                30,
                75,
                180,
                300
        );
    }
}
