package com.maidsoul.brain.affect;

/**
 * 轻量关系阶段机。
 *
 * <p>这里借鉴 HMM 的思想：阶段不是由单个事件直接硬切，而是由当前连续状态和事件共同
 * 推动。实现上保持很小，避免给 Minecraft 模组引入额外依赖或耗时模型。</p>
 */
final class RelationshipHmm {
    RelationshipStage observe(RelationshipStage current, AffectEventKind event, double intimacy, double conflict, int positiveStreak) {
        RelationshipStage safe = current == null ? RelationshipStage.COURTING : current;
        if (event == AffectEventKind.OWNER_ATTACK || event == AffectEventKind.MAID_HURT_BY_OWNER) {
            return conflict > 0.18D ? RelationshipStage.COLD : RelationshipStage.REPAIRING;
        }
        if (event == AffectEventKind.OWNER_APOLOGY) {
            return conflict > 0.20D ? RelationshipStage.REPAIRING : sweetOrStable(intimacy, positiveStreak);
        }
        if (event == AffectEventKind.OWNER_AFFECTION || event == AffectEventKind.OWNER_DISTRESS) {
            if (conflict > 0.48D) {
                return RelationshipStage.REPAIRING;
            }
            return sweetOrStable(intimacy, positiveStreak);
        }
        if (event == AffectEventKind.QUIET_RECOVERY) {
            return stepQuiet(safe, intimacy, conflict);
        }
        if (conflict > 0.60D) {
            return RelationshipStage.COLD;
        }
        if (safe == RelationshipStage.REPAIRING && conflict < 0.18D) {
            return RelationshipStage.STABLE;
        }
        if (safe == RelationshipStage.COLD && conflict < 0.28D && intimacy > 0.42D) {
            return RelationshipStage.REPAIRING;
        }
        return safe;
    }

    private RelationshipStage sweetOrStable(double intimacy, int positiveStreak) {
        if (intimacy >= 0.76D && positiveStreak >= 4) {
            return RelationshipStage.PASSIONATE;
        }
        if (intimacy >= 0.56D && positiveStreak >= 2) {
            return RelationshipStage.SWEET;
        }
        if (intimacy >= 0.42D) {
            return RelationshipStage.STABLE;
        }
        return RelationshipStage.COURTING;
    }

    private RelationshipStage stepQuiet(RelationshipStage current, double intimacy, double conflict) {
        if (conflict > 0.50D) {
            return RelationshipStage.COLD;
        }
        if (current == RelationshipStage.PASSIONATE && intimacy < 0.66D) {
            return RelationshipStage.SWEET;
        }
        if (current == RelationshipStage.SWEET && intimacy < 0.48D) {
            return RelationshipStage.STABLE;
        }
        if (current == RelationshipStage.COLD && conflict < 0.34D) {
            return RelationshipStage.REPAIRING;
        }
        return current;
    }
}
