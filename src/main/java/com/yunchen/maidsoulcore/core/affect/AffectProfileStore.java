package com.yunchen.maidsoulcore.core.affect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AffectProfileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    public AffectProfileStore(Path path) {
        this.path = path;
    }

    public AffectProfile load() {
        try {
            if (Files.notExists(path)) {
                AffectProfile profile = new AffectProfile();
                profile.normalize();
                return profile;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                AffectProfile profile = GSON.fromJson(reader, AffectProfile.class);
                if (profile == null) {
                    profile = new AffectProfile();
                }
                profile.normalize();
                return profile;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read affect state: " + path, e);
        }
    }

    public void save(AffectProfile profile) {
        try {
            Files.createDirectories(path.getParent());
            profile.normalize();
            Files.writeString(path, GSON.toJson(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save affect state: " + path, e);
        }
    }
}
