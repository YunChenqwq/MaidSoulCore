package com.maidsoul.brain.forge.soul;

import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class SoulStore {
    private final Path root;

    public SoulStore(Path root) {
        this.root = root;
    }

    public static SoulStore global() {
        return new SoulStore(ForgeBrainConfigInstaller.configRoot().resolve("souls"));
    }

    public Path soulDir(String soulId) {
        return root.resolve(SoulId.sanitize(soulId));
    }

    public List<SoulSummary> list() {
        if (Files.notExists(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> load(path.getFileName().toString()))
                    .map(SoulSummary::from)
                    .sorted(Comparator.comparingLong(SoulSummary::lastUsedAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取灵魂列表失败: " + root, e);
        }
    }

    public SoulRecord load(String soulId) {
        String id = SoulId.sanitize(soulId);
        Path path = soulDir(id).resolve("soul.json");
        if (Files.notExists(path)) {
            return new SoulRecord(id, id, "", "", "", "", "", "", System.currentTimeMillis(), 0L, 1);
        }
        try {
            return SoulRecord.fromJson(Files.readString(path, StandardCharsets.UTF_8), id);
        } catch (IOException e) {
            throw new UncheckedIOException("读取灵魂数据失败: " + path, e);
        }
    }

    public SoulRecord save(SoulRecord record) {
        Path path = soulDir(record.soulId()).resolve("soul.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, record.toJson(), StandardCharsets.UTF_8);
            return record;
        } catch (IOException e) {
            throw new UncheckedIOException("保存灵魂数据失败: " + path, e);
        }
    }

    public SoulBindResult bind(String requestedSoulId, String displayName, SoulBindingData previousBinding, SoulBindingData newBinding) {
        String soulId = SoulId.sanitize(requestedSoulId);
        SoulRecord existing = load(soulId);
        SoulRecord base = existing.lastUsedAt() <= 0
                ? SoulRecord.create(soulId, displayName == null || displayName.isBlank() ? soulId : displayName, newBinding)
                : existing;
        String eventType = eventType(previousBinding, base, newBinding);
        SoulRecord saved = save(base.withBinding(newBinding));
        return new SoulBindResult(newBinding, saved, eventType, eventDetail(eventType, previousBinding, saved, newBinding));
    }

    private static String eventType(SoulBindingData previousBinding, SoulRecord record, SoulBindingData next) {
        if (record.lastUsedAt() <= 0 || record.lastWorldId().isBlank()) {
            return "soul.first_bound";
        }
        if (previousBinding != null && previousBinding.isBound() && !previousBinding.soulId().equals(next.soulId())) {
            return "soul.replaced_binding";
        }
        if (!record.lastWorldId().equals(next.worldId())) {
            return "soul.transferred_world";
        }
        if (!record.lastMaidUuid().equals(next.maidUuid())) {
            return "soul.rebound_same_world";
        }
        return "soul.binding_refreshed";
    }

    private static String eventDetail(String eventType, SoulBindingData previousBinding, SoulRecord record, SoulBindingData next) {
        return "event=" + eventType
                + ", soulId=" + next.soulId()
                + ", displayName=" + record.displayName()
                + ", fromWorldId=" + empty(record.lastWorldId())
                + ", toWorldId=" + next.worldId()
                + ", fromMaidUuid=" + empty(record.lastMaidUuid())
                + ", toMaidUuid=" + next.maidUuid()
                + ", ownerUuid=" + next.ownerUuid()
                + ", previousSoulId=" + (previousBinding == null ? "" : empty(previousBinding.soulId()));
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
