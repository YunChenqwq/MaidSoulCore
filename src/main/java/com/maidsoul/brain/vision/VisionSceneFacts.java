package com.maidsoul.brain.vision;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 视觉摘要后的结构化事实层。
 *
 * <p>视觉模型返回的自然语言只作为 evidence；真正用于陪伴话题和危险判断的字段，
 * 优先从 Forge 传入的结构化 MC 状态中解析。这样可以减少“把当前女仆看成陌生女仆”
 * 之类的视觉幻觉。</p>
 */
public record VisionSceneFacts(
        String rawSummary,
        String ownerFocus,
        String dangerLevel,
        String dangerReason,
        String companionshipCues,
        String topicCandidates,
        String uncertainty
) {
    public static VisionSceneFacts from(String rawSummary, String sceneHint) {
        String summary = clean(rawSummary);
        Map<String, String> state = parseStructuredScene(sceneHint);
        boolean lookingAtSelf = "true".equalsIgnoreCase(state.getOrDefault("owner_looking_at_request_maid", "false"));
        int monsterCount = integer(state.get("nearby_monsters"));
        String focus = state.getOrDefault("focus", "unknown");
        String weather = state.getOrDefault("weather", "unknown");
        String time = state.getOrDefault("time", "unknown");
        String ownerRisks = state.getOrDefault("owner_risks", "[]");
        String ownerFocus = lookingAtSelf ? "current_maid_self" : focus;

        String dangerLevel = "none";
        String dangerReason = "no obvious structured risk";
        if (!"[]".equals(ownerRisks) && !ownerRisks.isBlank()) {
            dangerLevel = "high";
            dangerReason = "owner_risks=" + ownerRisks;
        } else if (monsterCount >= 3) {
            dangerLevel = "high";
            dangerReason = "nearby_monsters=" + monsterCount;
        } else if (monsterCount > 0) {
            dangerLevel = "medium";
            dangerReason = "nearby_monsters=" + monsterCount;
        } else if ("thunder".equals(weather)) {
            dangerLevel = "medium";
            dangerReason = "weather=thunder";
        } else if ("night".equals(time)) {
            dangerLevel = "low";
            dangerReason = "time=night";
        }

        String cues = companionshipCues(lookingAtSelf, state);
        String topics = topicCandidates(lookingAtSelf, dangerLevel, state);
        String uncertainty = summary.isBlank()
                ? "vision_summary_empty; rely_on_structured_minecraft_state"
                : "vision_summary_is_auxiliary; structured_minecraft_state_has_priority";
        return new VisionSceneFacts(summary, ownerFocus, dangerLevel, dangerReason, cues, topics, uncertainty);
    }

    public String toPlannerText() {
        return "[视觉事实] "
                + "owner_focus=" + ownerFocus
                + ", danger_level=" + dangerLevel
                + ", danger_reason=" + dangerReason
                + ", companionship_cues=" + companionshipCues
                + ", topic_candidates=" + topicCandidates
                + ", uncertainty=" + uncertainty
                + (rawSummary.isBlank() ? "" : ", raw_summary=" + rawSummary);
    }

    public String toWorldEventDetail(String source, String owner, String maidUuid, String sceneHint) {
        return "source=" + clean(source)
                + ", owner=" + clean(owner)
                + ", request_maid_uuid=" + clean(maidUuid)
                + ", scene_hint=" + clip(sceneHint, 300)
                + ", " + toPlannerText();
    }

    private static String companionshipCues(boolean lookingAtSelf, Map<String, String> state) {
        String distance = state.getOrDefault("maid_distance", "");
        String weather = state.getOrDefault("weather", "unknown");
        String time = state.getOrDefault("time", "unknown");
        java.util.ArrayList<String> cues = new java.util.ArrayList<>();
        if (lookingAtSelf) {
            cues.add("owner_is_looking_at_current_maid");
        }
        double dist = decimal(distance, 999.0D);
        if (dist < 20.0D) {
            cues.add("owner_is_nearby");
        }
        if ("rain".equals(weather) || "thunder".equals(weather)) {
            cues.add("weather_can_be_used_as_soft_topic");
        }
        if ("night".equals(time) || "evening".equals(time)) {
            cues.add("night_or_evening_companion_context");
        }
        return cues.isEmpty() ? "[]" : cues.toString();
    }

    private static String topicCandidates(boolean lookingAtSelf, String dangerLevel, Map<String, String> state) {
        java.util.ArrayList<String> topics = new java.util.ArrayList<>();
        if (!"none".equals(dangerLevel)) {
            topics.add("care_check");
        }
        if (lookingAtSelf) {
            topics.add("affection_ping");
        }
        if (decimal(state.getOrDefault("maid_distance", ""), 999.0D) < 20.0D) {
            topics.add("companionship");
        }
        String weather = state.getOrDefault("weather", "unknown");
        String time = state.getOrDefault("time", "unknown");
        if ("rain".equals(weather) || "thunder".equals(weather) || "night".equals(time) || "evening".equals(time)) {
            topics.add("world_comment");
        }
        return topics.isEmpty() ? "[natural_company]" : topics.toString();
    }

    private static Map<String, String> parseStructuredScene(String sceneHint) {
        String text = sceneHint == null ? "" : sceneHint;
        int start = text.indexOf("structured_scene={");
        if (start >= 0) {
            start += "structured_scene={".length();
            int end = text.indexOf('}', start);
            if (end > start) {
                text = text.substring(start, end);
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        String currentKey = "";
        StringBuilder currentValue = new StringBuilder();
        for (String part : text.split(",")) {
            String item = part.trim();
            int eq = item.indexOf('=');
            if (eq > 0) {
                if (!currentKey.isBlank()) {
                    out.put(currentKey, currentValue.toString().trim());
                }
                currentKey = item.substring(0, eq).trim();
                currentValue.setLength(0);
                currentValue.append(item.substring(eq + 1).trim());
            } else if (!currentKey.isBlank()) {
                currentValue.append(',').append(item);
            }
        }
        if (!currentKey.isBlank()) {
            out.put(currentKey, currentValue.toString().trim());
        }
        return out;
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String clip(String text, int max) {
        String value = clean(text);
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
