package com.yunchen.maidsoulcore.forge.soul;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record SoulBindingData(
        String soulId,
        String bindingId,
        String ownerUuid,
        String maidUuid,
        String worldId,
        long boundAt,
        int schemaVersion
) {
    public static final String TAG_ROOT = "MaidSoulCoreSoul";
    private static final int CURRENT_SCHEMA = 1;

    public static SoulBindingData empty() {
        return new SoulBindingData("", "", "", "", "", 0L, CURRENT_SCHEMA);
    }

    public static SoulBindingData create(String soulId, UUID ownerUuid, UUID maidUuid, String worldId) {
        String safeSoulId = SoulId.sanitize(soulId);
        String safeWorldId = worldId == null || worldId.isBlank() ? "unknown_world" : worldId.trim();
        return new SoulBindingData(
                safeSoulId,
                "binding-" + UUID.randomUUID(),
                ownerUuid == null ? "" : ownerUuid.toString(),
                maidUuid == null ? "" : maidUuid.toString(),
                safeWorldId,
                System.currentTimeMillis(),
                CURRENT_SCHEMA
        );
    }

    public static SoulBindingData fromTag(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.contains(TAG_ROOT)) {
            return empty();
        }
        CompoundTag tag = persistentData.getCompound(TAG_ROOT);
        return new SoulBindingData(
                tag.getString("soulId"),
                tag.getString("bindingId"),
                tag.getString("ownerUuid"),
                tag.getString("maidUuid"),
                tag.getString("worldId"),
                tag.getLong("boundAt"),
                tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : CURRENT_SCHEMA
        );
    }

    public boolean isBound() {
        return soulId != null && !soulId.isBlank();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("soulId", soulId == null ? "" : soulId);
        tag.putString("bindingId", bindingId == null ? "" : bindingId);
        tag.putString("ownerUuid", ownerUuid == null ? "" : ownerUuid);
        tag.putString("maidUuid", maidUuid == null ? "" : maidUuid);
        tag.putString("worldId", worldId == null ? "" : worldId);
        tag.putLong("boundAt", boundAt);
        tag.putInt("schemaVersion", schemaVersion <= 0 ? CURRENT_SCHEMA : schemaVersion);
        return tag;
    }

    public void writeTo(CompoundTag persistentData) {
        persistentData.put(TAG_ROOT, toTag());
    }

    public static void clear(CompoundTag persistentData) {
        if (persistentData != null) {
            persistentData.remove(TAG_ROOT);
        }
    }
}


