package com.maidsoul.brain.character;

import com.maidsoul.brain.util.SimpleJson;

import java.util.Map;

/**
 * 角色与当前玩家之间的长期关系状态。
 *
 * <p>AffectProfile 更像“现在她身上正在波动的情绪”；RelationshipState 记录更长期、
 * 更不该被一两句话抹掉的关系事实，例如是否确认恋人关系、修复债、关系深度。</p>
 */
public final class RelationshipState {
    public String level = "初识";
    public int bondDepth = 20;
    public boolean romanticConfirmed = false;
    public int trustHistory = 50;
    public int affectionHistory = 50;
    public int repairDebt = 0;
    public String knownBoundaries = "";
    public String importantMilestones = "";

    static RelationshipState fromJson(String raw) {
        RelationshipState state = new RelationshipState();
        Map<String, String> data = SimpleJson.object(raw);
        state.level = text(data.get("level"), state.level);
        state.bondDepth = SimpleJson.integer(data.get("bondDepth"), state.bondDepth);
        state.romanticConfirmed = Boolean.parseBoolean(text(data.get("romanticConfirmed"), "false"));
        state.trustHistory = SimpleJson.integer(data.get("trustHistory"), state.trustHistory);
        state.affectionHistory = SimpleJson.integer(data.get("affectionHistory"), state.affectionHistory);
        state.repairDebt = SimpleJson.integer(data.get("repairDebt"), state.repairDebt);
        state.knownBoundaries = text(data.get("knownBoundaries"), "");
        state.importantMilestones = text(data.get("importantMilestones"), "");
        return state;
    }

    String renderForPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("关系阶段=").append(level)
                .append("，关系深度=").append(bondDepth)
                .append("，恋人确认=").append(romanticConfirmed)
                .append("，长期信任=").append(trustHistory)
                .append("，长期好感=").append(affectionHistory)
                .append("，修复债=").append(repairDebt);
        if (!knownBoundaries.isBlank()) {
            builder.append("\n已知边界=").append(knownBoundaries);
        }
        if (!importantMilestones.isBlank()) {
            builder.append("\n重要节点=").append(importantMilestones);
        }
        return builder.toString();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
