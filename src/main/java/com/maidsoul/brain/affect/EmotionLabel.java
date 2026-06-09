package com.maidsoul.brain.affect;

import java.util.Locale;

/**
 * VAD 空间映射出的主导情绪标签。
 *
 * <p>情绪层内部保存连续的 valence/arousal/dominance，标签只是一种给 prompt、
 * 日志和 GUI 使用的解释结果。这样不会把角色卡死在几个离散状态里。</p>
 */
public enum EmotionLabel {
    JOY("joy", "开心"),
    TRUST("trust", "信任"),
    CONTENTMENT("contentment", "安心"),
    EXCITEMENT("excitement", "兴奋期待"),
    LOVE("love", "依恋喜欢"),
    ANTICIPATION("anticipation", "期待"),
    ANXIETY("anxiety", "不安"),
    SADNESS("sadness", "难过"),
    ANGER("anger", "生气"),
    FEAR("fear", "害怕"),
    NEUTRAL("neutral", "平静");

    private final String id;
    private final String zhName;

    EmotionLabel(String id, String zhName) {
        this.id = id;
        this.zhName = zhName;
    }

    public String id() {
        return id;
    }

    public String zhName() {
        return zhName;
    }

    public static EmotionLabel fromId(String id) {
        if (id == null || id.isBlank()) {
            return NEUTRAL;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (EmotionLabel label : values()) {
            if (label.id.equals(normalized)) {
                return label;
            }
        }
        return NEUTRAL;
    }

    public static EmotionLabel fromVad(double valence, double arousal, double dominance, double intimacy) {
        if (valence > 0.62D && intimacy > 0.62D && dominance < 0.58D) {
            return LOVE;
        }
        if (valence > 0.50D) {
            if (arousal > 0.62D) {
                return dominance > 0.58D ? JOY : EXCITEMENT;
            }
            if (arousal < 0.38D) {
                return CONTENTMENT;
            }
            return TRUST;
        }
        if (valence < -0.50D) {
            if (arousal > 0.62D) {
                if (dominance > 0.58D) {
                    return ANGER;
                }
                if (dominance < 0.40D) {
                    return FEAR;
                }
                return ANXIETY;
            }
            if (arousal < 0.38D) {
                return SADNESS;
            }
            return ANXIETY;
        }
        if (arousal > 0.62D) {
            return ANTICIPATION;
        }
        return NEUTRAL;
    }
}
