package com.maidsoul.brain.character;

import java.util.Properties;

/**
 * 人格参数。
 *
 * <p>这些值用来解释“同一个事件为什么对不同角色影响不同”。第一版先投影给
 * planner/replyer，后续可以接入 AffectEngine，让情绪增量真正经过人格权重调制。</p>
 */
public record CharacterTraits(
        double pride,
        double shyness,
        double caretaking,
        double hiddenDependency,
        double aggression,
        double forgivenessSpeed,
        double intimacyOpenness,
        double boundarySensitivity
) {
    static CharacterTraits from(Properties p) {
        return new CharacterTraits(
                number(p, "pride", 0.75),
                number(p, "shyness", 0.85),
                number(p, "caretaking", 0.65),
                number(p, "hiddenDependency", 0.80),
                number(p, "aggression", 0.20),
                number(p, "forgivenessSpeed", 0.45),
                number(p, "intimacyOpenness", 0.55),
                number(p, "boundarySensitivity", 0.75)
        );
    }

    String renderForPrompt() {
        return "自尊/胜负心=" + fmt(pride)
                + "，害羞=" + fmt(shyness)
                + "，照顾欲=" + fmt(caretaking)
                + "，隐藏依赖=" + fmt(hiddenDependency)
                + "，攻击性=" + fmt(aggression)
                + "，原谅速度=" + fmt(forgivenessSpeed)
                + "，亲密开放=" + fmt(intimacyOpenness)
                + "，边界敏感=" + fmt(boundarySensitivity);
    }

    private static double number(Properties p, String key, double fallback) {
        try {
            return clamp(Double.parseDouble(p.getProperty(key, "")));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
