package com.maidsoul.brain.affect;

/**
 * 女仆面对主人时的情绪与关系量化状态。
 *
 * <p>旧版 0-100 字段仍然保留，是为了兼容现有日志、GUI、记忆快照和主动节奏。
 * 新版核心状态是 VAD、intimacy/conflict、relationshipStage 与 emotionLabel。
 * 回复器主要读取新版状态产生表达风格，运行时不应该把它当成是否发言的唯一开关。</p>
 */
public final class AffectProfile {
    public int mood = 60;
    public int anger = 0;
    public int hurt = 0;
    public int tension = 10;
    public int trust = 50;
    public int familiarity = 20;
    public int affection = 50;
    public int security = 55;
    public int curiosity = 45;
    public double valence = 0.18D;
    public double arousal = 0.32D;
    public double dominance = 0.48D;
    public double intimacy = 0.28D;
    public double conflict = 0.06D;
    public double hurtDebt = 0.0D;
    public double repairDebt = 0.0D;
    public double longing = 0.42D;
    public double styleWarmth = 0.50D;
    public double styleClinginess = 0.42D;
    public double styleCaution = 0.18D;
    public int positiveEventStreak = 0;
    public String relationshipStage = RelationshipStage.COURTING.id();
    public String emotionLabel = EmotionLabel.NEUTRAL.id();
    public String lastEvent = "";

    public String brief() {
        return "她现在是“" + emotion().zhName()
                + "”：" + affectReason()
                + "；关系=" + stage().zhName() + "/" + relationshipLevel()
                + "，亲密=" + level(intimacy)
                + "，冲突=" + level(conflict)
                + "，受伤债=" + level(hurtDebt)
                + "，修复债=" + level(repairDebt)
                + "，想念=" + level(longing)
                + "；表达=" + styleToneZh()
                + "；主动兼容=" + effectiveCuriosity()
                + "，来源=" + curiositySource()
                + "；raw: VAD=" + percent(valence) + "/" + percent(arousal) + "/" + percent(dominance)
                + " intimacy=" + percent(intimacy)
                + " conflict=" + percent(conflict)
                + " longing=" + percent(longing)
                + " hurtDebt=" + percent(hurtDebt)
                + " repairDebt=" + percent(repairDebt)
                + (lastEvent == null || lastEvent.isBlank() ? "" : " lastEvent=" + lastEvent);
    }

    public String legacyBrief() {
        return "心情=" + mood
                + "，愤怒=" + anger
                + "，受伤=" + hurt
                + "，紧张=" + tension
                + "，信赖=" + trust
                + "，熟悉=" + familiarity
                + "，好感=" + affection
                + "，关系=" + relationshipLevel()
                + "，安全感=" + security
                + "，好奇心=" + curiosity
                + "，主动好奇=" + effectiveCuriosity()
                + "，VAD=" + percent(valence) + "/" + percent(arousal) + "/" + percent(dominance)
                + "，亲密=" + percent(intimacy)
                + "，冲突=" + percent(conflict)
                + "，阶段=" + stage().zhName()
                + "，主情绪=" + emotion().zhName();
    }

    public String stateHint() {
        String relation = "当前关系层级：" + relationshipLevel()
                + "；动态阶段：" + stage().zhName()
                + "；主导情绪：" + emotion().zhName() + "。"
                + relationshipHint() + " ";
        if (conflict >= 0.55D || hurtDebt >= 0.55D || repairDebt >= 0.55D) {
            return relation + "她仍有明显受伤、防备或修复压力，回复时要承认情绪余波，不能直接甜蜜重置。";
        }
        if (stage() == RelationshipStage.REPAIRING || conflict >= 0.25D || hurtDebt >= 0.22D) {
            return relation + "她正在修复关系，语气应该温柔但谨慎，先接住当前话题，再慢慢靠近。";
        }
        if (stage() == RelationshipStage.SWEET || stage() == RelationshipStage.PASSIONATE || emotion() == EmotionLabel.LOVE) {
            return relation + "她很喜欢主人，表达可以更柔软、粘人、愿意贴近，但仍要回应最新输入。";
        }
        if (valence >= 0.45D && arousal <= 0.55D) {
            return relation + "她现在比较安心，适合轻声陪伴和自然接话。";
        }
        if (valence <= -0.35D) {
            return relation + "她心情偏低，表达要短一些、软一些，避免跳到无关话题。";
        }
        return relation + "她当前状态基本平稳，可以正常聊天，并保持温柔陪伴感。";
    }

    public String replyStyleAdvice() {
        StringBuilder builder = new StringBuilder();
        builder.append("表达建议：tone=").append(styleTone())
                .append("；warmth=").append(percent(styleWarmth))
                .append("；clinginess=").append(percent(styleClinginess))
                .append("；caution=").append(percent(styleCaution))
                .append("；状态=").append(emotion().zhName()).append("/").append(stage().zhName()).append("。");
        if (repairDebt >= 0.22D || hurtDebt >= 0.22D || stage() == RelationshipStage.REPAIRING) {
            builder.append(" 保留修复余波，先回应主人的当前话，再表达愿意慢慢和好。");
        } else if (stage() == RelationshipStage.SWEET || stage() == RelationshipStage.PASSIONATE || emotion() == EmotionLabel.LOVE) {
            builder.append(" 可以更温柔、更喜欢主人、更粘一点，但不要无视当前事实。");
        } else if (emotion() == EmotionLabel.ANXIETY || emotion() == EmotionLabel.FEAR) {
            builder.append(" 先确认安全和陪伴，不要装作轻松。");
        } else {
            builder.append(" 自然接话，保持柔和陪伴。");
        }
        return builder.toString();
    }

    public String relationshipLevel() {
        int score = (affection * 4 + trust * 3 + familiarity * 2 + security) / 10;
        if (score >= 90 && affection >= 85 && trust >= 80) {
            return "伴侣";
        }
        if (score >= 78 && affection >= 75 && trust >= 70) {
            return "恋人";
        }
        if (score >= 65 && affection >= 60 && familiarity >= 55) {
            return "暧昧";
        }
        if (score >= 50 && trust >= 45) {
            return "熟悉";
        }
        return "初识";
    }

    private String relationshipHint() {
        return switch (relationshipLevel()) {
            case "伴侣" -> "可以更自然亲密，但仍要保留自己的情绪和边界。";
            case "恋人" -> "可以主动表达在意，撒娇和别扭都应带着真实关心。";
            case "暧昧" -> "可以有轻微试探和害羞，但不要默认过度亲密。";
            case "熟悉" -> "可以放松一点接话，但亲密表达要看上下文。";
            default -> "表达应保持礼貌和适度距离，先建立熟悉感。";
        };
    }

    /**
     * 被防御情绪削弱后的主动好奇心。
     *
     * <p>原始好奇心代表“想知道”；主动好奇代表“敢不敢问出口”。
     * 受伤、愤怒、紧张和低安全感会压住主动欲望，避免角色在受伤时还机械贴上去。</p>
     */
    public int effectiveCuriosity() {
        int suppressed = curiosity
                - anger / 2
                - hurt / 2
                - tension / 3
                - Math.max(0, 55 - security) / 2;
        return Math.max(0, Math.min(100, suppressed));
    }

    public String proactiveHint() {
        return "主动节奏仍由运行时和 planner 判断；情绪层只提供表达状态。"
                + " 当前主动好奇兼容值=" + effectiveCuriosity()
                + "，回复风格参考：" + replyStyleAdvice();
    }

    public RelationshipStage stage() {
        return RelationshipStage.fromId(relationshipStage);
    }

    public EmotionLabel emotion() {
        return EmotionLabel.fromId(emotionLabel);
    }

    public void refreshDerivedStyle() {
        emotionLabel = EmotionLabel.fromVad(valence, arousal, dominance, intimacy).id();
        styleWarmth = clamp01(0.42D + valence * 0.30D + intimacy * 0.22D - conflict * 0.30D);
        styleClinginess = clamp01(0.32D + intimacy * 0.42D + longing * 0.18D - conflict * 0.24D);
        styleCaution = clamp01(0.14D + conflict * 0.58D + hurtDebt * 0.32D + Math.max(0.0D, arousal - 0.58D) * 0.20D);
        mood = clampInt((int) Math.round((valence + 1.0D) * 50.0D));
        tension = clampInt((int) Math.round(arousal * 72.0D + conflict * 28.0D));
        anger = clampInt((int) Math.round(Math.max(0.0D, -valence) * arousal * dominance * 100.0D + conflict * 35.0D));
        hurt = clampInt((int) Math.round(hurtDebt * 100.0D));
        trust = clampInt((int) Math.round(42.0D + intimacy * 42.0D - conflict * 24.0D));
        affection = clampInt((int) Math.round(42.0D + intimacy * 48.0D + valence * 10.0D - conflict * 20.0D));
        familiarity = clampInt((int) Math.round(20.0D + intimacy * 70.0D));
        security = clampInt((int) Math.round(52.0D + dominance * 20.0D - conflict * 35.0D));
    }

    public void normalize() {
        valence = clamp(valence, -1.0D, 1.0D);
        arousal = clamp01(arousal);
        dominance = clamp01(dominance);
        intimacy = clamp01(intimacy);
        conflict = clamp01(conflict);
        hurtDebt = clamp01(hurtDebt);
        repairDebt = clamp01(repairDebt);
        longing = clamp01(longing);
        positiveEventStreak = Math.max(0, positiveEventStreak);
        refreshDerivedStyle();
    }

    private String styleTone() {
        if (styleCaution >= 0.62D) {
            return "soft_cautious";
        }
        if (emotion() == EmotionLabel.LOVE || styleClinginess >= 0.68D) {
            return "warm_clingy";
        }
        if (emotion() == EmotionLabel.ANXIETY || emotion() == EmotionLabel.FEAR) {
            return "protective_nervous";
        }
        if (emotion() == EmotionLabel.EXCITEMENT || emotion() == EmotionLabel.JOY) {
            return "bright_soft";
        }
        return "gentle_companion";
    }

    private String styleToneZh() {
        if (styleCaution >= 0.62D) {
            return "温柔谨慎";
        }
        if (emotion() == EmotionLabel.LOVE || styleClinginess >= 0.68D) {
            return "温柔粘人";
        }
        if (emotion() == EmotionLabel.ANXIETY || emotion() == EmotionLabel.FEAR) {
            return "紧张但想保护";
        }
        if (emotion() == EmotionLabel.EXCITEMENT || emotion() == EmotionLabel.JOY) {
            return "明亮柔软";
        }
        return "柔和陪伴";
    }

    private String affectReason() {
        if (hurtDebt >= 0.48D || conflict >= 0.48D) {
            return "冲突或受伤债仍明显，不能当成已经完全恢复";
        }
        if (repairDebt >= 0.30D || stage() == RelationshipStage.REPAIRING) {
            return "关系正在修复，需要先接住余波再靠近";
        }
        if (intimacy >= 0.62D && longing >= 0.55D) {
            return "亲密感和想念都偏高，更想贴近主人";
        }
        if (valence >= 0.45D && conflict <= 0.16D) {
            return "最近状态偏安心，冲突残留很低";
        }
        if (arousal >= 0.62D && valence < 0.10D) {
            return "唤醒度偏高，当前更容易紧张或警觉";
        }
        if (valence <= -0.28D) {
            return "愉悦度偏低，需要更轻、更软地回应";
        }
        return "状态整体平稳，适合自然陪伴和顺着话题接话";
    }

    private String curiositySource() {
        if (conflict >= 0.35D || hurtDebt >= 0.30D) {
            return "被冲突/受伤债压低";
        }
        if (longing >= 0.58D && intimacy >= 0.55D) {
            return "想念高+亲密高";
        }
        if (intimacy >= 0.55D) {
            return "亲密关系支撑";
        }
        if (arousal >= 0.58D) {
            return "当前事件唤醒";
        }
        return "基础互动欲望";
    }

    private static String level(double value) {
        int p = percent(value);
        if (p >= 75) {
            return "很高(" + p + ")";
        }
        if (p >= 55) {
            return "高(" + p + ")";
        }
        if (p >= 30) {
            return "中(" + p + ")";
        }
        if (p >= 12) {
            return "低(" + p + ")";
        }
        return "无(" + p + ")";
    }

    public static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int percent(double value) {
        return clampInt((int) Math.round(value * 100.0D));
    }

    private static int clampInt(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
