package com.maidsoul.brain.affect;

import com.maidsoul.brain.util.SimpleJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 情绪关系状态持久化。
 */
public final class AffectProfileStore {
    private final Path path;
    private final Path legacyPath;

    public AffectProfileStore(Path path) {
        this(path, null);
    }

    public AffectProfileStore(Path path, Path legacyPath) {
        this.path = path;
        this.legacyPath = legacyPath;
    }

    public AffectProfile load() {
        Path source = Files.exists(path) ? path : legacyPath;
        if (source == null || Files.notExists(source)) {
            return new AffectProfile();
        }
        try {
            Map<String, String> data = SimpleJson.object(Files.readString(source, StandardCharsets.UTF_8));
            AffectProfile profile = new AffectProfile();
            profile.mood = SimpleJson.integer(data.get("mood"), profile.mood);
            profile.anger = SimpleJson.integer(data.get("anger"), profile.anger);
            profile.hurt = SimpleJson.integer(data.get("hurt"), profile.hurt);
            profile.tension = SimpleJson.integer(data.get("tension"), profile.tension);
            profile.trust = SimpleJson.integer(data.get("trust"), profile.trust);
            profile.familiarity = SimpleJson.integer(data.get("familiarity"), profile.familiarity);
            profile.affection = SimpleJson.integer(data.get("affection"), profile.affection);
            profile.security = SimpleJson.integer(data.get("security"), profile.security);
            profile.curiosity = SimpleJson.integer(data.get("curiosity"), profile.curiosity);
            return profile;
        } catch (IOException e) {
            throw new UncheckedIOException("读取情绪关系状态失败: " + source, e);
        }
    }

    public void save(AffectProfile profile) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("保存情绪关系状态失败: " + path, e);
        }
    }

    private static String toJson(AffectProfile profile) {
        return "{\n"
                + "  \"mood\": " + profile.mood + ",\n"
                + "  \"anger\": " + profile.anger + ",\n"
                + "  \"hurt\": " + profile.hurt + ",\n"
                + "  \"tension\": " + profile.tension + ",\n"
                + "  \"trust\": " + profile.trust + ",\n"
                + "  \"familiarity\": " + profile.familiarity + ",\n"
                + "  \"affection\": " + profile.affection + ",\n"
                + "  \"security\": " + profile.security + ",\n"
                + "  \"curiosity\": " + profile.curiosity + "\n"
                + "}\n";
    }
}
