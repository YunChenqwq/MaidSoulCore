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
            profile.valence = decimal(data.get("valence"), profile.valence);
            profile.arousal = decimal(data.get("arousal"), profile.arousal);
            profile.dominance = decimal(data.get("dominance"), profile.dominance);
            profile.intimacy = decimal(data.get("intimacy"), profile.intimacy);
            profile.conflict = decimal(data.get("conflict"), profile.conflict);
            profile.hurtDebt = decimal(data.get("hurtDebt"), profile.hurtDebt);
            profile.repairDebt = decimal(data.get("repairDebt"), profile.repairDebt);
            profile.longing = decimal(data.get("longing"), profile.longing);
            profile.styleWarmth = decimal(data.get("styleWarmth"), profile.styleWarmth);
            profile.styleClinginess = decimal(data.get("styleClinginess"), profile.styleClinginess);
            profile.styleCaution = decimal(data.get("styleCaution"), profile.styleCaution);
            profile.positiveEventStreak = SimpleJson.integer(data.get("positiveEventStreak"), profile.positiveEventStreak);
            profile.relationshipStage = stringValue(data.get("relationshipStage"), profile.relationshipStage);
            profile.emotionLabel = stringValue(data.get("emotionLabel"), profile.emotionLabel);
            profile.lastEvent = stringValue(data.get("lastEvent"), profile.lastEvent);
            profile.normalize();
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
                + "  \"curiosity\": " + profile.curiosity + ",\n"
                + "  \"valence\": " + format(profile.valence) + ",\n"
                + "  \"arousal\": " + format(profile.arousal) + ",\n"
                + "  \"dominance\": " + format(profile.dominance) + ",\n"
                + "  \"intimacy\": " + format(profile.intimacy) + ",\n"
                + "  \"conflict\": " + format(profile.conflict) + ",\n"
                + "  \"hurtDebt\": " + format(profile.hurtDebt) + ",\n"
                + "  \"repairDebt\": " + format(profile.repairDebt) + ",\n"
                + "  \"longing\": " + format(profile.longing) + ",\n"
                + "  \"styleWarmth\": " + format(profile.styleWarmth) + ",\n"
                + "  \"styleClinginess\": " + format(profile.styleClinginess) + ",\n"
                + "  \"styleCaution\": " + format(profile.styleCaution) + ",\n"
                + "  \"positiveEventStreak\": " + profile.positiveEventStreak + ",\n"
                + "  \"relationshipStage\": \"" + escape(profile.relationshipStage) + "\",\n"
                + "  \"emotionLabel\": \"" + escape(profile.emotionLabel) + "\",\n"
                + "  \"lastEvent\": \"" + escape(profile.lastEvent) + "\"\n"
                + "}\n";
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stringValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
