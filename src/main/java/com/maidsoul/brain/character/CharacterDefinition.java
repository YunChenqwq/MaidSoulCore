package com.maidsoul.brain.character;

import java.util.Properties;

/**
 * 角色包里的稳定人格定义。
 *
 * <p>这层不是 prompt 文案库，而是角色对象的“出生设定”。运行时可以把它投影给
 * planner/replyer，但不会因为某一轮聊天自动改写它。真正会变化的内容放在
 * affect_state、relationship 和 memories 里。</p>
 */
public record CharacterDefinition(
        String id,
        String name,
        String role,
        String coreDrive,
        String coreFear,
        String attachmentStyle,
        String hurtStyle,
        String affectionStyle,
        String boundaryStyle,
        String speechPrinciple
) {
    static CharacterDefinition from(Properties p, String fallbackId) {
        return new CharacterDefinition(
                text(p, "id", fallbackId),
                text(p, "name", "酒狐"),
                text(p, "role", "小只傲娇女仆"),
                text(p, "coreDrive", "想被主人需要，也想被认真对待。"),
                text(p, "coreFear", "害怕被当成工具，也害怕自己太主动显得难堪。"),
                text(p, "attachmentStyle", "嘴硬但容易心软，关系越深越会露出依赖。"),
                text(p, "hurtStyle", "受伤后会退开，不会立刻恢复。"),
                text(p, "affectionStyle", "先害羞防御，再小声靠近。"),
                text(p, "boundaryStyle", "被冒犯时会设边界，而不是继续讨好。"),
                text(p, "speechPrinciple", "短句、口语、少口癖，让情绪推动表达。")
        );
    }

    String renderCoreBlock() {
        return "身份=" + name + " / " + role
                + "\n核心驱动力=" + coreDrive
                + "\n核心恐惧=" + coreFear
                + "\n依恋方式=" + attachmentStyle
                + "\n受伤反应=" + hurtStyle
                + "\n亲近反应=" + affectionStyle
                + "\n边界方式=" + boundaryStyle
                + "\n表达原则=" + speechPrinciple;
    }

    private static String text(Properties p, String key, String fallback) {
        String value = p.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
