package com.maidsoul.brain.action.ysm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 动作配置 — 加载 poses.json，支持中英文别名和外部重载。
 */
public final class PoseConfig {

    public record PoseData(Map<String, float[]> bones) {}

    private static final Gson GSON = new Gson();
    private static final Map<String, PoseData> POSES = new LinkedHashMap<>();

    /** 英文别名 → 中文键 */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("hug", "拥抱");
        ALIASES.put("surprise1", "惊讶1"); ALIASES.put("surprise2", "惊讶2"); ALIASES.put("surprise3", "惊讶3");
        ALIASES.put("blowkiss", "飞吻"); ALIASES.put("crossarms", "抱臂");
        ALIASES.put("lookback1", "回眸1"); ALIASES.put("lookback2", "回眸2");
        ALIASES.put("kick", "踢腿"); ALIASES.put("laugh", "捂嘴笑");
        ALIASES.put("lyingconvulse", "躺倒抽动"); ALIASES.put("lyingscratch", "躺倒挠");
        ALIASES.put("phonecut", "挂电话"); ALIASES.put("footpainjump", "脚疼跳");
        ALIASES.put("startled", "吓一跳"); ALIASES.put("giggle", "傻笑"); ALIASES.put("huff", "赌气");
        ALIASES.put("bang", "捶桌"); ALIASES.put("standingsleep", "打盹");
        ALIASES.put("trip1", "绊倒1"); ALIASES.put("trip2", "绊倒2");
        ALIASES.put("teleportout", "传送"); ALIASES.put("dramaticstandup", "起身");
    }

    private PoseConfig() {}

    /** 初始化：优先外部 config/maidsoulcore/poses.json，否则 jar 内 poses.json */
    public static void init() { reload(); }

    public static void reload() {
        Path external = FMLPaths.GAMEDIR.get().resolve("config").resolve("maidsoulcore").resolve("poses.json");
        if (Files.exists(external)) loadFromFile(external);
        else load();
    }

    private static void load() {
        POSES.clear();
        try {
            Reader reader = new InputStreamReader(
                    Objects.requireNonNull(PoseConfig.class.getClassLoader().getResourceAsStream("poses.json")),
                    StandardCharsets.UTF_8);
            Map<String, Map<String, Object>> raw = GSON.fromJson(reader,
                    new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
            reader.close();
            if (raw == null || !raw.containsKey("poses")) return;
            parsePoses(raw.get("poses"));
        } catch (Exception ignored) {}
    }

    private static void loadFromFile(Path file) {
        POSES.clear();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Map<String, Object>> raw = GSON.fromJson(json,
                    new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
            if (raw == null || !raw.containsKey("poses")) return;
            parsePoses(raw.get("poses"));
        } catch (Exception e) { load(); }
    }

    @SuppressWarnings("unchecked")
    private static void parsePoses(Map<String, Object> posesRaw) {
        for (var entry : posesRaw.entrySet()) {
            Map<String, Object> bonesRaw = (Map<String, Object>) entry.getValue();
            Map<String, float[]> bones = new LinkedHashMap<>();
            for (var be : bonesRaw.entrySet()) {
                List<Double> rot = (List<Double>) be.getValue();
                bones.put(be.getKey(), new float[]{rot.get(0).floatValue(), rot.get(1).floatValue(), rot.get(2).floatValue()});
            }
            POSES.put(entry.getKey(), new PoseData(bones));
        }
    }

    // ── 查询 ──

    public static PoseData getPose(String name) { return POSES.get(name); }
    public static List<String> getPoseNames() { return new ArrayList<>(POSES.keySet()); }

    /** 中文直返，英文别名转中文，都不匹配返 null */
    public static String resolve(String input) {
        if (input == null) return null;
        if (POSES.containsKey(input)) return input;
        return ALIASES.get(input.toLowerCase());
    }
}
