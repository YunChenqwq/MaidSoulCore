package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quantified short-term emotion plus relation-aware affect.
 */
public final class MaidSoulEmotionService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("amount=([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern ATTACKER_UUID_PATTERN = Pattern.compile("attackerUuid=([0-9a-fA-F\\-]{36})");
    private static final ConcurrentMap<UUID, EmotionState> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Deque<String>> RECENT_IMMEDIATE_LINES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> IMMEDIATE_LINE_INDEX = new ConcurrentHashMap<>();
    private static final int MAX_RECENT_IMMEDIATE_LINES = 6;

    private MaidSoulEmotionService() {
    }

    public static void onTick(EntityMaid maid) {
        if (maid == null || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return;
        }
        EmotionState state = stateOf(maid);
        synchronized (state) {
            syncOwnerRelation(maid, state);
            observeDayAndSleepRecovery(maid, state);
            if (maid.tickCount % Math.max(20, MaidSoulCommonConfig.EMOTION_RECOVERY_INTERVAL_TICKS.get()) == 0) {
                recoverShortTerm(state);
            }
            if (state.unresolvedUntilMillis > 0L && System.currentTimeMillis() > state.unresolvedUntilMillis) {
                state.unresolvedEvent = "";
                state.unresolvedDetail = "";
                state.unresolvedNeed = "";
                state.unresolvedUntilMillis = 0L;
                selectReaction(state, "settled", "unresolved topic expired");
            }
        }
    }

    public static void observeEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        if (maid == null || eventType == null || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return;
        }
        EmotionState state = stateOf(maid);
        String reason;
        synchronized (state) {
            PersonaProfile persona = PersonaProfile.fromConfig();
            RelationState relation = relationFor(maid, state, eventType, detail);
            float amount = parseAmount(detail);
            syncOwnerRelation(maid, state);

            if ("maid.attacked.by_owner".equals(eventType)) {
                int familiarity = Math.max(relation.familiarity, relation.affection);
                int hurtScale = scaleBy(persona.sensitivity, 65, 145);
                int attachmentConfusion = scaled(persona.attachment + familiarity, 0.16f);
                int angerGain = scaled(persona.pride + persona.angerExpressiveness + relation.grudge, 0.12f);
                int fearGain = scaled(persona.fearfulness + Math.max(0, 55 - familiarity), 0.09f);
                int trustLoss = scaled(MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_TRUST_DELTA.get(), hurtScale / 100.0f)
                        + scaled(Math.max(0, 65 - persona.forgiveness), 0.08f);
                int moodLoss = scaled(MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_MOOD_DELTA.get() + Math.round(amount), hurtScale / 100.0f);

                adjust(state, -moodLoss, -trustLoss, 18 + Math.round(amount), 10 + Math.round(amount),
                        fearGain, angerGain, attachmentConfusion, -scaled(persona.boundaryStrength, 0.04f));
                relation.trust = clamp(relation.trust - trustLoss);
                relation.security = clamp(relation.security - fearGain - scaled(persona.boundaryStrength, 0.05f));
                relation.grudge = clamp(relation.grudge + scaled(100 - persona.forgiveness + persona.pride, 0.08f) + relation.recentOffenseCount);
                relation.recentOffenseCount = clamp(relation.recentOffenseCount + 1);
                markUnresolved(state, eventType, detail, unresolvedSeconds(persona, 1.15f), "apology_or_comfort");
                reason = "owner hurt maid; relation and persona convert hurt into confusion, anger, or fear";
            } else if ("maid.attacked".equals(eventType)) {
                int fearGain = 12 + Math.round(amount) + scaled(persona.fearfulness, 0.16f);
                int angerGain = 6 + scaled(persona.pride + persona.angerExpressiveness, 0.08f);
                adjust(state, -MaidSoulCommonConfig.EMOTION_HOSTILE_ATTACK_MOOD_DELTA.get(), 0, 20 + Math.round(amount),
                        12 + Math.round(amount), fearGain, angerGain, 0, -fearGain);
                relation.security = clamp(relation.security - fearGain);
                relation.grudge = clamp(relation.grudge + 12 + Math.round(amount));
                relation.recentOffenseCount = clamp(relation.recentOffenseCount + 1);
                markUnresolved(state, eventType, detail, unresolvedSeconds(persona, 0.72f), "reassurance_and_safety");
                reason = "maid was hurt by danger; safety need is active";
            } else if ("maid.death".equals(eventType)) {
                adjust(state, -35, -8, 45, 40, 35, 20, 18, -30);
                markUnresolved(state, eventType, detail, unresolvedSeconds(persona, 2.0f), "urgent_care_and_reassurance");
                reason = "death event";
            } else if ("maid.ate".equals(eventType) || "maid.interact".equals(eventType)) {
                adjust(state, MaidSoulCommonConfig.EMOTION_POSITIVE_EVENT_MOOD_DELTA.get(), 1, -4, -6, -2, -2, -1, 2);
                relation.familiarity = clamp(relation.familiarity + 1);
                relation.security = clamp(relation.security + 2);
                reason = "small positive care event";
            } else if (eventType.contains("failed") || eventType.contains("missing") || eventType.contains("not_allowed")) {
                adjust(state, -5, 0, 12, 0, 2, 0, 6, -2);
                markUnresolved(state, eventType, detail, unresolvedSeconds(persona, 0.45f), "acknowledge_failure");
                reason = "failed action needs acknowledgement";
            } else if (priority == EventPriority.P0) {
                adjust(state, -8, 0, 14, 8, 8, 4, 4, -6);
                markUnresolved(state, eventType, detail, 12, "safety_check");
                reason = "critical event";
            } else {
                reason = "neutral event";
            }
            state.dominantEmotion = dominantEmotion(state);
            selectReaction(state, chooseAction(state, eventType, relation, persona), reason);
            state.lastActorRole = relation.role;
            state.lastActorKey = relation.actorKey;
            state.lastUpdatedMillis = System.currentTimeMillis();
        }
        trace(maid);
    }

    public static void observeOwnerMessage(EntityMaid maid, String message) {
        if (maid == null || message == null || message.isBlank() || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return;
        }
        EmotionState state = stateOf(maid);
        synchronized (state) {
            /*
             * 这里曾经使用 containsAny(...) 直接从玩家文本里猜“道歉/辱骂/夸奖”。
             * 这种做法会把情绪系统变成关键词表：既不稳，也会和新的 planner 语义链路打架。
             *
             * 现在旧 Forge 情绪服务只接收“主人确实发来了一条消息”这个低层事实，
             * 不再从自然语言里硬编码判断关系事件。真正的 apology / affection / fight
             * 等语义事件必须由 planner 或工具循环输出结构化 affect_event，再交给新情绪层消费。
             */
            RelationState owner = ownerRelation(maid, state);
            syncOwnerRelation(maid, state);
            owner.familiarity = clamp(owner.familiarity + 1);
            state.lastActorRole = "owner";
            state.lastActorKey = owner.actorKey;
            state.lastUpdatedMillis = System.currentTimeMillis();
        }
    }

    public static String promptBlock(EntityMaid maid) {
        EmotionState state = STATES.get(maid.getUUID());
        if (state == null || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return "emotion_system=disabled_or_neutral";
        }
        synchronized (state) {
            PersonaProfile persona = PersonaProfile.fromConfig();
            RelationState owner = ownerRelation(maid, state);
            syncOwnerRelation(maid, state);
            return """
                    mood=%d/100
                    trust=%d/100
                    stress=%d/100
                    pain=%d/100
                    fear=%d/100
                    anger=%d/100
                    confusion=%d/100
                    security=%d/100
                    dominant_emotion=%s
                    reaction_action=%s
                    reaction_reason=%s
                    unresolved_topic=%s
                    unresolved_need=%s
                    owner_relation=%s
                    persona=%s
                    guidance=%s
                    """.formatted(
                    state.mood,
                    state.trust,
                    state.stress,
                    state.pain,
                    state.fear,
                    state.anger,
                    state.confusion,
                    state.security,
                    blankToDefault(state.dominantEmotion, "neutral"),
                    blankToDefault(state.reactionAction, "neutral"),
                    blankToDefault(state.reactionReason, "none"),
                    unresolvedSummary(state),
                    blankToDefault(state.unresolvedNeed, "none"),
                    owner.promptSummary(),
                    persona.promptSummary(),
                    guidanceFor(state, persona)
            );
        }
    }

    public static String debugSummary(EntityMaid maid) {
        EmotionState state = STATES.get(maid.getUUID());
        if (state == null) {
            return "action=neutral reason=initial mood=70 trust=70 stress=0 pain=0";
        }
        synchronized (state) {
            RelationState owner = ownerRelation(maid, state);
            syncOwnerRelation(maid, state);
            return "action=" + blankToDefault(state.reactionAction, "neutral")
                    + " reason=" + blankToDefault(state.reactionReason, "none")
                    + " dominant=" + blankToDefault(state.dominantEmotion, "neutral")
                    + " mood=" + state.mood
                    + " trust=" + state.trust
                    + " stress=" + state.stress
                    + " pain=" + state.pain
                    + " fear=" + state.fear
                    + " anger=" + state.anger
                    + " confusion=" + state.confusion
                    + " security=" + state.security
                    + " ownerRelation=" + owner.debugSummary()
                    + " lastActor=" + blankToDefault(state.lastActorRole, "none")
                    + " unresolved=" + unresolvedSummary(state)
                    + " need=" + blankToDefault(state.unresolvedNeed, "none");
        }
    }

    public static boolean hasActiveUnresolvedTopic(EntityMaid maid) {
        if (maid == null || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return false;
        }
        EmotionState state = STATES.get(maid.getUUID());
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.unresolvedUntilMillis > System.currentTimeMillis()
                    && state.unresolvedEvent != null
                    && !state.unresolvedEvent.isBlank();
        }
    }

    public static boolean hasActiveOwnerOffenseUnresolved(EntityMaid maid) {
        if (maid == null || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return false;
        }
        EmotionState state = STATES.get(maid.getUUID());
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.unresolvedUntilMillis > System.currentTimeMillis()
                    && ("maid.attacked.by_owner".equals(state.unresolvedEvent)
                    || "owner.harsh_message".equals(state.unresolvedEvent));
        }
    }

    public static String immediateLine(EntityMaid maid, String eventType) {
        if (maid == null || eventType == null || !MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()) {
            return "";
        }
        EmotionState state = STATES.get(maid.getUUID());
        if (state == null) {
            return "";
        }
        String pooledImmediate = immediateAttackLine(maid, eventType, state);
        if (!pooledImmediate.isBlank()) {
            return pooledImmediate;
        }
        synchronized (state) {
            PersonaProfile persona = PersonaProfile.fromConfig();
            if ("maid.attacked.by_owner".equals(eventType)) {
                if (state.anger >= 45 && persona.boundaryStrength >= 55) {
                    return "主人，先停一下。这样不可以。";
                }
                if (state.fear >= 45 || state.security < 35) {
                    return "主人……我有点害怕，先别再打我。";
                }
                if (state.confusion >= 35 || persona.attachment >= 65) {
                    return "主人……为什么突然打我？我还没明白。";
                }
                if (state.pain > 35) {
                    return "呜，疼疼的……主人先别打我，好不好？";
                }
                return "主人轻一点嘛，我会听话的。";
            }
            if ("maid.attacked".equals(eventType)) {
                if (state.pain > 45 || state.stress > 60) {
                    return "我受伤了……主人，先帮我离危险远一点。";
                }
                if (state.fear > state.anger) {
                    return "刚才那个好危险……主人小心。";
                }
                return "我被打到了，主人小心附近的敌人。";
            }
            return "";
        }
    }

    private static String immediateAttackLine(EntityMaid maid, String eventType, EmotionState state) {
        if (!"maid.attacked.by_owner".equals(eventType) && !"maid.attacked".equals(eventType)) {
            return "";
        }
        synchronized (state) {
            PersonaProfile persona = PersonaProfile.fromConfig();
            if ("maid.attacked.by_owner".equals(eventType)) {
                if (state.anger >= 45 && persona.boundaryStrength >= 55) {
                    return pickImmediateLine(maid, "owner_attack.boundary", List.of(
                            "主人，先停一下。这样不可以。",
                            "别再打了，主人。就算是你，我也会疼的。",
                            "我不是故意惹你生气的，可是这样真的不行。",
                            "主人，再这样的话，我会想躲开你的。"
                    ));
                }
                if (state.fear >= 45 || state.security < 35) {
                    return pickImmediateLine(maid, "owner_attack.fear", List.of(
                            "主人……我有点害怕，先别再打我。",
                            "等一下，好不好？我真的被吓到了。",
                            "主人，别靠这么凶……我会怕的。",
                            "我、我在听你说话，所以先把手放下嘛。"
                    ));
                }
                if (state.confusion >= 35 || persona.attachment >= 65) {
                    return pickImmediateLine(maid, "owner_attack.confused", List.of(
                            "主人……为什么突然打我？我还没明白。",
                            "欸？我做错什么了吗？你至少告诉我呀。",
                            "突然这样，我会不知道该怎么办的。",
                            "主人，别只打我嘛。你生气的话，好好说也可以。"
                    ));
                }
                if (state.pain > 35) {
                    return pickImmediateLine(maid, "owner_attack.pain", List.of(
                            "呜，疼的……主人先别打我，好不好？",
                            "好痛……我会听话的，先停一下啦。",
                            "疼疼疼，主人你下手太重了。",
                            "我知道啦，所以别再敲我了嘛。"
                    ));
                }
                return pickImmediateLine(maid, "owner_attack.soft", List.of(
                        "主人轻一点嘛，我会听话的。",
                        "呜……不要拿我撒气啦。",
                        "主人，打招呼也不用这么用力吧。",
                        "我在这里呢，别用打的叫我嘛。"
                ));
            }
            if (state.pain > 45 || state.stress > 60) {
                return pickImmediateLine(maid, "attacked.injured", List.of(
                        "我受伤了……主人，先帮我离危险远一点。",
                        "好痛，附近还有危险，主人小心。",
                        "我还能坚持，但现在不太妙。",
                        "主人，我被打疼了，先别站太近。"
                ));
            }
            if (state.fear > state.anger) {
                return pickImmediateLine(maid, "attacked.fear", List.of(
                        "刚才那个好危险……主人小心。",
                        "主人，附近不安全，我们别大意。",
                        "我被吓了一下，先确认周围吧。",
                        "有东西打到我了，主人注意身后。"
                ));
            }
            return pickImmediateLine(maid, "attacked.alert", List.of(
                    "我被打到了，主人小心附近的敌人。",
                    "有敌人碰到我了，我会注意躲开的。",
                    "唔，受到攻击了，主人也别受伤。",
                    "我没事，但附近可能还有危险。"
            ));
        }
    }

    private static String pickImmediateLine(EntityMaid maid, String poolKey, List<String> lines) {
        if (maid == null || lines == null || lines.isEmpty()) {
            return "";
        }
        Deque<String> recent = RECENT_IMMEDIATE_LINES.computeIfAbsent(maid.getUUID(), id -> new ArrayDeque<>());
        String key = maid.getUUID() + ":" + poolKey;
        int start = Math.floorMod(IMMEDIATE_LINE_INDEX.merge(key, 1, Integer::sum), lines.size());
        for (int offset = 0; offset < lines.size(); offset++) {
            String line = lines.get((start + offset) % lines.size());
            if (line == null || line.isBlank() || recent.contains(line)) {
                continue;
            }
            rememberImmediateLine(recent, line);
            return line;
        }
        String fallback = lines.get(start);
        rememberImmediateLine(recent, fallback);
        return fallback;
    }

    private static void rememberImmediateLine(Deque<String> recent, String line) {
        recent.addLast(line);
        while (recent.size() > MAX_RECENT_IMMEDIATE_LINES) {
            recent.removeFirst();
        }
    }

    private static EmotionState stateOf(EntityMaid maid) {
        return STATES.computeIfAbsent(maid.getUUID(), id -> new EmotionState());
    }

    private static RelationState relationFor(EntityMaid maid, EmotionState state, String eventType, String detail) {
        if ("maid.attacked.by_owner".equals(eventType) || "owner.message".equals(eventType)) {
            return ownerRelation(maid, state);
        }
        UUID actorUuid = parseAttackerUuid(detail);
        String key = actorUuid == null ? "unknown:" + shortText(detail, 32) : actorUuid.toString();
        RelationState relation = state.relations.computeIfAbsent(key, ignored -> new RelationState(key, "stranger"));
        relation.lastSeenMillis = System.currentTimeMillis();
        return relation;
    }

    private static RelationState ownerRelation(EntityMaid maid, EmotionState state) {
        String key = maid.getOwner() == null ? "owner:unknown" : maid.getOwner().getUUID().toString();
        RelationState relation = state.relations.computeIfAbsent(key, ignored -> new RelationState(key, "owner"));
        relation.role = "owner";
        relation.lastSeenMillis = System.currentTimeMillis();
        relation.affectionRaw = safeFavorability(maid);
        relation.affectionLevel = safeFavorabilityLevel(maid);
        relation.affection = clamp(Math.max(relation.affectionLevel * 20, relation.affectionRaw));
        relation.familiarity = Math.max(relation.familiarity, clamp(35 + relation.affection / 2));
        return relation;
    }

    private static void syncOwnerRelation(EntityMaid maid, EmotionState state) {
        ownerRelation(maid, state);
    }

    private static void adjust(EmotionState state, int mood, int trust, int stress, int pain, int fear, int anger, int confusion, int security) {
        state.mood = clamp(state.mood + mood);
        state.trust = clamp(state.trust + trust);
        state.stress = clamp(state.stress + stress);
        state.pain = clamp(state.pain + pain);
        state.fear = clamp(state.fear + fear);
        state.anger = clamp(state.anger + anger);
        state.confusion = clamp(state.confusion + confusion);
        state.security = clamp(state.security + security);
    }

    private static void observeDayAndSleepRecovery(EntityMaid maid, EmotionState state) {
        long day = maid.level().getDayTime() / 24000L;
        if (state.lastObservedDay < 0L) {
            state.lastObservedDay = day;
        } else if (day > state.lastObservedDay) {
            state.lastObservedDay = day;
            if (MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_ENABLED.get()) {
                recoverByPercent(state, MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_PERCENT.get(), MaidSoulCommonConfig.EMOTION_DAY_GRUDGE_RECOVERY_STEP.get(), "new day partial recovery");
            }
        }

        boolean sleeping = maid.isSleeping();
        if (sleeping && !state.wasSleeping) {
            state.sleepStartTick = maid.tickCount;
        } else if (!sleeping && state.wasSleeping) {
            int sleptTicks = Math.max(0, maid.tickCount - state.sleepStartTick);
            if (sleptTicks >= MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_MIN_TICKS.get()) {
                recoverByPercent(state, MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_PERCENT.get(), MaidSoulCommonConfig.EMOTION_SLEEP_GRUDGE_RECOVERY_STEP.get(), "sleep partial recovery");
            }
            state.sleepStartTick = 0;
        }
        state.wasSleeping = sleeping;
    }

    private static void recoverShortTerm(EmotionState state) {
        int baselineMood = MaidSoulCommonConfig.EMOTION_BASELINE_MOOD.get();
        int baselineTrust = MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get();
        state.mood = stepToward(state.mood, baselineMood, MaidSoulCommonConfig.EMOTION_MOOD_RECOVERY_STEP.get());
        state.trust = stepTowardAllowZero(state.trust, baselineTrust, MaidSoulCommonConfig.EMOTION_TRUST_RECOVERY_STEP.get());
        state.stress = stepToward(state.stress, 0, MaidSoulCommonConfig.EMOTION_STRESS_RECOVERY_STEP.get());
        state.pain = stepToward(state.pain, 0, MaidSoulCommonConfig.EMOTION_PAIN_RECOVERY_STEP.get());
        state.fear = stepTowardAllowZero(state.fear, 0, MaidSoulCommonConfig.EMOTION_FEAR_RECOVERY_STEP.get());
        state.anger = stepTowardAllowZero(state.anger, 0, MaidSoulCommonConfig.EMOTION_ANGER_RECOVERY_STEP.get());
        state.confusion = stepTowardAllowZero(state.confusion, 0, MaidSoulCommonConfig.EMOTION_CONFUSION_RECOVERY_STEP.get());
        state.dominantEmotion = dominantEmotion(state);
        selectReaction(state, chooseAction(state, "", null, PersonaProfile.fromConfig()), "gradual recovery");
    }

    private static void recoverByPercent(EmotionState state, int percent, int grudgeStep, String reason) {
        int safePercent = clamp(percent);
        state.mood = percentToward(state.mood, MaidSoulCommonConfig.EMOTION_BASELINE_MOOD.get(), safePercent);
        state.trust = percentToward(state.trust, MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get(), safePercent / 2);
        state.stress = percentToward(state.stress, 0, safePercent);
        state.pain = percentToward(state.pain, 0, safePercent);
        state.fear = percentToward(state.fear, 0, safePercent);
        state.anger = percentToward(state.anger, 0, Math.max(0, safePercent - 10));
        state.confusion = percentToward(state.confusion, 0, safePercent);
        state.security = percentToward(state.security, 70, safePercent / 2);
        for (RelationState relation : state.relations.values()) {
            relation.trust = percentToward(relation.trust, MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get(), safePercent / 2);
            relation.security = percentToward(relation.security, 70, safePercent / 2);
            relation.grudge = Math.max(0, relation.grudge - Math.max(0, grudgeStep));
            relation.recentOffenseCount = Math.max(0, relation.recentOffenseCount - Math.max(0, grudgeStep / 4));
        }
        state.dominantEmotion = dominantEmotion(state);
        selectReaction(state, chooseAction(state, "", null, PersonaProfile.fromConfig()), reason);
    }

    private static int stepToward(int value, int target, int step) {
        if (value == target) {
            return value;
        }
        int safeStep = Math.max(1, step);
        if (value < target) {
            return Math.min(target, value + safeStep);
        }
        return Math.max(target, value - safeStep);
    }

    private static int stepTowardAllowZero(int value, int target, int step) {
        return step <= 0 ? value : stepToward(value, target, step);
    }

    private static int percentToward(int value, int target, int percent) {
        int safePercent = clamp(percent);
        if (safePercent <= 0 || value == target) {
            return value;
        }
        int delta = Math.round((target - value) * safePercent / 100.0f);
        if (delta == 0) {
            delta = target > value ? 1 : -1;
        }
        return clamp(value + delta);
    }

    private static void markUnresolved(EmotionState state, String eventType, String detail, int seconds, String need) {
        state.unresolvedEvent = eventType;
        state.unresolvedDetail = shortText(detail, 80);
        state.unresolvedNeed = need;
        state.unresolvedUntilMillis = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
    }

    private static void softenUnresolved(EmotionState state, PersonaProfile persona) {
        if (state.unresolvedUntilMillis <= 0L) {
            return;
        }
        int soothedStress = MaidSoulCommonConfig.EMOTION_SOOTHED_STRESS_THRESHOLD.get() + scaled(persona.forgiveness, 0.05f);
        int soothedPain = MaidSoulCommonConfig.EMOTION_SOOTHED_PAIN_THRESHOLD.get() + scaled(persona.forgiveness, 0.04f);
        if (state.mood >= MaidSoulCommonConfig.EMOTION_SOOTHED_MOOD_THRESHOLD.get()
                && state.stress <= soothedStress
                && state.pain <= soothedPain
                && state.anger < 45
                && state.fear < 45) {
            state.unresolvedEvent = "";
            state.unresolvedDetail = "";
            state.unresolvedNeed = "";
            state.unresolvedUntilMillis = 0L;
        } else {
            state.unresolvedUntilMillis = Math.min(
                    state.unresolvedUntilMillis,
                    System.currentTimeMillis() + Math.max(6, unresolvedSeconds(persona, 0.55f)) * 1000L
            );
        }
    }

    private static String chooseAction(EmotionState state, String eventType, RelationState relation, PersonaProfile persona) {
        if ("maid.attacked.by_owner".equals(eventType)) {
            if (relation != null && relation.recentOffenseCount >= 3 && persona.boundaryStrength >= 50) {
                return "hurt_by_owner_set_boundary";
            }
            if (state.fear >= state.anger && state.fear >= 45) {
                return "hurt_by_owner_frightened";
            }
            if (state.anger >= 45) {
                return persona.angerExpressiveness >= 50 ? "hurt_by_owner_direct_anger" : "hurt_by_owner_sulking";
            }
            if (state.confusion >= 32) {
                return "hurt_by_owner_confused_needs_answer";
            }
            return state.trust < 45 || state.mood < 35 ? "hurt_by_owner_guarded" : "hurt_by_owner_needs_comfort";
        }
        if ("maid.attacked".equals(eventType)) {
            return state.pain > 45 ? "injured_needs_help" : "startled_by_danger";
        }
        if (state.unresolvedUntilMillis > System.currentTimeMillis()) {
            if (state.anger >= 45) {
                return "unresolved_boundary_waiting";
            }
            if (state.fear >= 45) {
                return "unresolved_needs_safety";
            }
            return state.mood < 40 ? "unresolved_low_mood" : "unresolved_waiting_for_comfort";
        }
        if (state.pain > 35) {
            return "physically_sore";
        }
        if (state.stress > 55 || state.fear > 45) {
            return "tense";
        }
        if (state.mood < 35) {
            return "low_mood";
        }
        if (state.mood > 78 && state.trust > 72) {
            return "warm_attached";
        }
        return "neutral_companion";
    }

    private static void selectReaction(EmotionState state, String action, String reason) {
        state.reactionAction = action;
        state.reactionReason = reason;
    }

    private static String guidanceFor(EmotionState state, PersonaProfile persona) {
        if (state.unresolvedUntilMillis > System.currentTimeMillis()) {
            if ("maid.attacked.by_owner".equals(state.unresolvedEvent)
                    || "owner.harsh_message".equals(state.unresolvedEvent)) {
                return "The owner hurt or offended you and this is still unresolved. Do not start caring casual topics, do not act as if nothing happened, and do not ask whether you are still angry from the owner's perspective. Stay guarded, cold, or boundary-setting until the owner apologizes or comforts you. Need=" + blankToDefault(state.unresolvedNeed, "apology_or_comfort")
                        + ". Coping style=" + persona.copingStyle + ".";
            }
            return "Keep acknowledging the unresolved emotional topic and need=" + blankToDefault(state.unresolvedNeed, "comfort")
                    + ". Do not jump to a new casual topic until apology, safety, or care is established. Coping style=" + persona.copingStyle + ".";
        }
        if (state.pain > 35) {
            return "Sound physically affected and ask for safety or care before casual talk.";
        }
        if (state.anger > 45) {
            return persona.angerExpressiveness >= 50
                    ? "Use clear boundary wording without becoming cruel."
                    : "Show restrained hurt or sulking instead of cheerful tone.";
        }
        if (state.fear > 45) {
            return "Prioritize reassurance and physical safety.";
        }
        if (state.mood < 40) {
            return "Use shorter, more guarded wording; avoid overly cheerful tone.";
        }
        return "Respond naturally and match the current mood and persona.";
    }

    private static String unresolvedSummary(EmotionState state) {
        if (state.unresolvedUntilMillis <= System.currentTimeMillis() || state.unresolvedEvent.isBlank()) {
            return "none";
        }
        return state.unresolvedEvent + " | " + blankToDefault(state.unresolvedDetail, "no detail");
    }

    private static String dominantEmotion(EmotionState state) {
        int max = Math.max(Math.max(state.fear, state.anger), Math.max(state.confusion, state.pain));
        if (max < 20 && state.mood >= 55) {
            return "calm";
        }
        if (state.pain == max) {
            return "pain";
        }
        if (state.fear == max) {
            return "fear";
        }
        if (state.anger == max) {
            return "anger";
        }
        if (state.confusion == max) {
            return "confusion";
        }
        return state.mood < 40 ? "sadness" : "neutral";
    }

    private static void trace(EntityMaid maid) {
        if (MaidSoulCommonConfig.EMOTION_TRACE_ECHO_ENABLED.get()) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.emotion", debugSummary(maid));
        }
    }

    private static float parseAmount(String detail) {
        if (detail == null) {
            return 0.0f;
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(detail);
        if (!matcher.find()) {
            return 0.0f;
        }
        try {
            return Float.parseFloat(matcher.group(1));
        } catch (Exception ignored) {
            return 0.0f;
        }
    }

    private static UUID parseAttackerUuid(String detail) {
        if (detail == null) {
            return null;
        }
        Matcher matcher = ATTACKER_UUID_PATTERN.matcher(detail);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int unresolvedSeconds(PersonaProfile persona, float multiplier) {
        float comfortFactor = 0.75f + persona.comfortNeed / 100.0f;
        float forgivenessFactor = 1.18f - persona.forgiveness / 180.0f;
        return Math.round(MaidSoulCommonConfig.EMOTION_UNRESOLVED_SECONDS.get() * multiplier * comfortFactor * forgivenessFactor);
    }

    private static int scaleBy(int value, int minPercent, int maxPercent) {
        return minPercent + Math.round((maxPercent - minPercent) * clamp(value) / 100.0f);
    }

    private static int scaled(int value, float factor) {
        return Math.round(value * factor);
    }

    private static int safeFavorability(EntityMaid maid) {
        try {
            return maid.getFavorability();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int safeFavorabilityLevel(EntityMaid maid) {
        try {
            return maid.getFavorabilityManager().getLevel();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static final class EmotionState {
        private int mood = MaidSoulCommonConfig.EMOTION_BASELINE_MOOD.get();
        private int trust = MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get();
        private int stress = 0;
        private int pain = 0;
        private int fear = 0;
        private int anger = 0;
        private int confusion = 0;
        private int security = 70;
        private String dominantEmotion = "calm";
        private String unresolvedEvent = "";
        private String unresolvedDetail = "";
        private String unresolvedNeed = "";
        private long unresolvedUntilMillis = 0L;
        private String reactionAction = "neutral_companion";
        private String reactionReason = "initial";
        private String lastActorRole = "";
        private String lastActorKey = "";
        private long lastUpdatedMillis = 0L;
        private long lastObservedDay = -1L;
        private boolean wasSleeping = false;
        private int sleepStartTick = 0;
        private final ConcurrentMap<String, RelationState> relations = new ConcurrentHashMap<>();
    }

    private static final class RelationState {
        private final String actorKey;
        private String role;
        private int affection = 0;
        private int affectionRaw = 0;
        private int affectionLevel = 0;
        private int trust = MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get();
        private int familiarity = 10;
        private int security = 70;
        private int grudge = 0;
        private int recentOffenseCount = 0;
        private long lastSeenMillis = 0L;

        private RelationState(String actorKey, String role) {
            this.actorKey = actorKey;
            this.role = role;
        }

        private String promptSummary() {
            return "role=%s affection=%d/100 rawFavorability=%d level=%d trust=%d familiarity=%d security=%d grudge=%d offenses=%d"
                    .formatted(role, affection, affectionRaw, affectionLevel, trust, familiarity, security, grudge, recentOffenseCount);
        }

        private String debugSummary() {
            return "{role=%s,aff=%d,raw=%d,level=%d,trust=%d,familiarity=%d,security=%d,grudge=%d,offenses=%d}"
                    .formatted(role, affection, affectionRaw, affectionLevel, trust, familiarity, security, grudge, recentOffenseCount);
        }
    }

    private record PersonaProfile(
            int sensitivity,
            int forgiveness,
            int attachment,
            int pride,
            int fearfulness,
            int angerExpressiveness,
            int comfortNeed,
            int boundaryStrength,
            String copingStyle
    ) {
        private static PersonaProfile fromConfig() {
            return new PersonaProfile(
                    MaidSoulCommonConfig.PERSONA_SENSITIVITY.get(),
                    MaidSoulCommonConfig.PERSONA_FORGIVENESS.get(),
                    MaidSoulCommonConfig.PERSONA_ATTACHMENT.get(),
                    MaidSoulCommonConfig.PERSONA_PRIDE.get(),
                    MaidSoulCommonConfig.PERSONA_FEARFULNESS.get(),
                    MaidSoulCommonConfig.PERSONA_ANGER_EXPRESSIVENESS.get(),
                    MaidSoulCommonConfig.PERSONA_COMFORT_NEED.get(),
                    MaidSoulCommonConfig.PERSONA_BOUNDARY_STRENGTH.get(),
                    blankToDefault(MaidSoulCommonConfig.PERSONA_COPING_STYLE.get(), "soft")
            );
        }

        private String promptSummary() {
            return "sensitivity=%d forgiveness=%d attachment=%d pride=%d fearfulness=%d angerExpression=%d comfortNeed=%d boundary=%d coping=%s"
                    .formatted(sensitivity, forgiveness, attachment, pride, fearfulness, angerExpressiveness, comfortNeed, boundaryStrength, copingStyle);
        }
    }
}
