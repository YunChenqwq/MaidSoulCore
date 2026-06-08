package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.maidsoulcore.forge.execution.MaidSoulCombatTaskDriver;
import com.maidsoulcore.forge.execution.MaidSoulExecutionSession;
import com.maidsoulcore.forge.execution.MaidSoulExecutionStatus;
import com.maidsoulcore.forge.execution.MaidSoulGroupAttackTaskDriver;
import com.maidsoulcore.forge.execution.MaidSoulSingleAttackTaskDriver;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.forge.task.MaidSoulGlobalAttackTask;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.tags.TagKey;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 战斗执行控制器。
 * <p>
 * 这是 `MaidSoulCore` 首版执行层的落地入口：
 * - 聊天层和 planner 只提交“单体攻击”或“群体攻击”的目标
 * - 执行控制器负责创建会话
 * - driver 在每 tick 中持续推进当前战斗
 * <p>
 * 首版仍复用 TLM 的近战任务与攻击记忆，
 * 但把“锁谁、何时切目标、何时超时结束”从聊天链路里拆了出来。
 */
public final class MaidSoulCombatExecutionController {
    private static final double AUTO_TARGET_RADIUS = 32.0d;
    private static final double LOCKED_TARGET_KEEP_RADIUS = 48.0d;
    private static final long TARGET_LOCK_TIMEOUT_MILLIS = 15_000L;
    private static final int TARGET_LOCK_EFFECT_INTERVAL_TICKS = 10;
    private static final int TARGET_LOCK_TRACE_INTERVAL_TICKS = 20;

    private static final AtomicLong SESSION_IDS = new AtomicLong(1L);
    private static final ConcurrentMap<UUID, MaidSoulExecutionSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, MaidSoulCombatTaskDriver> DRIVERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, CombatExecutionResult> FINISHED_RESULTS = new ConcurrentHashMap<>();

    static {
        registerDriver(new MaidSoulSingleAttackTaskDriver());
        registerDriver(new MaidSoulGroupAttackTaskDriver());
    }

    private MaidSoulCombatExecutionController() {
    }

    /**
     * 注册战斗驱动器。
     */
    private static void registerDriver(MaidSoulCombatTaskDriver driver) {
        DRIVERS.put(driver.id(), driver);
    }

    /**
     * 每 tick 推进当前战斗会话。
     */
    public static void onMaidTick(EntityMaid maid) {
        IMaidTask task = maid.getTask();
        if (!(task instanceof IAttackTask attackTask)) {
            if (SESSIONS.containsKey(maid.getUUID())) {
                clearSession(maid, "task_not_attack");
            }
            return;
        }
        MaidSoulExecutionSession session = SESSIONS.get(maid.getUUID());
        if (session == null) {
            if (isExecutionOwnedAttackTask(task)) {
                clearAttackState(maid);
                return;
            }
            ensureCurrentBrainTargetStillValid(maid, attackTask);
            return;
        }
        MaidSoulCombatTaskDriver driver = DRIVERS.get(session.driverId());
        if (driver == null) {
            clearSession(maid, "missing_driver:" + session.driverId());
            return;
        }
        driver.tick(maid, attackTask, session);
        if (session.status() == MaidSoulExecutionStatus.RUNNING) {
            return;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.execution.combat.finish",
                "driver=" + session.driverId() + ", result=" + session.lastResult() + ", status=" + session.status()
        );
        FINISHED_RESULTS.put(
                maid.getUUID(),
                new CombatExecutionResult(session.sessionId(), session.driverId(), session.status(), session.lastResult())
        );
        SESSIONS.remove(maid.getUUID());
    }

    /**
     * 创建单目标攻击会话。
     */
    public static String startSingle(EntityMaid maid, String taskId, int targetEntityId) {
        IMaidTask task = maid.getTask();
        if (!(task instanceof IAttackTask attackTask)) {
            return "attack_task_unavailable";
        }
        List<MaidSoulExecutionSession.TargetEntry> entries = collectSingleTargets(maid, attackTask, targetEntityId);
        MaidSoulExecutionSession session = new MaidSoulExecutionSession(
                SESSION_IDS.getAndIncrement(),
                taskId,
                MaidSoulSingleAttackTaskDriver.ID,
                null,
                entries
        );
        SESSIONS.put(maid.getUUID(), session);
        String result = DRIVERS.get(session.driverId()).initialize(maid, attackTask, session);
        if (session.status() != MaidSoulExecutionStatus.RUNNING) {
            SESSIONS.remove(maid.getUUID());
            clearAttackState(maid);
        }
        return result;
    }

    /**
     * 创建群体攻击会话。
     */
    public static String startGroup(EntityMaid maid, String taskId, String entityTypeId, int targetEntityId) {
        IMaidTask task = maid.getTask();
        if (!(task instanceof IAttackTask attackTask)) {
            return "attack_task_unavailable";
        }
        String normalizedFilter = normalizeCombatGroupFilter(maid, entityTypeId, targetEntityId);
        if (normalizedFilter == null || normalizedFilter.isBlank()) {
            return "group_type_missing";
        }
        List<MaidSoulExecutionSession.TargetEntry> entries = collectGroupTargets(maid, attackTask, normalizedFilter, targetEntityId);
        MaidSoulExecutionSession session = new MaidSoulExecutionSession(
                SESSION_IDS.getAndIncrement(),
                taskId,
                MaidSoulGroupAttackTaskDriver.ID,
                normalizedFilter,
                entries
        );
        SESSIONS.put(maid.getUUID(), session);
        String result = DRIVERS.get(session.driverId()).initialize(maid, attackTask, session);
        if (session.status() != MaidSoulExecutionStatus.RUNNING) {
            SESSIONS.remove(maid.getUUID());
            clearAttackState(maid);
        }
        return result;
    }

    /**
     * 返回当前是否仍存在运行中的战斗会话。
     */
    public static boolean hasActiveSession(EntityMaid maid) {
        MaidSoulExecutionSession session = SESSIONS.get(maid.getUUID());
        return session != null && session.status() == MaidSoulExecutionStatus.RUNNING;
    }

    /**
     * 取走最近一次结束的战斗执行结果。
     */
    public static CombatExecutionResult consumeFinishedResult(EntityMaid maid) {
        return FINISHED_RESULTS.remove(maid.getUUID());
    }

    /**
     * 主动取消当前战斗执行会话。
     */
    public static void cancelActiveSession(EntityMaid maid, String reason) {
        clearSession(maid, reason == null || reason.isBlank() ? "cancelled" : reason);
    }

    /**
     * 输出战斗执行摘要。
     */
    public static String describeSession(EntityMaid maid) {
        MaidSoulExecutionSession session = SESSIONS.get(maid.getUUID());
        LivingEntity currentTarget = maid.getTarget();
        LivingEntity memoryTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (session == null && currentTarget == null && memoryTarget == null) {
            return "no active combat session";
        }
        String current = currentTarget == null ? "none" : currentTarget.getName().getString() + "#" + currentTarget.getId();
        String memory = memoryTarget == null ? "none" : memoryTarget.getName().getString() + "#" + memoryTarget.getId();
        if (session == null) {
            return "combat target=" + current + ", memory=" + memory + ", queued=0";
        }
        return "session=" + session.sessionId()
                + ", driver=" + session.driverId()
                + ", status=" + session.status()
                + ", target=" + current
                + ", memory=" + memory
                + ", queued=" + session.remainingCount()
                + ", locked=" + session.currentTargetId()
                + ", timed_out=" + session.isCurrentTargetTimedOut(TARGET_LOCK_TIMEOUT_MILLIS);
    }

    /**
     * 返回默认攻击任务 id。
     */
    public static String findDefaultAttackTaskId() {
        Optional<IMaidTask> globalAttackTask = TaskManager.findTask(MaidSoulGlobalAttackTask.UID);
        if (globalAttackTask.isPresent()) {
            return MaidSoulGlobalAttackTask.UID.toString();
        }
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            if (task instanceof IAttackTask) {
                return task.getUid().toString();
            }
        }
        return "touhou_little_maid:attack";
    }

    /**
     * 供 driver 查询实体。
     */
    public static LivingEntity entityFromId(EntityMaid maid, int targetEntityId) {
        if (targetEntityId < 0) {
            return null;
        }
        Entity entity = maid.level().getEntity(targetEntityId);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    /**
     * 判断候选目标是否有效。
     */
    public static boolean isCandidateValid(EntityMaid maid, IAttackTask attackTask, LivingEntity entity, String filterSpec) {
        return entity != null
                && entity.isAlive()
                && entity != maid
                && entity != maid.getOwner()
                && maid.distanceTo(entity) <= AUTO_TARGET_RADIUS
                && maid.isWithinRestriction(entity.blockPosition())
                && matchesTargetFilter(entity, filterSpec)
                && attackTask.canAttack(maid, entity);
    }

    /**
     * 判断已锁定目标是否仍可继续追击。
     */
    public static boolean isLockedTargetStillValid(EntityMaid maid, IAttackTask attackTask, LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && entity != maid
                && entity != maid.getOwner()
                && maid.distanceTo(entity) <= LOCKED_TARGET_KEEP_RADIUS
                && maid.isWithinRestriction(entity.blockPosition())
                && attackTask.canAttack(maid, entity);
    }

    /**
     * 同步攻击目标到 TLM 脑内存。
     */
    public static void syncAttackTarget(EntityMaid maid, LivingEntity target) {
        maid.setLastHurtByMob(target);
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    }

    /**
     * 确保攻击目标内存存在，但避免反复重置。
     */
    public static void ensureAttackTargetMemory(EntityMaid maid, LivingEntity target) {
        LivingEntity currentBrainTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (currentBrainTarget == target && maid.getTarget() == target) {
            return;
        }
        syncAttackTarget(maid, target);
    }

    /**
     * 清空当前攻击相关内存。
     */
    public static void clearAttackState(EntityMaid maid) {
        maid.setLastHurtByMob(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    /**
     * 为锁定目标发送粒子提示。
     */
    public static void emitLockEffect(EntityMaid maid, LivingEntity target) {
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
     * 周期性输出锁定目标 trace。
     */
    public static void traceLockedTarget(EntityMaid maid, MaidSoulExecutionSession session, LivingEntity target) {
        if (target == null || maid.tickCount % TARGET_LOCK_TRACE_INTERVAL_TICKS != 0) {
            return;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.execution.combat.tick",
                "target=" + target.getName().getString() + "#" + target.getId()
                        + ", queued=" + session.remainingCount()
                        + ", timed_out=" + session.isCurrentTargetTimedOut(TARGET_LOCK_TIMEOUT_MILLIS)
        );
    }

    /**
     * 返回锁定超时阈值。
     */
    public static long targetLockTimeoutMillis() {
        return TARGET_LOCK_TIMEOUT_MILLIS;
    }

    private static void ensureCurrentBrainTargetStillValid(EntityMaid maid, IAttackTask attackTask) {
        LivingEntity currentTarget = maid.getTarget();
        if (currentTarget == null) {
            currentTarget = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        }
        if (isCandidateValid(maid, attackTask, currentTarget, null)) {
            ensureAttackTargetMemory(maid, currentTarget);
            return;
        }
        clearAttackState(maid);
    }

    private static void clearSession(EntityMaid maid, String reason) {
        MaidSoulExecutionSession removed = SESSIONS.remove(maid.getUUID());
        if (removed != null) {
            removed.status(MaidSoulExecutionStatus.CANCELLED);
            removed.lastResult(reason);
            FINISHED_RESULTS.put(
                    maid.getUUID(),
                    new CombatExecutionResult(removed.sessionId(), removed.driverId(), removed.status(), removed.lastResult())
            );
            clearAttackState(maid);
        }
    }

    private static List<MaidSoulExecutionSession.TargetEntry> collectSingleTargets(EntityMaid maid, IAttackTask attackTask, int targetEntityId) {
        List<MaidSoulExecutionSession.TargetEntry> entries = new ArrayList<>();
        Set<Integer> seenEntityIds = new LinkedHashSet<>();

        if (targetEntityId >= 0) {
            LivingEntity plannerTarget = entityFromId(maid, targetEntityId);
            addTargetEntry(entries, seenEntityIds, maid, attackTask, plannerTarget, "planner", null);
            return entries;
        }

        addTargetEntry(entries, seenEntityIds, maid, attackTask, maid.getTarget(), "current_target", null);
        addTargetEntry(entries, seenEntityIds, maid, attackTask, maid.getLastHurtByMob(), "maid_last_hurt_by", null);
        addTargetEntry(entries, seenEntityIds, maid, attackTask, maid.getLastHurtMob(), "maid_last_hurt", null);
        LivingEntity owner = maid.getOwner();
        if (owner != null) {
            addTargetEntry(entries, seenEntityIds, maid, attackTask, ownerCrosshairTarget(owner), "owner_crosshair", null);
            addTargetEntry(entries, seenEntityIds, maid, attackTask, owner.getLastHurtMob(), "owner_last_hurt", null);
            addTargetEntry(entries, seenEntityIds, maid, attackTask, owner.getLastHurtByMob(), "owner_last_hurt_by", null);
        }
        return entries;
    }

    private static List<MaidSoulExecutionSession.TargetEntry> collectGroupTargets(EntityMaid maid,
                                                                                  IAttackTask attackTask,
                                                                                  String filterSpec,
                                                                                  int preferredTargetEntityId) {
        List<MaidSoulExecutionSession.TargetEntry> entries = new ArrayList<>();
        Set<Integer> seenEntityIds = new LinkedHashSet<>();
        maid.level().getEntities(
                        maid,
                        maid.getBoundingBox().inflate(AUTO_TARGET_RADIUS),
                        entity -> entity instanceof LivingEntity livingEntity
                                && isCandidateValid(maid, attackTask, livingEntity, filterSpec)
                ).stream()
                .sorted(Comparator
                        .<Entity>comparingInt(entity -> entity.getId() == preferredTargetEntityId ? 0 : 1)
                        .thenComparingDouble(maid::distanceToSqr))
                .forEach(entity -> addTargetEntry(entries, seenEntityIds, maid, attackTask, (LivingEntity) entity, "group:" + filterSpec, filterSpec));
        return entries;
    }

    private static void addTargetEntry(List<MaidSoulExecutionSession.TargetEntry> entries,
                                       Set<Integer> seenEntityIds,
                                       EntityMaid maid,
                                       IAttackTask attackTask,
                                       LivingEntity entity,
                                       String source,
                                       String filterSpec) {
        if (!isCandidateValid(maid, attackTask, entity, filterSpec)) {
            return;
        }
        if (!seenEntityIds.add(entity.getId())) {
            return;
        }
        entries.add(new MaidSoulExecutionSession.TargetEntry(entity.getId(), source));
    }

    private static String normalizeCombatGroupFilter(EntityMaid maid, String filterSpec, int targetEntityId) {
        if (filterSpec != null && !filterSpec.isBlank()) {
            return normalizeFilterSpec(filterSpec);
        }
        LivingEntity target = entityFromId(maid, targetEntityId);
        if (target == null) {
            return "";
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return key == null ? "" : "type=" + key;
    }

    private static boolean matchesTargetFilter(LivingEntity entity, String filterSpec) {
        if (filterSpec == null || filterSpec.isBlank()) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return false;
        }
        CombatTargetFilter filter = CombatTargetFilter.parse(filterSpec);
        if (!filter.typeId().isBlank() && !filter.typeId().equalsIgnoreCase(key.toString())) {
            return false;
        }
        if (filter.tagId().isBlank()) {
            return true;
        }
        ResourceLocation tagKey = ResourceLocation.tryParse(filter.tagId());
        if (tagKey == null) {
            return false;
        }
        return entity.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tagKey));
    }

    private static LivingEntity ownerCrosshairTarget(LivingEntity owner) {
        HitResult hitResult = owner.pick(24.0d, 1.0f, false);
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        Entity entity = entityHitResult.getEntity();
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    /**
     * `global_attack` 只作为执行层专用攻击任务存在。
     * 没有执行会话时，必须强制清空攻击记忆，避免女仆自行扫到附近实体后开始无差别攻击。
     */
    private static boolean isExecutionOwnedAttackTask(IMaidTask task) {
        ResourceLocation uid = task.getUid();
        return uid != null && uid.equals(MaidSoulGlobalAttackTask.UID);
    }

    private static String normalizeFilterSpec(String raw) {
        CombatTargetFilter filter = CombatTargetFilter.parse(raw);
        if (filter.isEmpty()) {
            String trimmed = raw.trim().toLowerCase(Locale.ROOT);
            return trimmed.startsWith("#") ? "tag=" + trimmed.substring(1) : "type=" + trimmed;
        }
        StringBuilder builder = new StringBuilder();
        if (!filter.typeId().isBlank()) {
            builder.append("type=").append(filter.typeId().toLowerCase(Locale.ROOT));
        }
        if (!filter.tagId().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append("tag=").append(filter.tagId().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    public static boolean isValidGroupFilterSpec(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        CombatTargetFilter filter = CombatTargetFilter.parse(raw);
        if (filter.isEmpty()) {
            ResourceLocation directType = ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
            return directType != null;
        }
        if (!filter.typeId().isBlank() && ResourceLocation.tryParse(filter.typeId()) == null) {
            return false;
        }
        if (!filter.tagId().isBlank() && ResourceLocation.tryParse(filter.tagId()) == null) {
            return false;
        }
        return true;
    }

    /**
     * 群体攻击过滤表达：
     * - `minecraft:zombie`
     * - `#forge:skeletons`
     * - `type=minecraft:zombie`
     * - `tag=forge:skeletons`
     * - `type=minecraft:zombie;tag=forge:undead`
     */
    private record CombatTargetFilter(String typeId, String tagId) {
        static CombatTargetFilter parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new CombatTargetFilter("", "");
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("#")) {
                return new CombatTargetFilter("", normalized.substring(1));
            }
            if (!normalized.contains("=") && !normalized.contains(";")) {
                return new CombatTargetFilter(normalized, "");
            }
            String typeId = "";
            String tagId = "";
            String[] segments = normalized.split(";");
            for (String segment : segments) {
                String[] parts = segment.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String key = parts[0].trim();
                String value = parts[1].trim();
                if ("type".equals(key)) {
                    typeId = value;
                } else if ("tag".equals(key)) {
                    tagId = value.startsWith("#") ? value.substring(1) : value;
                }
            }
            return new CombatTargetFilter(typeId, tagId);
        }

        boolean isEmpty() {
            return typeId.isBlank() && tagId.isBlank();
        }
    }

    /**
     * 战斗执行结束快照。
     */
    public record CombatExecutionResult(long sessionId, String driverId, MaidSoulExecutionStatus status, String result) {
    }
}
