package com.maidsoul.brain.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 用户画像落盘。
 */
public final class UserProfileStore {
    private final Path path;

    public UserProfileStore(Path path) {
        this.path = path;
    }

    public UserProfile load(String ownerId) {
        if (Files.notExists(path)) {
            UserProfile profile = new UserProfile();
            profile.ownerId = ownerId;
            return profile;
        }
        try {
            UserProfile profile = UserProfile.fromJson(Files.readString(path, StandardCharsets.UTF_8));
            if (profile.ownerId == null || profile.ownerId.isBlank()) {
                profile.ownerId = ownerId;
            }
            return profile;
        } catch (IOException e) {
            throw new UncheckedIOException("读取用户画像失败: " + path, e);
        }
    }

    public void save(UserProfile profile) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, profile.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("保存用户画像失败: " + path, e);
        }
    }
}
