package com.maidsoul.brain.affect;

/**
 * 酒狐面对玩家时的情绪与关系量化状态。
 *
 * <p>这里刻意区分短期情绪和长期情感：心情、愤怒、受伤、紧张会随事件快速波动；
 * 信赖、熟悉、安全感、好感变化更慢，用来表达“她如何长期看待这个人”。</p>
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

    public String brief() {
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
                + "，主动好奇=" + effectiveCuriosity();
    }

    public String stateHint() {
        String relation = "当前关系层级：" + relationshipLevel() + "。" + relationshipHint() + " ";
        if (hurt >= 55 || anger >= 55) {
            return relation + "她还明显受伤或生气，不适合立刻热情关心玩家。";
        }
        if (hurt >= 30 || anger >= 30 || tension >= 55) {
            return relation + "她有些别扭和防备，可以继续聊，但语气会更短、更谨慎。";
        }
        if (effectiveCuriosity() >= 70 && mood >= 55) {
            return relation + "她现在对玩家的状态很在意，容易主动追问或轻轻推进话题。";
        }
        if (mood >= 70 && trust >= 55) {
            return relation + "她现在比较放松，愿意自然接话和轻轻主动。";
        }
        if (mood <= 35) {
            return relation + "她心情偏低，需要更安静、克制的表达。";
        }
        return relation + "她当前情绪基本平稳，可以正常聊天。";
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
        int effective = effectiveCuriosity();
        if (hurt >= 45 || anger >= 45) {
            return "主动欲望被受伤/愤怒压住：即使好奇，也只适合冷一点、短一点地接话，不要热情追问。";
        }
        if (effective >= 75) {
            return "主动好奇很高：如果用户沉默较久，可以更积极地轻推一次，问一个低压力问题或接住刚才的情绪余韵。";
        }
        if (effective >= 55) {
            return "主动好奇中等：可以轻轻续话，但不要连续追问。";
        }
        if (curiosity >= 65 && effective < 45) {
            return "她其实很想知道，但被紧张或安全感压住了；主动时应更试探、更短，不要显得逼问。";
        }
        return "主动好奇偏低：除非话题明显未收束，否则优先安静等待。";
    }
}
