package com.yunchen.maidsoulcore.core.affect;

import java.util.Locale;

public enum EmotionLabel {
    JOY("joy", "开心"),
    TRUST("trust", "信任"),
    CONTENTMENT("contentment", "安心"),
    EXCITEMENT("excitement", "兴奋期待"),
    ANTICIPATION("anticipation", "预感"),
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
        String normalized = id.toLowerCase(Locale.ROOT);
        for (EmotionLabel label : values()) {
            if (label.id.equals(normalized)) {
                return label;
            }
        }
        return NEUTRAL;
    }

    public static EmotionLabel fromVad(double valence, double arousal, double dominance) {
        if (valence > 0.50D) {
            if (arousal > 0.60D) {
                return dominance > 0.60D ? JOY : EXCITEMENT;
            }
            if (arousal < 0.40D) {
                return CONTENTMENT;
            }
            return TRUST;
        }
        if (valence < -0.50D) {
            if (arousal > 0.60D) {
                if (dominance > 0.60D) {
                    return ANGER;
                }
                if (dominance < 0.40D) {
                    return FEAR;
                }
                return ANXIETY;
            }
            if (arousal < 0.40D) {
                return SADNESS;
            }
            return ANXIETY;
        }
        if (arousal > 0.60D) {
            return ANTICIPATION;
        }
        return NEUTRAL;
    }
}
