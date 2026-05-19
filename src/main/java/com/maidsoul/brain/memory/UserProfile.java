package com.maidsoul.brain.memory;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 酒狐对玩家的稳定认识。
 *
 * <p>画像不是一句话一改，而是由长期记忆反复强化。这里先用本地规则更新，后续每日整理可以替换成 LLM
 * 归纳，但归纳结果仍然写回这个结构。</p>
 */
public final class UserProfile {
    public String ownerId = "";
    public String knownName = "用户";
    public final List<ProfileItem> traits = new ArrayList<>();
    public final List<ProfileItem> preferences = new ArrayList<>();
    public final List<ProfileItem> boundaries = new ArrayList<>();

    public String renderForPrompt(int limit) {
        StringBuilder builder = new StringBuilder();
        appendItems(builder, "stable_traits", traits, limit);
        appendItems(builder, "preferences", preferences, limit);
        appendItems(builder, "boundaries", boundaries, limit);
        return builder.toString().trim();
    }

    public String toJson() {
        return "{\n"
                + "  \"ownerId\": \"" + JsonText.escape(ownerId) + "\",\n"
                + "  \"knownName\": \"" + JsonText.escape(knownName) + "\",\n"
                + "  \"traits\": \"" + JsonText.escape(joinItems(traits)) + "\",\n"
                + "  \"preferences\": \"" + JsonText.escape(joinItems(preferences)) + "\",\n"
                + "  \"boundaries\": \"" + JsonText.escape(joinItems(boundaries)) + "\"\n"
                + "}\n";
    }

    public static UserProfile fromJson(String raw) {
        Map<String, String> data = SimpleJson.object(raw);
        UserProfile profile = new UserProfile();
        profile.ownerId = data.getOrDefault("ownerId", "");
        profile.knownName = data.getOrDefault("knownName", "用户");
        parseItems(data.get("traits"), profile.traits);
        parseItems(data.get("preferences"), profile.preferences);
        parseItems(data.get("boundaries"), profile.boundaries);
        return profile;
    }

    public void reinforcePreference(String key, String content, String evidenceId) {
        upsert(preferences, key, content, evidenceId);
    }

    public void reinforceBoundary(String key, String content, String evidenceId) {
        upsert(boundaries, key, content, evidenceId);
    }

    public void reinforceTrait(String key, String content, String evidenceId) {
        upsert(traits, key, content, evidenceId);
    }

    private static void upsert(List<ProfileItem> items, String key, String content, String evidenceId) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isBlank()) {
            return;
        }
        for (ProfileItem item : items) {
            if (item.key.equals(normalizedKey)) {
                item.content = content;
                item.confidence = Math.min(1.0, item.confidence + 0.08);
                if (evidenceId != null && !evidenceId.isBlank() && !item.evidenceIds.contains(evidenceId)) {
                    item.evidenceIds.add(evidenceId);
                }
                return;
            }
        }
        ProfileItem item = new ProfileItem();
        item.key = normalizedKey;
        item.content = content == null ? "" : content;
        item.confidence = 0.45;
        if (evidenceId != null && !evidenceId.isBlank()) {
            item.evidenceIds.add(evidenceId);
        }
        items.add(item);
    }

    private static void appendItems(StringBuilder builder, String title, List<ProfileItem> items, int limit) {
        if (items.isEmpty() || limit <= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(title).append(":\n");
        items.stream()
                .sorted((a, b) -> Double.compare(b.confidence, a.confidence))
                .limit(limit)
                .forEach(item -> builder.append("- ")
                        .append(item.content)
                        .append("（置信度")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", item.confidence))
                        .append("）\n"));
    }

    private static String joinItems(List<ProfileItem> items) {
        List<String> encoded = new ArrayList<>();
        for (ProfileItem item : items) {
            encoded.add(item.key + "\t" + item.confidence + "\t" + item.content + "\t" + String.join(",", item.evidenceIds));
        }
        return String.join("\n", encoded);
    }

    private static void parseItems(String raw, List<ProfileItem> target) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String line : raw.split("\\n")) {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 3) {
                continue;
            }
            ProfileItem item = new ProfileItem();
            item.key = parts[0];
            try {
                item.confidence = Double.parseDouble(parts[1]);
            } catch (NumberFormatException ignored) {
                item.confidence = 0.45;
            }
            item.content = parts[2];
            if (parts.length >= 4 && !parts[3].isBlank()) {
                item.evidenceIds.addAll(List.of(parts[3].split(",")));
            }
            target.add(item);
        }
    }

    public static final class ProfileItem {
        public String key = "";
        public String content = "";
        public double confidence = 0.0;
        public final List<String> evidenceIds = new ArrayList<>();
    }
}
