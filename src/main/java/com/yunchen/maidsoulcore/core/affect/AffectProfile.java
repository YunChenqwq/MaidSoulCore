package com.yunchen.maidsoulcore.core.affect;

import java.util.Locale;

/**
 * MaidSoulCore v2 情感档案。
 *
 * <p>这里不再保存 mood/anger/hurt/favorability 这类旧式整数状态。
 * 新模型分成三层：</p>
 *
 * <ul>
 *     <li>VAD 情绪坐标：valence/arousal/dominance 描述当下情绪质感。</li>
 *     <li>关系动力学：intimacy/conflict/trust/attachment 描述长期关系走向。</li>
 *     <li>修复债务：hurtDebt/repairDebt 描述未被修复的关系伤口。</li>
 * </ul>
 *
 * <p>所有数值字段都使用 0.0 到 1.0，只有 valence 使用 -1.0 到 1.0。
 * 这样 planner 看到的是稳定的情绪坐标，而不是几个互相重叠的旧指标。</p>
 */
public final class AffectProfile {
    public String schemaVersion = "affect_v3_ou_hmm";

    /** 外部系统的关系信号，只作为轻微校准，不再作为主情绪字段。 */
    public double externalBondSignal = 0.0D;

    /** 长期信任：越高越容易把主人行为解释为善意，变化速度必须慢。 */
    public double trust = 0.50D;

    /** 亲密度：越高越愿意贴近、撒娇、主动陪伴。 */
    public double intimacy = 0.50D;

    /** 冲突度：越高越容易进入冷淡、修复或边界表达。 */
    public double conflict = 0.10D;

    /** 依恋度：越高越怕失联，也越容易触发想念。 */
    public double attachment = 0.58D;

    /** 关系阶段：由 HMM belief 决定，不再由简单阈值推断。 */
    public String relationshipStage = RelationshipStage.STABLE.id();

    /** 六阶段概率，顺序与 RelationshipStage.values() 一致。 */
    public double[] stageBelief = new double[]{0.25D, 0.25D, 0.0D, 0.50D, 0.0D, 0.0D};

    /** 愉快度：-1 难过/受伤，+1 开心/满足。 */
    public double valence = 0.30D;

    /** 唤醒度：0 平静，1 激动/紧张/兴奋。 */
    public double arousal = 0.40D;

    /** 掌控感：0 怯弱/依赖，1 自信/强势。 */
    public double dominance = 0.45D;

    /** 离散情绪标签：由 VAD 推导，方便 GUI 和 prompt 阅读。 */
    public String emotion = EmotionLabel.NEUTRAL.id();

    /** 被伤害但尚未修复的关系残留。 */
    public double hurtDebt = 0.0D;

    /** 当前需要安抚、解释、道歉或陪伴的程度。 */
    public double repairDebt = 0.0D;

    /** 记忆触发强度：由记忆检索层写入，影响 longing。 */
    public double memoryTriggerScore = 0.0D;

    /** 想念/主动靠近驱动力，替代旧的单一主动好奇分。 */
    public double longing = 0.55D;

    /** 给调度器使用的主动偏置，来自 longing + 风险 + 关系阶段。 */
    public double proactiveBias = 0.65D;

    /** 最近一次结构事件，用来调试状态为什么变化。 */
    public String lastEvent = AffectiveEvent.INITIATE.id();
    public String lastSemanticEvent = AffectiveEvent.INITIATE.id();
    public String lastEventEvidence = "";

    public int positiveEventStreak = 0;
    public int repairEventStreak = 0;
    public int conflictEventStreak = 0;

    public long lastEventAtEpochMillis = System.currentTimeMillis();
    public long updatedAtEpochMillis = System.currentTimeMillis();

    /**
     * 读取旧 JSON 或外部编辑后的 JSON 后，统一修正范围和派生字段。
     */
    public void normalize() {
        schemaVersion = "affect_v3_ou_hmm";
        externalBondSignal = clamp01(externalBondSignal);
        trust = clamp01(trust);
        intimacy = clamp01(intimacy);
        conflict = clamp01(conflict);
        attachment = clamp01(attachment);
        hurtDebt = clamp01(hurtDebt);
        repairDebt = clamp01(repairDebt);
        memoryTriggerScore = clamp01(memoryTriggerScore);
        longing = clamp01(longing);
        proactiveBias = clamp01(proactiveBias);
        valence = clampSigned(valence);
        arousal = clamp01(arousal);
        dominance = clamp01(dominance);
        relationshipStage = RelationshipStage.fromId(relationshipStage).id();
        normalizeStageBelief();
        emotion = EmotionLabel.fromVad(valence, arousal, dominance).id();
        if (lastEvent == null || lastEvent.isBlank()) {
            lastEvent = AffectiveEvent.INITIATE.id();
        } else {
            lastEvent = lastEvent.toLowerCase(Locale.ROOT);
        }
        if (lastSemanticEvent == null || lastSemanticEvent.isBlank()) {
            lastSemanticEvent = lastEvent;
        } else {
            lastSemanticEvent = lastSemanticEvent.toLowerCase(Locale.ROOT);
        }
        if (lastEventEvidence == null) {
            lastEventEvidence = "";
        }
        positiveEventStreak = Math.max(0, positiveEventStreak);
        repairEventStreak = Math.max(0, repairEventStreak);
        conflictEventStreak = Math.max(0, conflictEventStreak);
        long now = System.currentTimeMillis();
        if (lastEventAtEpochMillis <= 0L) {
            lastEventAtEpochMillis = now;
        }
        if (updatedAtEpochMillis <= 0L) {
            updatedAtEpochMillis = now;
        }
    }

    /**
     * 给 planner/replyer 的中文摘要。
     *
     * <p>这里有意不输出旧字段名，避免模型继续围绕 mood/anger/hurt 推理。</p>
     */
    public String brief() {
        normalize();
        RelationshipStage stage = RelationshipStage.fromId(relationshipStage);
        EmotionLabel label = EmotionLabel.fromId(emotion);
        ReplyStyleGuide style = new ReplyStyleResolver().resolve(this);
        return """
                affect_schema=affect_v3_ou_hmm
                relationship_stage=%s(%s)
                stage_belief=%s
                intimacy=%.2f  # 亲密/靠近程度
                conflict=%.2f  # 冲突/受伤张力
                trust=%.2f     # 长期信任
                attachment=%.2f # 依恋/粘人倾向
                emotion=%s(%s)
                valence=%.2f   # 愉快(-1..1)
                arousal=%.2f   # 激动(0..1)
                dominance=%.2f # 掌控感(0..1)
                hurt_debt=%.2f
                repair_debt=%.2f
                memory_trigger_score=%.2f
                longing=%.2f
                proactive_bias=%.2f
                last_event=%s
                last_semantic_event=%s
                last_event_evidence=%s
                event_streaks=positive:%d repair:%d conflict:%d
                %s
                """.formatted(
                stage.id(), stage.zhName(),
                stageBeliefText(),
                intimacy,
                conflict,
                trust,
                attachment,
                label.id(), label.zhName(),
                valence,
                arousal,
                dominance,
                hurtDebt,
                repairDebt,
                memoryTriggerScore,
                longing,
                proactiveBias,
                lastEvent,
                lastSemanticEvent,
                lastEventEvidence.isBlank() ? "none" : lastEventEvidence,
                positiveEventStreak,
                repairEventStreak,
                conflictEventStreak,
                style.toPromptBlock()
        ).trim();
    }

    public AffectSnapshot snapshot() {
        normalize();
        return new AffectSnapshot(
                RelationshipStage.fromId(relationshipStage),
                EmotionLabel.fromId(emotion),
                trust,
                intimacy,
                conflict,
                attachment,
                valence,
                arousal,
                dominance,
                hurtDebt,
                repairDebt,
                memoryTriggerScore,
                longing,
                proactiveBias,
                lastEvent
        );
    }

    private void normalizeStageBelief() {
        int length = RelationshipStage.values().length;
        if (stageBelief == null || stageBelief.length != length) {
            RelationshipStage stage = RelationshipStage.fromId(relationshipStage);
            stageBelief = new double[length];
            stageBelief[RelationshipHmm.indexOf(stage)] = 1.0D;
        }
        double total = 0.0D;
        for (int i = 0; i < stageBelief.length; i++) {
            stageBelief[i] = Math.max(0.0D, stageBelief[i]);
            total += stageBelief[i];
        }
        if (total <= 0.0D) {
            stageBelief[RelationshipHmm.indexOf(RelationshipStage.fromId(relationshipStage))] = 1.0D;
            return;
        }
        for (int i = 0; i < stageBelief.length; i++) {
            stageBelief[i] /= total;
        }
    }

    private String stageBeliefText() {
        StringBuilder builder = new StringBuilder();
        RelationshipStage[] stages = RelationshipStage.values();
        for (int i = 0; i < stages.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(stages[i].id())
                    .append("=")
                    .append(String.format(Locale.ROOT, "%.2f", stageBelief[i]));
        }
        return builder.toString();
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    static double clampSigned(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }
}
