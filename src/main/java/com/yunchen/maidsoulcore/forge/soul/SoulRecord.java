package com.yunchen.maidsoulcore.forge.soul;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.time.Instant;
import java.util.Map;

public record SoulRecord(
        String soulId,
        String displayName,
        String createdFromWorldId,
        String createdFromMaidUuid,
        String createdFromOwnerUuid,
        String lastWorldId,
        String lastMaidUuid,
        String lastOwnerUuid,
        long createdAt,
        long lastUsedAt,
        int schemaVersion
) {
    public static SoulRecord create(String soulId, String displayName, SoulBindingData binding) {
        long now = System.currentTimeMillis();
        return new SoulRecord(
                SoulId.sanitize(soulId),
                displayName == null || displayName.isBlank() ? soulId : displayName.trim(),
                binding.worldId(),
                binding.maidUuid(),
                binding.ownerUuid(),
                binding.worldId(),
                binding.maidUuid(),
                binding.ownerUuid(),
                now,
                now,
                1
        );
    }

    public static SoulRecord fromJson(String json, String fallbackSoulId) {
        Map<String, String> map = SimpleJson.object(json);
        String id = map.getOrDefault("soulId", fallbackSoulId);
        return new SoulRecord(
                SoulId.sanitize(id),
                map.getOrDefault("displayName", id),
                map.getOrDefault("createdFromWorldId", ""),
                map.getOrDefault("createdFromMaidUuid", ""),
                map.getOrDefault("createdFromOwnerUuid", ""),
                map.getOrDefault("lastWorldId", ""),
                map.getOrDefault("lastMaidUuid", ""),
                map.getOrDefault("lastOwnerUuid", ""),
                parseLong(map.get("createdAt"), System.currentTimeMillis()),
                parseLong(map.get("lastUsedAt"), System.currentTimeMillis()),
                SimpleJson.integer(map.get("schemaVersion"), 1)
        );
    }

    public SoulRecord withBinding(SoulBindingData binding) {
        return new SoulRecord(
                soulId,
                displayName,
                createdFromWorldId,
                createdFromMaidUuid,
                createdFromOwnerUuid,
                binding.worldId(),
                binding.maidUuid(),
                binding.ownerUuid(),
                createdAt,
                System.currentTimeMillis(),
                schemaVersion
        );
    }

    public String toJson() {
        return "{\n"
                + "  \"soulId\": \"" + JsonText.escape(soulId) + "\",\n"
                + "  \"displayName\": \"" + JsonText.escape(displayName) + "\",\n"
                + "  \"createdFromWorldId\": \"" + JsonText.escape(createdFromWorldId) + "\",\n"
                + "  \"createdFromMaidUuid\": \"" + JsonText.escape(createdFromMaidUuid) + "\",\n"
                + "  \"createdFromOwnerUuid\": \"" + JsonText.escape(createdFromOwnerUuid) + "\",\n"
                + "  \"lastWorldId\": \"" + JsonText.escape(lastWorldId) + "\",\n"
                + "  \"lastMaidUuid\": \"" + JsonText.escape(lastMaidUuid) + "\",\n"
                + "  \"lastOwnerUuid\": \"" + JsonText.escape(lastOwnerUuid) + "\",\n"
                + "  \"createdAt\": " + createdAt + ",\n"
                + "  \"lastUsedAt\": " + lastUsedAt + ",\n"
                + "  \"createdAtIso\": \"" + Instant.ofEpochMilli(createdAt) + "\",\n"
                + "  \"lastUsedAtIso\": \"" + Instant.ofEpochMilli(lastUsedAt) + "\",\n"
                + "  \"schemaVersion\": " + schemaVersion + "\n"
                + "}\n";
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}


