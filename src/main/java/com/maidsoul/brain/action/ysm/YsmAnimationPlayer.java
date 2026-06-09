package com.maidsoul.brain.action.ysm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * .ymma 动画播放器 — 四元数关键帧 + Slerp 插值。
 */
public final class YsmAnimationPlayer {

    public record Keyframe(double t, float[] q) {}
    public record AnimationData(String name, double duration, boolean loop,
                                Map<String, List<Keyframe>> bones) {}

    private static final Map<String, AnimationData> cache = new LinkedHashMap<>();
    private static AnimationData current = null;
    private static long startMs = 0;
    private static final Gson GSON = new Gson();

    private YsmAnimationPlayer() {}

    public static boolean isPlaying() { return current != null; }
    public static String currentName() { return current != null ? current.name : null; }

    public static void tick() {
        if (current == null) return;
        if ((System.currentTimeMillis() - startMs) / 1000.0 >= current.duration) {
            if (current.loop) startMs = System.currentTimeMillis();
            else stop();
        }
    }

    public static Map<String, float[]> getCurrentRotations() {
        if (current == null) return Map.of();
        double t = (System.currentTimeMillis() - startMs) / 1000.0;
        Map<String, float[]> result = new LinkedHashMap<>();
        for (var entry : current.bones.entrySet()) {
            List<Keyframe> kfs = entry.getValue();
            if (kfs.isEmpty()) continue;
            result.put(entry.getKey(), quatToEulerRad(interpolate(kfs, t)));
        }
        return result;
    }

    public static void stop() { current = null; YsmBoneUtil.activePoseName = null; }

    // ═══ 加载 ═══

    private static Path animsDir() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("maidsoulcore").resolve("anims");
    }

    public static List<String> list() {
        Path dir = animsDir();
        if (!Files.exists(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".ymma"))
                    .map(p -> p.getFileName().toString().replace(".ymma", ""))
                    .collect(Collectors.toList());
        } catch (Exception ignored) { return List.of(); }
    }

    public static String play(String name) {
        AnimationData data = cache.get(name);
        if (data == null) {
            data = load(name);
            if (data == null) return "找不到动画: " + name;
            cache.put(name, data);
        }
        current = data;
        startMs = System.currentTimeMillis();
        YsmBoneUtil.tempOverrides.clear();
        return null;
    }

    private static AnimationData load(String name) {
        Path file = animsDir().resolve(name + ".ymma");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> raw = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            if (raw == null) return null;
            String animName = raw.getOrDefault("name", name).toString();
            double duration = ((Number) raw.getOrDefault("duration", 0.0)).doubleValue();
            boolean loop = (Boolean) raw.getOrDefault("loop", false);
            @SuppressWarnings("unchecked")
            Map<String, Object> bonesRaw = (Map<String, Object>) raw.get("bones");
            if (bonesRaw == null) return null;
            Map<String, List<Keyframe>> bones = new LinkedHashMap<>();
            for (var entry : bonesRaw.entrySet()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> kfsRaw = (List<Map<String, Object>>) entry.getValue();
                List<Keyframe> kfs = new ArrayList<>();
                for (var kfRaw : kfsRaw) {
                    double kt = ((Number) kfRaw.get("t")).doubleValue();
                    @SuppressWarnings("unchecked")
                    List<Double> qRaw = (List<Double>) kfRaw.get("q");
                    kfs.add(new Keyframe(kt, new float[]{qRaw.get(0).floatValue(), qRaw.get(1).floatValue(), qRaw.get(2).floatValue(), qRaw.get(3).floatValue()}));
                }
                bones.put(entry.getKey(), kfs);
            }
            return new AnimationData(animName, duration, loop, bones);
        } catch (Exception e) { return null; }
    }

    // ═══ 插值 ═══

    private static float[] interpolate(List<Keyframe> kfs, double t) {
        if (kfs.size() == 1) return kfs.get(0).q;
        if (t <= kfs.get(0).t) return kfs.get(0).q;
        if (t >= kfs.get(kfs.size() - 1).t) return kfs.get(kfs.size() - 1).q;
        for (int i = 0; i < kfs.size() - 1; i++) {
            Keyframe a = kfs.get(i), b = kfs.get(i + 1);
            if (t >= a.t && t <= b.t) {
                double span = b.t - a.t;
                if (span <= 0) return a.q;
                return slerp(a.q, b.q, (float) ((t - a.t) / span));
            }
        }
        return kfs.get(kfs.size() - 1).q;
    }

    private static float[] slerp(float[] qa, float[] qb, float t) {
        double dot = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3];
        if (dot < 0) { qb = new float[]{-qb[0], -qb[1], -qb[2], -qb[3]}; dot = -dot; }
        if (dot > 0.9995) {
            float[] r = {qa[0] + t * (qb[0] - qa[0]), qa[1] + t * (qb[1] - qa[1]), qa[2] + t * (qb[2] - qa[2]), qa[3] + t * (qb[3] - qa[3])};
            double len = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + r[3] * r[3]);
            return new float[]{(float) (r[0] / len), (float) (r[1] / len), (float) (r[2] / len), (float) (r[3] / len)};
        }
        double theta0 = Math.acos(dot), sinTheta = Math.sin(theta0);
        double wA = Math.sin((1 - t) * theta0) / sinTheta, wB = Math.sin(t * theta0) / sinTheta;
        return new float[]{(float) (wA * qa[0] + wB * qb[0]), (float) (wA * qa[1] + wB * qb[1]), (float) (wA * qa[2] + wB * qb[2]), (float) (wA * qa[3] + wB * qb[3])};
    }

    private static float[] quatToEulerRad(float[] q) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double rx = Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        double sinY = 2 * (w * y - z * x);
        double ry = Math.abs(sinY) >= 1 ? Math.copySign(Math.PI / 2, sinY) : Math.asin(sinY);
        double rz = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        return new float[]{(float) rx, (float) ry, (float) rz};
    }
}
