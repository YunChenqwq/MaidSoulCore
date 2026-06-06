package com.maidsoul.brain.forge.soul;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 把早期“女仆实体即记忆身份”的目录迁移成“灵魂即记忆身份”。
 *
 * <p>现在的模型是：女仆实体只是身体，soulId 才是长期人格和记忆的锚点。
 * 旧版可能已经把记忆写在 maidUuid/worldId 下面；迁移器会把这些文件复制到
 * soulId 目录，并保留已有 soul 记忆。迁移完成后会写 soul.json，再由调用方发送
 * soul.migrated_legacy_maid 世界事件，让记忆图谱知道“这是一次跨身份恢复”。</p>
 */
public final class LegacyMaidMemoryMigrator {
    private LegacyMaidMemoryMigrator() {
    }

    public static Result migrateCurrentMaid(EntityMaid maid, String requestedSoulId) {
        if (maid == null) {
            return Result.failed("没有可迁移的女仆。");
        }
        String soulId = SoulId.sanitize(requestedSoulId);
        if (soulId.isBlank()) {
            return Result.failed("soulId 不能为空。");
        }

        BrainConfig config = BrainConfig.load(ForgeBrainConfigInstaller.configRoot());
        Path configured = Path.of(config.memory().dataRoot());
        Path dataRoot = configured.isAbsolute()
                ? configured
                : Path.of("").toAbsolutePath().resolve(configured).normalize();
        String maidUuid = maid.getUUID().toString();
        String worldId = MaidBrainRuntimeRegistry.worldIdFor(maid);
        Path maidsRoot = dataRoot.resolve("maids");
        Path worldScopedLegacy = maidsRoot.resolve(maidUuid).resolve(worldId);
        Path directLegacy = maidsRoot.resolve(maidUuid);
        Path soulTarget = maidsRoot.resolve(soulId);

        List<Path> sources = new ArrayList<>();
        if (Files.isDirectory(worldScopedLegacy)) {
            sources.add(worldScopedLegacy);
        }
        if (Files.isDirectory(directLegacy) && sources.isEmpty()) {
            sources.add(directLegacy);
        }
        if (sources.isEmpty()) {
            return Result.failed("没有找到旧记忆目录: " + worldScopedLegacy + " 或 " + directLegacy);
        }

        try {
            Files.createDirectories(soulTarget);
            CopyStats stats = new CopyStats();
            for (Path source : sources) {
                copyTreeWithoutOverwriting(source, soulTarget, stats);
            }

            UUID ownerUuid = maid.getOwner() == null ? null : maid.getOwner().getUUID();
            SoulBindingData next = SoulBindingData.create(soulId, ownerUuid, maid.getUUID(), worldId);
            next.writeTo(maid.getPersistentData());
            SoulStore.global().bind(soulId, maid.getName().getString(), SoulBindingData.empty(), next);
            MaidBrainRuntimeRegistry.invalidate(maid);

            String message = "迁移完成: soulId=" + soulId
                    + ", copiedFiles=" + stats.copiedFiles
                    + ", skippedExisting=" + stats.skippedExisting
                    + ", target=" + soulTarget.toAbsolutePath().normalize();
            String detail = "event=soul.migrated_legacy_maid"
                    + ", soulId=" + soulId
                    + ", legacyMaidUuid=" + maidUuid
                    + ", worldId=" + worldId
                    + ", target=" + soulTarget.toAbsolutePath().normalize()
                    + ", copiedFiles=" + stats.copiedFiles
                    + ", skippedExisting=" + stats.skippedExisting;
            return Result.success(message, detail);
        } catch (IOException e) {
            throw new UncheckedIOException("迁移旧女仆记忆失败", e);
        }
    }

    private static void copyTreeWithoutOverwriting(Path sourceRoot, Path targetRoot, CopyStats stats) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            for (Path source : stream.toList()) {
                Path relative = sourceRoot.relativize(source);
                if (relative.toString().isBlank()) {
                    continue;
                }
                Path target = targetRoot.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) {
                    stats.skippedExisting++;
                    continue;
                }
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                stats.copiedFiles++;
            }
        }
    }

    private static final class CopyStats {
        int copiedFiles;
        int skippedExisting;
    }

    public record Result(boolean success, String message, String eventDetail) {
        static Result success(String message, String eventDetail) {
            return new Result(true, message, eventDetail);
        }

        static Result failed(String message) {
            return new Result(false, message, "");
        }
    }
}
