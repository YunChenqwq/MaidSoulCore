package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.forge.task.MaidSoulGlobalAttackTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MaidSoulCore 的动作执行器。
 * <p>
 * 这一层负责把 planner 输出的动作真正落到 TLM 女仆实体上。
 * 对战斗任务额外补了一层目标解析、追击内存写入和失效回退，
 * 用来解决“锁了目标但女仆只会原地挥刀”以及“单个目标失效后不会续接”这两个问题。
 */
public final class MaidSoulActionExecutorService {
    /**
     * 自动补目标时使用的搜索半径。
     */
    private static final double AUTO_TARGET_RADIUS = 24.0d;
    /**
     * 已经锁定目标后允许维持的更大半径。
     * <p>
     * 这样可以避免目标稍微跑远一点就立刻被判定丢失。
     */
    private static final double LOCKED_TARGET_KEEP_RADIUS = 40.0d;
    /**
     * 主动锁敌时同步写入的追击速度。
     */
    private static final float ATTACK_WALK_SPEED = 0.6f;
    /**
     * 单个锁定目标的最长追击时间，超过后自动切换下一目标。
     */
    private static final long TARGET_LOCK_TIMEOUT_MILLIS = 15_000L;
    /**
     * 锁定特效的刷新间隔。
     */
    private static final int TARGET_LOCK_EFFECT_INTERVAL_TICKS = 10;
    /**
     * 锁定状态 trace 的刷新间隔。
     */
    private static final int TARGET_LOCK_TRACE_INTERVAL_TICKS = 20;
    /**
     * 每只女仆的战斗计划状态。
     */
    private static final ConcurrentMap<UUID, AttackPlanState> ATTACK_PLANS = new ConcurrentHashMap<>();

    private MaidSoulActionExecutorService() {
    }

    /**
     * 每 tick 维护一次攻击计划。
     * <p>
     * 规则：
     * 1. 当前锁定目标活着且未超时，就继续追击，不切换；
     * 2. 当前目标死亡、失效或超时，再切到下一个候选；
     * 3. 没有合法目标时，清空战斗内存，避免女仆持续挥空刀。
     */
    public static void onMaidTick(EntityMaid maid) {
        IMaidTask task = maid.getTask();
        if (!(task instanceof IAttackTask attackTask)) {
            discardAttackPlan(maid);
            return;
        }
        AttackPlanState plan = ATTACK_PLANS.get(maid.getUUID());
        if (plan == null) {
            ensureCurrentBrainTargetStillValid(maid, attackTask);
            return;
        }

        LivingEntity currentTarget = entityFromId(maid, plan.currentTargetId());
        if (isPlannedTargetStillValid(maid, attackTask, currentTarget)) {
            ensureAttackTargetMemory(maid, currentTarget);
            emitLockEffect(maid, currentTarget);
            traceLockedTarget(maid, plan, currentTarget);
            return;
        }

        if (currentTarget != null && plan.isCurrentTargetTimedOut()) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.attack.plan.timeout",
                    "target=" + currentTarget.getName().getString() + "#" + currentTarget.getId()
            );
        }

        ResolvedAttackTarget nextTarget = plan.nextValidTarget(maid, attackTask);
        if (nextTarget != null) {
            plan.lock(nextTarget.target().getId());
            syncAttackTarget(maid, nextTarget.target());
            emitLockEffect(maid, nextTarget.target());
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.attack.plan.advance",
                    "target=" + nextTarget.target().getName().getString() + "#" + nextTarget.target().getId()
                            + ", source=" + nextTarget.source()
                            + ", remaining=" + plan.remainingCount()
            );
            return;
        }

        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.attack.plan.clear", "no valid queued target");
        clearAttackState(maid);
        discardAttackPlan(maid);
    }

    /**
     * 执行一条 planner 输出的动作指令。
     */
    public static String execute(EntityMaid maid, MaidSoulChatRuntimeService.PlannerDecision plannerDecision) {
        String actionType = plannerDecision.actionType();
        if (actionType == null || actionType.isBlank() || "NONE".equalsIgnoreCase(actionType)) {
            return "no_action";
        }
        String result = switch (actionType.toUpperCase(Locale.ROOT)) {
            case "FOLLOW_ON" -> followOn(maid);
            case "FOLLOW_OFF" -> followOff(maid);
            case "SIT_ON" -> sit(maid, true);
            case "SIT_OFF" -> sit(maid, false);
            case "SET_SCHEDULE" -> setSchedule(maid, plannerDecision.actionValue());
            case "SET_TASK" -> setTask(maid, plannerDecision.actionValue(), plannerDecision.targetEntityId());
            case "ENTER_COMBAT" -> enterCombat(maid, plannerDecision.actionValue(), plannerDecision.targetEntityId());
            case "ENTER_COMBAT_GROUP" -> enterCombatGroup(maid, plannerDecision.actionValue(), plannerDecision.targetEntityId());
            default -> "unknown_action:" + actionType;
        };
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.chat.action.execute",
                "type=" + actionType
                        + ", value=" + plannerDecision.actionValue()
                        + ", target=" + plannerDecision.targetEntityId()
                        + ", result=" + result
        );
        return result;
    }

    /**
     * 直接以“进入战斗并锁定目标”的方式调用现有战斗执行链。
     */
    public static String enterCombatDirectly(EntityMaid maid, String preferredTaskId, int targetEntityId) {
        return enterCombat(maid, preferredTaskId, targetEntityId);
    }

    /**
     * 直接以“进入战斗并按实体类型逐个清理”的方式复用现有执行链。
     */
    public static String enterCombatGroupDirectly(EntityMaid maid, String preferredTaskId, String entityTypeId, int targetEntityId) {
        return enterCombatGroup(maid, preferredTaskId, entityTypeId, targetEntityId);
    }

    /**
     * 导出当前战斗计划摘要，供 TLM 上下文和调试面板复用。
     */
    public static String describeAttackPlan(EntityMaid maid) {
        AttackPlanState plan = ATTACK_PLANS.get(maid.getUUID());
        LivingEntity currentTarget = maid.getTarget();
        LivingEntity memoryTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (plan == null && currentTarget == null && memoryTarget == null) {
            return "no active combat plan";
        }
        String current = currentTarget == null ? "none" : currentTarget.getName().getString() + "#" + currentTarget.getId();
        String memory = memoryTarget == null ? "none" : memoryTarget.getName().getString() + "#" + memoryTarget.getId();
        if (plan == null) {
            return "combat target=" + current + ", memory=" + memory + ", queued=0";
        }
        return "combat target=" + current
                + ", memory=" + memory
                + ", queued=" + plan.remainingCount()
                + ", locked=" + plan.currentTargetId()
                + ", timed_out=" + plan.isCurrentTargetTimedOut();
    }

    /**
     * 返回当前是否仍存在未完成的战斗执行计划。
     */
    public static boolean hasActiveCombatPlan(EntityMaid maid) {
        return maid != null && ATTACK_PLANS.containsKey(maid.getUUID());
    }

    private static String followOn(EntityMaid maid) {
        if (!maid.isHomeModeEnable()) {
            return "already_following";
        }
        maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
        maid.setHomeModeEnable(false);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.follow_on", "follow owner", EventPriority.P1);
        return "follow_enabled";
    }

    private static String followOff(EntityMaid maid) {
        if (maid.isHomeModeEnable()) {
            return "already_home_mode";
        }
        maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
        maid.setHomeModeEnable(true);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.follow_off", "stay at current position", EventPriority.P1);
        return "home_mode_enabled";
    }

    private static String sit(EntityMaid maid, boolean toSit) {
        boolean isSitting = maid.isMaidInSittingPose();
        if (toSit == isSitting) {
            return toSit ? "already_sitting" : "already_standing";
        }
        maid.setInSittingPose(toSit);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.sit", "sit=" + toSit, EventPriority.P1);
        return toSit ? "sit_enabled" : "sit_disabled";
    }

    private static String setSchedule(EntityMaid maid, String scheduleName) {
        if (scheduleName == null || scheduleName.isBlank()) {
            return "missing_schedule";
        }
        MaidSchedule target;
        try {
            target = MaidSchedule.valueOf(scheduleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return "invalid_schedule:" + scheduleName;
        }
        if (maid.getSchedule() == target) {
            return "schedule_unchanged:" + target.name();
        }
        maid.setSchedule(target);
        MaidSoulStateRegistry.record(maid, "maidsoul.action.schedule", target.name(), EventPriority.P1);
        return "schedule_switched:" + target.name();
    }

    private static String setTask(EntityMaid maid, String taskId, int targetEntityId) {
        if (taskId == null || taskId.isBlank()) {
            return "missing_task_id";
        }
        Optional<IMaidTask> optionalTask = TaskManager.findTask(new ResourceLocation(taskId));
        if (optionalTask.isEmpty()) {
            return "invalid_task:" + taskId;
        }

        IMaidTask task = optionalTask.get();
        IMaidTask currentTask = maid.getTask();
        if (task != currentTask) {
            maid.setTask(task);
        }
        FunctionCallSwitchResult switchResult = task.onFunctionCallSwitch(maid);
        Activity currentActivity = maid.getScheduleDetail();

        String result = "task_switched:" + task.getUid();
        if (currentActivity != Activity.WORK) {
            result += "|not_work_time=" + maid.getSchedule().name();
        }
        result += "|switch_result=" + switchResult.name();

        if (task instanceof IAttackTask attackTask) {
            result += "|" + applyAttackTarget(maid, attackTask, targetEntityId);
        } else {
            discardAttackPlan(maid);
        }
        MaidSoulStateRegistry.record(maid, "maidsoul.action.task", result, EventPriority.P1);
        return result;
    }

    private static String enterCombat(EntityMaid maid, String preferredTaskId, int targetEntityId) {
        String resolvedTaskId = preferredTaskId;
        if (resolvedTaskId == null || resolvedTaskId.isBlank()) {
            resolvedTaskId = findDefaultAttackTaskId();
        }
        return setTask(maid, resolvedTaskId, targetEntityId);
    }

    /**
     * 进入群体战斗模式。
     * <p>
     * 该模式不会无差别攻击全部实体，而是只筛选同一种实体类型，再按顺序逐个处理。
     */
    private static String enterCombatGroup(EntityMaid maid, String entityTypeId, int targetEntityId) {
        return enterCombatGroup(maid, findDefaultAttackTaskId(), entityTypeId, targetEntityId);
    }

    /**
     * 进入群体战斗模式，并允许显式指定任务 id。
     */
    private static String enterCombatGroup(EntityMaid maid, String preferredTaskId, String entityTypeId, int targetEntityId) {
        String resolvedTaskId = preferredTaskId;
        if (resolvedTaskId == null || resolvedTaskId.isBlank()) {
            resolvedTaskId = findDefaultAttackTaskId();
        }
        String switchResult = setTask(maid, resolvedTaskId, -1);
        if (!switchResult.startsWith("task_switched")
                && !"already_on_task".equals(switchResult)
                && !"target_missing".equals(switchResult)) {
            return switchResult;
        }
        IMaidTask task = maid.getTask();
        if (!(task instanceof IAttackTask attackTask)) {
            clearAttackState(maid);
            discardAttackPlan(maid);
            return "attack_task_unavailable";
        }

        String normalizedTypeId = normalizeCombatGroupType(maid, entityTypeId, targetEntityId);
        if (normalizedTypeId == null || normalizedTypeId.isBlank()) {
            clearAttackState(maid);
            discardAttackPlan(maid);
            return "group_type_missing";
        }

        List<ResolvedAttackTarget> candidates = collectAttackCandidates(maid, attackTask, -1, Set.of(), normalizedTypeId);
        if (candidates.isEmpty()) {
            clearAttackState(maid);
            discardAttackPlan(maid);
            return "group_target_missing:" + normalizedTypeId;
        }

        ResolvedAttackTarget firstTarget = candidates.get(0);
        AttackPlanState plan = new AttackPlanState(candidates);
        plan.lock(firstTarget.target().getId());
        ATTACK_PLANS.put(maid.getUUID(), plan);
        syncAttackTarget(maid, firstTarget.target());
        emitLockEffect(maid, firstTarget.target());
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.attack.plan.group_lock",
                "type=" + normalizedTypeId
                        + ", target=" + firstTarget.target().getName().getString() + "#" + firstTarget.target().getId()
                        + ", queued=" + candidates.size()
        );
        return "group_locked:type=" + normalizedTypeId
                + "|target=" + firstTarget.target().getName().getString() + "#" + firstTarget.target().getId()
                + "|candidates=" + candidates.size()
                + "|timeout_ms=" + TARGET_LOCK_TIMEOUT_MILLIS;
    }

    private static String applyAttackTarget(EntityMaid maid, IAttackTask attackTask, int targetEntityId) {
        List<ResolvedAttackTarget> candidates = collectAttackCandidates(maid, attackTask, targetEntityId, Set.of());
        if (candidates.isEmpty()) {
            clearAttackState(maid);
            discardAttackPlan(maid);
            return "target_missing";
        }

        ResolvedAttackTarget firstTarget = candidates.get(0);
        AttackPlanState plan = new AttackPlanState(candidates);
        plan.lock(firstTarget.target().getId());
        ATTACK_PLANS.put(maid.getUUID(), plan);
        syncAttackTarget(maid, firstTarget.target());
        emitLockEffect(maid, firstTarget.target());
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.attack.plan.lock",
                "target=" + firstTarget.target().getName().getString() + "#" + firstTarget.target().getId()
                        + ", source=" + firstTarget.source()
                        + ", queued=" + candidates.size()
        );

        return "target_locked:" + firstTarget.target().getName().getString() + "#" + firstTarget.target().getId()
                + "|distance=" + String.format(Locale.ROOT, "%.1f", maid.distanceTo(firstTarget.target()))
                + "|source=" + firstTarget.source()
                + "|candidates=" + candidates.size()
                + "|timeout_ms=" + TARGET_LOCK_TIMEOUT_MILLIS;
    }

    private static List<ResolvedAttackTarget> collectAttackCandidates(EntityMaid maid,
                                                                      IAttackTask attackTask,
                                                                      int targetEntityId,
                                                                      Set<Integer> excludedEntityIds) {
        return collectAttackCandidates(maid, attackTask, targetEntityId, excludedEntityIds, null);
    }

    /**
     * 收集攻击候选目标。
     * <p>
     * `entityTypeFilter` 为空时走单目标/上下文优先模式；
     * 非空时只收集同一类型目标，并按距离排序。
     */
    private static List<ResolvedAttackTarget> collectAttackCandidates(EntityMaid maid,
                                                                      IAttackTask attackTask,
                                                                      int targetEntityId,
                                                                      Set<Integer> excludedEntityIds,
                                                                      String entityTypeFilter) {
        List<ResolvedAttackTarget> candidates = new ArrayList<>();
        Set<Integer> seenEntityIds = new LinkedHashSet<>();
        seenEntityIds.addAll(excludedEntityIds);

        if (entityTypeFilter != null && !entityTypeFilter.isBlank()) {
            maid.level().getEntities(
                    maid,
                    maid.getBoundingBox().inflate(AUTO_TARGET_RADIUS),
                    entity -> entity instanceof LivingEntity livingEntity
                            && isCandidateValid(maid, attackTask, livingEntity, entityTypeFilter)
            ).stream()
                    .sorted(Comparator.comparingDouble(maid::distanceToSqr))
                    .forEach(entity -> addCandidate(candidates, seenEntityIds, maid, attackTask, (LivingEntity) entity, "group:" + entityTypeFilter, entityTypeFilter));
            return candidates;
        }

        LivingEntity plannerTarget = entityFromId(maid, targetEntityId);
        if (targetEntityId >= 0) {
            addCandidate(candidates, seenEntityIds, maid, attackTask, plannerTarget, "planner");
            return candidates;
        }

        addCandidate(candidates, seenEntityIds, maid, attackTask, maid.getTarget(), "current_target");
        addCandidate(candidates, seenEntityIds, maid, attackTask, maid.getLastHurtByMob(), "maid_last_hurt_by");
        addCandidate(candidates, seenEntityIds, maid, attackTask, maid.getLastHurtMob(), "maid_last_hurt");

        LivingEntity owner = maid.getOwner();
        if (owner != null) {
            addCandidate(candidates, seenEntityIds, maid, attackTask, ownerCrosshairTarget(owner), "owner_crosshair");
            addCandidate(candidates, seenEntityIds, maid, attackTask, owner.getLastHurtMob(), "owner_last_hurt");
            addCandidate(candidates, seenEntityIds, maid, attackTask, owner.getLastHurtByMob(), "owner_last_hurt_by");
        }

        return candidates;
    }

    private static void addCandidate(List<ResolvedAttackTarget> candidates,
                                     Set<Integer> seenEntityIds,
                                     EntityMaid maid,
                                     IAttackTask attackTask,
                                     LivingEntity entity,
                                     String source) {
        addCandidate(candidates, seenEntityIds, maid, attackTask, entity, source, null);
    }

    private static void addCandidate(List<ResolvedAttackTarget> candidates,
                                     Set<Integer> seenEntityIds,
                                     EntityMaid maid,
                                     IAttackTask attackTask,
                                     LivingEntity entity,
                                     String source,
                                     String entityTypeFilter) {
        if (!isCandidateValid(maid, attackTask, entity, entityTypeFilter)) {
            return;
        }
        if (!seenEntityIds.add(entity.getId())) {
            return;
        }
        candidates.add(new ResolvedAttackTarget(entity, source));
    }

    private static boolean isCandidateValid(EntityMaid maid, IAttackTask attackTask, LivingEntity entity) {
        return isCandidateValid(maid, attackTask, entity, null);
    }

    private static boolean isCandidateValid(EntityMaid maid, IAttackTask attackTask, LivingEntity entity, String entityTypeFilter) {
        return entity != null
                && entity.isAlive()
                && entity != maid
                && entity != maid.getOwner()
                && maid.distanceTo(entity) <= AUTO_TARGET_RADIUS
                && maid.isWithinRestriction(entity.blockPosition())
                && matchesEntityType(entity, entityTypeFilter)
                && attackTask.canAttack(maid, entity);
    }

    private static boolean isPlannedTargetStillValid(EntityMaid maid, IAttackTask attackTask, LivingEntity entity) {
        if (!isLockedTargetStillValid(maid, attackTask, entity)) {
            return false;
        }
        AttackPlanState plan = ATTACK_PLANS.get(maid.getUUID());
        return plan == null || !plan.isCurrentTargetTimedOut();
    }

    /**
     * 判断“已经锁定”的目标是否仍然有效。
     * <p>
     * 与普通候选筛选不同，这里放宽了距离限制，
     * 只要目标还活着、还能攻击、没有超出持锁半径，就继续追。
     */
    private static boolean isLockedTargetStillValid(EntityMaid maid, IAttackTask attackTask, LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && entity != maid
                && entity != maid.getOwner()
                && maid.distanceTo(entity) <= LOCKED_TARGET_KEEP_RADIUS
                && maid.isWithinRestriction(entity.blockPosition())
                && attackTask.canAttack(maid, entity);
    }

    private static void ensureCurrentBrainTargetStillValid(EntityMaid maid, IAttackTask attackTask) {
        LivingEntity currentTarget = maid.getTarget();
        if (currentTarget == null) {
            currentTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        }
        if (isCandidateValid(maid, attackTask, currentTarget)) {
            ensureAttackTargetMemory(maid, currentTarget);
            return;
        }
        clearAttackState(maid);
    }

    private static void syncAttackTarget(EntityMaid maid, LivingEntity target) {
        maid.setTarget(target);
        maid.setLastHurtByMob(target);
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    }

    /**
     * 当当前计划目标仍然有效时，只保证 ATTACK_TARGET 存在，
     * 不反复重写寻路和朝向内存，避免干扰原版近战行为。
     */
    private static void ensureAttackTargetMemory(EntityMaid maid, LivingEntity target) {
        LivingEntity currentBrainTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (currentBrainTarget == target && maid.getTarget() == target) {
            return;
        }
        syncAttackTarget(maid, target);
    }

    private static LivingEntity entityFromId(EntityMaid maid, int targetEntityId) {
        if (targetEntityId < 0) {
            return null;
        }
        Entity entity = maid.level().getEntity(targetEntityId);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    /**
     * 归一化群体战斗目标类型。
     * <p>
     * 优先使用显式传入的类型；如果为空且给了 target_entity_id，则退回该实体的实际类型。
     */
    private static String normalizeCombatGroupType(EntityMaid maid, String entityTypeId, int targetEntityId) {
        if (entityTypeId != null && !entityTypeId.isBlank()) {
            return entityTypeId.trim().toLowerCase(Locale.ROOT);
        }
        LivingEntity target = entityFromId(maid, targetEntityId);
        if (target == null) {
            return "";
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return key == null ? "" : key.toString();
    }

    /**
     * 判断实体是否匹配指定实体类型。
     */
    private static boolean matchesEntityType(LivingEntity entity, String entityTypeFilter) {
        if (entityTypeFilter == null || entityTypeFilter.isBlank()) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && entityTypeFilter.equalsIgnoreCase(key.toString());
    }

    private static LivingEntity ownerCrosshairTarget(LivingEntity owner) {
        HitResult hitResult = owner.pick(24.0d, 1.0f, false);
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        Entity entity = entityHitResult.getEntity();
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    private static void clearAttackState(EntityMaid maid) {
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static void discardAttackPlan(EntityMaid maid) {
        ATTACK_PLANS.remove(maid.getUUID());
        clearAttackState(maid);
    }

    private static String findDefaultAttackTaskId() {
        Optional<IMaidTask> globalAttackTask = TaskManager.findTask(MaidSoulGlobalAttackTask.UID);
        if (globalAttackTask.isPresent()) {
            return MaidSoulGlobalAttackTask.UID.toString();
        }
        List<IMaidTask> tasks = TaskManager.getTaskIndex();
        for (IMaidTask task : tasks) {
            if (task instanceof IAttackTask) {
                return task.getUid().toString();
            }
        }
        return "touhou_little_maid:attack";
    }

    /**
     * 为当前锁定目标发送一个轻量粒子圈，方便直接在游戏里确认“这只就是当前目标”。
     */
    private static void emitLockEffect(EntityMaid maid, LivingEntity target) {
        if (!(maid.level() instanceof ServerLevel serverLevel) || target == null || !target.isAlive()) {
            return;
        }
        if (maid.tickCount % TARGET_LOCK_EFFECT_INTERVAL_TICKS != 0) {
            return;
        }
        double y = target.getY() + target.getBbHeight() + 0.25d;
        serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 6, 0.25d, 0.08d, 0.25d, 0.01d);
    }

    /**
     * 周期性打印当前锁定状态，方便你判断是否一直锁着同一个实体。
     */
    private static void traceLockedTarget(EntityMaid maid, AttackPlanState plan, LivingEntity target) {
        if (target == null || maid.tickCount % TARGET_LOCK_TRACE_INTERVAL_TICKS != 0) {
            return;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.attack.plan.tick",
                "target=" + target.getName().getString() + "#" + target.getId()
                        + ", queued=" + plan.remainingCount()
                        + ", timed_out=" + plan.isCurrentTargetTimedOut()
        );
    }

    /**
     * 单个候选目标记录。
     */
    private record ResolvedAttackTarget(LivingEntity target, String source) {
    }

    /**
     * 女仆当前的攻击计划状态。
     */
    private static final class AttackPlanState {
        private final List<AttackPlanEntry> entries = new ArrayList<>();
        private int currentIndex = -1;
        private int currentTargetId = -1;
        private long lockStartMillis = 0L;

        private AttackPlanState(List<ResolvedAttackTarget> candidates) {
            replaceCandidates(candidates);
        }

        private void replaceCandidates(List<ResolvedAttackTarget> candidates) {
            this.entries.clear();
            for (ResolvedAttackTarget candidate : candidates) {
                this.entries.add(new AttackPlanEntry(candidate.target().getId(), candidate.source()));
            }
            this.currentIndex = -1;
            this.currentTargetId = -1;
            this.lockStartMillis = 0L;
        }

        private void lock(int entityId) {
            for (int i = 0; i < this.entries.size(); i++) {
                if (this.entries.get(i).entityId() == entityId) {
                    this.currentIndex = i;
                    this.currentTargetId = entityId;
                    this.lockStartMillis = System.currentTimeMillis();
                    return;
                }
            }
            this.currentIndex = -1;
            this.currentTargetId = entityId;
            this.lockStartMillis = System.currentTimeMillis();
        }

        private int currentTargetId() {
            return currentTargetId;
        }

        private boolean isCurrentTargetTimedOut() {
            return currentTargetId >= 0 && System.currentTimeMillis() - lockStartMillis >= TARGET_LOCK_TIMEOUT_MILLIS;
        }

        private int remainingCount() {
            return Math.max(0, this.entries.size() - Math.max(currentIndex, 0));
        }

        private ResolvedAttackTarget nextValidTarget(EntityMaid maid, IAttackTask attackTask) {
            for (int i = currentIndex + 1; i < entries.size(); i++) {
                AttackPlanEntry entry = entries.get(i);
                LivingEntity entity = entityFromId(maid, entry.entityId());
                if (isCandidateValid(maid, attackTask, entity)) {
                    currentIndex = i;
                    return new ResolvedAttackTarget(entity, entry.source());
                }
            }
            return null;
        }
    }

    /**
     * 攻击计划中的静态候选项。
     */
    private record AttackPlanEntry(int entityId, String source) {
    }
}
