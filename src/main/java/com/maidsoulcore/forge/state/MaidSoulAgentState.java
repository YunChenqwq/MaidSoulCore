package com.maidsoulcore.forge.state;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.EventStage;
import com.maidsoulcore.forge.service.MaidSoulCompanionService;
import com.maidsoulcore.forge.service.MaidSoulPlanService;
import com.maidsoulcore.trace.TraceEvent;
import com.maidsoulcore.trace.TraceSink;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单只女仆的运行时观测状态。
 * <p>
 * 它一方面缓存 Forge/TLM 状态摘要，另一方面把关键变化写入 trace，
 * 方便后续让上下文、聊天、调试面板与工具调用共享同一份观测结果。
 */
final class MaidSoulAgentState {
    private final UUID maidId;
    private final TraceSink traceSink;
    private final AtomicLong traceSequence;
    private String maidName = "";
    private String ownerName = "";
    private String lastEventType = "none";
    private String lastEventDetail = "";
    private String ownerViewRawSummary = "";
    private String ownerViewInterpretedSummary = "";
    private String schedule = "unknown";
    private String task = "unknown";
    private boolean homeMode;
    private boolean sitting;
    private boolean sleeping;
    private String weather = "clear";
    private String timePhase = "unknown";
    private double health;
    private long lastOwnerChatMillis;
    private int totalObservedEvents;

    MaidSoulAgentState(UUID maidId, TraceSink traceSink, AtomicLong traceSequence) {
        this.maidId = maidId;
        this.traceSink = traceSink;
        this.traceSequence = traceSequence;
    }

    /**
     * 在 tick 中持续观测女仆状态，只在发生变化时写 trace。
     */
    void observeTick(EntityMaid maid) {
        String currentSchedule = String.valueOf(maid.getSchedule());
        String currentTask = maid.getTask() == null ? "none" : maid.getTask().getUid().toString();
        boolean currentHomeMode = maid.isHomeModeEnable();
        boolean currentSitting = maid.isInSittingPose();
        boolean currentSleeping = maid.isSleeping();
        String currentWeather = describeWeather(maid);
        String currentTimePhase = describeTimePhase(maid);
        updateIdentity(maid);
        if (!currentSchedule.equals(this.schedule)) {
            appendTrace(maid, "maid.schedule.changed", currentSchedule, EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, "maid.schedule.changed", currentSchedule, EventPriority.P1);
            this.schedule = currentSchedule;
        }
        if (!currentTask.equals(this.task)) {
            appendTrace(maid, "maid.task.changed", currentTask, EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, "maid.task.changed", currentTask, EventPriority.P1);
            this.task = currentTask;
        }
        if (currentHomeMode != this.homeMode) {
            appendTrace(maid, "maid.home_mode.changed", Boolean.toString(currentHomeMode), EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, "maid.home_mode.changed", Boolean.toString(currentHomeMode), EventPriority.P1);
            this.homeMode = currentHomeMode;
        }
        if (currentSitting != this.sitting) {
            appendTrace(maid, "maid.sitting.changed", Boolean.toString(currentSitting), EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, "maid.sitting.changed", Boolean.toString(currentSitting), EventPriority.P1);
            this.sitting = currentSitting;
        }
        if (currentSleeping != this.sleeping) {
            appendTrace(maid, currentSleeping ? "maid.sleep.enter" : "maid.sleep.exit", "", EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, currentSleeping ? "maid.sleep.enter" : "maid.sleep.exit", "", EventPriority.P1);
            this.sleeping = currentSleeping;
        }
        if (!currentWeather.equals(this.weather)) {
            appendTrace(maid, "world.weather.changed", currentWeather, EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, "world.weather.changed", currentWeather, EventPriority.P1);
            this.weather = currentWeather;
        }
        if (!currentTimePhase.equals(this.timePhase)) {
            appendTrace(maid, "world.time_phase.changed", currentTimePhase, EventPriority.P1);
            MaidSoulCompanionService.onObservedStateChange(maid, "world.time_phase.changed", currentTimePhase, EventPriority.P1);
            this.timePhase = currentTimePhase;
        }
        this.health = maid.getHealth();
        this.lastOwnerChatMillis = MaidSoulCompanionService.getLastOwnerChatMillis(maid.getUUID());
    }

    /**
     * 记录一条显式事件，例如受击、交互、进食等。
     */
    void recordEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        updateIdentity(maid);
        this.health = maid.getHealth();
        this.lastEventType = eventType;
        this.lastEventDetail = detail;
        this.totalObservedEvents++;
        this.lastOwnerChatMillis = MaidSoulCompanionService.getLastOwnerChatMillis(maid.getUUID());
        appendTrace(maid, eventType, detail, priority);
    }

    /**
     * 更新最近一次主人视角观测结果。
     * <p>
     * 这里不把它当成“显式事件广播”处理，而是作为运行时感知缓存，
     * 这样 planner、reply、工具上下文都能持续读取到最新视角摘要。
     */
    void updateOwnerView(EntityMaid maid, String rawSummary, String interpretedSummary) {
        updateIdentity(maid);
        this.ownerViewRawSummary = rawSummary == null ? "" : rawSummary;
        this.ownerViewInterpretedSummary = interpretedSummary == null ? "" : interpretedSummary;
        this.lastOwnerChatMillis = MaidSoulCompanionService.getLastOwnerChatMillis(maid.getUUID());
        appendTrace(maid, "owner.view.capture", this.ownerViewInterpretedSummary, EventPriority.P2);
    }

    /**
     * 导出轻量快照，给 prompt、工具与调试界面读取。
     */
    MaidSoulStateSnapshot snapshot() {
        return new MaidSoulStateSnapshot(
                maidName,
                ownerName,
                lastEventType,
                lastEventDetail,
                ownerViewRawSummary,
                ownerViewInterpretedSummary,
                schedule,
                task,
                homeMode,
                sitting,
                sleeping,
                weather,
                timePhase,
                health,
                lastOwnerChatMillis,
                totalObservedEvents
        );
    }

    /**
     * 同步名称信息，避免调用方重复处理。
     */
    private void updateIdentity(EntityMaid maid) {
        this.maidName = maid.getName().getString();
        LivingEntity owner = maid.getOwner();
        this.ownerName = owner == null ? "" : owner.getName().getString();
    }

    /**
     * 将当前天气压成适合写进黑板和提示词的稳定标签。
     */
    private String describeWeather(EntityMaid maid) {
        if (maid.level().isThundering()) {
            return "thunder";
        }
        if (maid.level().isRaining()) {
            return "rain";
        }
        return "clear";
    }

    /**
     * 把世界时间段压成更接近人类表达的标签，便于触发问候与提醒。
     */
    private String describeTimePhase(EntityMaid maid) {
        long dayTime = maid.level().getDayTime() % 24000L;
        if (dayTime < 1000L) {
            return "dawn";
        }
        if (dayTime < 6000L) {
            return "morning";
        }
        if (dayTime < 12000L) {
            return "day";
        }
        if (dayTime < 13000L) {
            return "sunset";
        }
        if (dayTime < 18000L) {
            return "evening";
        }
        return "night";
    }

    /**
     * 把一条观测结果写入 trace，并按配置选择是否同步回显到聊天栏。
     */
    private void appendTrace(EntityMaid maid, String type, String reason, EventPriority priority) {
        traceSink.accept(new TraceEvent(
                traceSequence.incrementAndGet(),
                Instant.now(),
                maidId.toString(),
                maidId.toString(),
                type,
                priority,
                EventStage.INGEST,
                "FORGE_EVENT",
                reason
        ));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, type, reason);
        MaidSoulPlanService.publishEvent(maid, type, reason, priority);
    }
}
