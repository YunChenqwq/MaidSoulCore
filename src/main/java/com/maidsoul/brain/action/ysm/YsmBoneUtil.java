package com.maidsoul.brain.action.ysm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * YSM 骨骼运行时操作工具 — 通过 Java 反射绕过 YSM 闭源 API。
 *
 * <h2>数据流</h2>
 * <pre>
 *   Mixin 每帧调用 applyIfNeeded(ysmWrapper)
 *     → ① wrapper.getModel()
 *     → ② model.getBones() → Map
 *     → ③ 遍历读骨骼名
 *     → ④ 从 PoseConfig / AnimationPlayer 拿旋转值
 *     → ⑤ Bone.setXRot/setYRot/setZRot 覆写
 * </pre>
 */
public final class YsmBoneUtil {

    // ═══ YSM 混淆方法名 ═══
    private static final String WRAPPER_ENTITY_GETTER = "OOO00Oo0OoOOooO0O0O00o0O";
    private static final String WRAPPER_MODEL_GETTER = "o0oo00OOooo0oooOoooo00O0";
    private static final String MODEL_BONES_GETTER = "Oooo0O0OO0O0000Oooo0Oo0o";
    private static final String BONE_NAME_GETTER = "oo0O0oOo0OOOO0O0OOOoo0oo";
    private static final String BONE_ROT_X = "O0OOOoOooOO0OO0o00OoO0O0";
    private static final String BONE_ROT_Y = "ooOooO0OOo00O0oooo000oOO";
    private static final String BONE_ROT_Z = "Oooo0O0OO0O0000Oooo0Oo0o";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static volatile String activePoseName = null;
    private static int heartbeatTick = 0;
    private static String lastDiagPose = null;

    public static final ConcurrentHashMap<String, float[]> boneRotationCache = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, float[]> tempOverrides = new ConcurrentHashMap<>();

    // ═══ 诊断日志 ═══
    private static final DateTimeFormatter DIAG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static PrintWriter diagWriter;
    private static boolean diagInitFailed;

    private static PrintWriter diagWriter() {
        if (diagWriter != null) return diagWriter;
        if (diagInitFailed) return null;
        try {
            Path logFile = FMLPaths.GAMEDIR.get().resolve("maidsoul_action_diag.log");
            diagWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
            diagWriter.println("=== MaidSoul action diag started at " + LocalTime.now() + " ===");
            return diagWriter;
        } catch (IOException e) { diagInitFailed = true; return null; }
    }

    private static void diagLog(String msg) {
        PrintWriter w = diagWriter();
        if (w != null) w.println(LocalTime.now().format(DIAG_TIME) + " " + msg);
    }

    // ═══ 核心入口 ═══

    public static void applyIfNeeded(Object ysmWrapper) {
        AnimationState.tick++;
        if (heartbeatTick++ % 200 == 0) {
            LOGGER.info("[Mixin] alive #{} wrapper={}", heartbeatTick, ysmWrapper != null ? ysmWrapper.getClass().getSimpleName() : "null");
        }
        if (ysmWrapper == null) return;

        // 动画优先
        if (YsmAnimationPlayer.isPlaying()) {
            YsmAnimationPlayer.tick();
            Map<String, float[]> animRots = YsmAnimationPlayer.getCurrentRotations();
            if (animRots.isEmpty()) return;
            try {
                Map<String, Object> bones = collectBones(ysmWrapper, false);
                if (!bones.isEmpty()) {
                    for (var entry : animRots.entrySet()) {
                        float[] rot = entry.getValue();
                        setBoneRotation(bones, entry.getKey(), rot[0], rot[1], rot[2]);
                    }
                }
            } catch (Throwable e) { diagLog("YMMA err: " + e.getMessage()); }
            return;
        }

        // smoothStep 动画
        if (AnimationState.activeAnim != null) {
            String animName = AnimationState.activeAnim;
            float progress = AnimationState.progress();
            PoseConfig.PoseData pose = PoseConfig.getPose(animName);
            if (pose == null) { AnimationState.stop(); return; }
            try {
                Map<String, Object> bones = collectBones(ysmWrapper, false);
                if (!bones.isEmpty()) applyPoseWithProgress(pose, bones, progress);
            } catch (Throwable e) { diagLog("ANIM err: " + e.getMessage()); }
            return;
        }

        // 静态 pose
        if (activePoseName == null || activePoseName.isEmpty()) return;
        boolean needDiag = !activePoseName.equals(lastDiagPose);
        try {
            Map<String, Object> bones = collectBones(ysmWrapper, needDiag);
            if (bones.isEmpty()) { if (needDiag) diagLog("collectBones empty, pose=" + activePoseName); return; }
            if (needDiag) {
                int shown = 0;
                for (String bn : bones.keySet()) { if (shown++ >= 8) break; diagLog("  bone[" + shown + "]=" + bn); }
            }
            applyPoseFromConfig(activePoseName, bones);
            if (needDiag) { diagLog(activePoseName + " ok " + bones.size() + " bones"); lastDiagPose = activePoseName; }
        } catch (Throwable e) {
            diagLog("ERROR: " + e.getClass().getName() + ": " + e.getMessage());
            StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
            diagLog("  " + sw.toString().replace("\n", "\n  "));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> collectBones(Object ysmWrapper, boolean needDiag) throws Exception {
        Object model = invokeNoArg(ysmWrapper, WRAPPER_MODEL_GETTER);
        if (model == null) { if (needDiag) diagLog("  step1 FAIL: model=null"); return Map.of(); }
        if (needDiag) diagLog("  step1 model.class=" + model.getClass().getName());
        Object rawMap = invokeNoArg(model, MODEL_BONES_GETTER);
        if (!(rawMap instanceof Map<?, ?> map)) {
            if (needDiag) diagLog("  step2 FAIL: rawMap=" + (rawMap == null ? "null" : rawMap.getClass().getName()));
            return Map.of();
        }
        if (needDiag) diagLog("  step2 boneMap.size=" + map.size());
        Map<String, Object> result = new HashMap<>();
        for (Object bone : map.values()) {
            if (bone == null) continue;
            Object rawName = invokeNoArg(bone, BONE_NAME_GETTER);
            if (rawName instanceof String name && !name.isEmpty()) result.putIfAbsent(name, bone);
        }
        return result;
    }

    private static void applyPoseWithProgress(PoseConfig.PoseData pose, Map<String, Object> bones, float progress) throws Exception {
        for (var entry : pose.bones().entrySet()) {
            float[] rot = entry.getValue();
            setBoneRotation(bones, entry.getKey(), rot[0] * progress, rot[1] * progress, rot[2] * progress);
        }
        if (!tempOverrides.isEmpty()) {
            for (var entry : tempOverrides.entrySet()) {
                float[] rot = entry.getValue();
                setBoneRotation(bones, entry.getKey(), rot[0], rot[1], rot[2]);
            }
        }
    }

    private static void applyPoseFromConfig(String poseName, Map<String, Object> bones) throws Exception {
        PoseConfig.PoseData pose = PoseConfig.getPose(poseName);
        if (pose == null) return;
        for (var entry : pose.bones().entrySet()) {
            float[] rot = entry.getValue();
            setBoneRotation(bones, entry.getKey(), rot[0], rot[1], rot[2]);
        }
        if (!tempOverrides.isEmpty()) {
            for (var entry : tempOverrides.entrySet()) {
                float[] rot = entry.getValue();
                setBoneRotation(bones, entry.getKey(), rot[0], rot[1], rot[2]);
            }
        }
    }

    static void setBoneRotation(Map<String, Object> bones, String name, float x, float y, float z) throws Exception {
        Object bone = bones.get(name);
        if (bone == null) return;
        invokeFloat(bone, BONE_ROT_X, x);
        invokeFloat(bone, BONE_ROT_Y, y);
        invokeFloat(bone, BONE_ROT_Z, z);
        boneRotationCache.put(name, new float[]{x, y, z});
    }

    // ═══ 保存 ═══
    private static final Gson GSON_SAVE = new Gson();

    /** @return null=成功, 否则错误信息 */
    public static String saveTempOverridesToPose(String poseName) {
        if (poseName == null || poseName.isEmpty()) return "无效的动作名";
        if (tempOverrides.isEmpty()) return "没有临时覆写数据可保存";
        Path dir = FMLPaths.GAMEDIR.get().resolve("config").resolve("maidsoulcore");
        Path external = dir.resolve("poses.json");
        try { Files.createDirectories(dir); } catch (IOException e) { return "无法创建目录: " + e.getMessage(); }
        Map<String, Object> root;
        if (Files.exists(external)) {
            try {
                root = GSON_SAVE.fromJson(Files.readString(external, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Object>>() {}.getType());
            } catch (Exception e) { return "解析失败: " + e.getMessage(); }
        } else {
            try (var in = YsmBoneUtil.class.getClassLoader().getResourceAsStream("poses.json")) {
                if (in == null) return "找不到 poses.json";
                root = GSON_SAVE.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Object>>() {}.getType());
            } catch (Exception e) { return "读取 jar 内 poses.json 失败: " + e.getMessage(); }
        }
        if (root == null) return "poses.json 格式错误";
        if (Files.exists(external)) {
            try { Files.copy(external, external.resolveSibling("poses.json.bak"), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException e) { return "备份失败: " + e.getMessage(); }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> poses = (Map<String, Object>) root.get("poses");
        if (poses == null) { poses = new LinkedHashMap<>(); root.put("poses", poses); }
        Map<String, Object> existingPose = poses.containsKey(poseName) ? (Map<String, Object>) poses.get(poseName) : new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> existingTyped = (Map<String, Object>) existingPose;
        Map<String, List<Double>> newPose = new LinkedHashMap<>();
        for (var e : existingTyped.entrySet()) {
            @SuppressWarnings("unchecked")
            List<Double> oldRot = (List<Double>) e.getValue();
            newPose.put(e.getKey(), oldRot != null ? new ArrayList<>(oldRot) : List.of(0.0, 0.0, 0.0));
        }
        for (var e : tempOverrides.entrySet()) {
            float[] rad = e.getValue();
            List<Double> vals = List.of(
                    Math.round(rad[0] * 1_000_000.0) / 1_000_000.0,
                    Math.round(rad[1] * 1_000_000.0) / 1_000_000.0,
                    Math.round(rad[2] * 1_000_000.0) / 1_000_000.0);
            newPose.put(e.getKey(), vals);
            @SuppressWarnings("unchecked")
            List<Double> old = existingTyped.containsKey(e.getKey()) ? (List<Double>) existingTyped.get(e.getKey()) : null;
            float ox = old != null ? old.get(0).floatValue() : 0f, oy = old != null ? old.get(1).floatValue() : 0f, oz = old != null ? old.get(2).floatValue() : 0f;
            diagLog("TUNE " + poseName + "/" + e.getKey()
                    + " old=" + fmtDeg(ox, oy, oz) + " new=" + fmtDeg(rad[0], rad[1], rad[2])
                    + " diff=" + fmtDeg(rad[0] - ox, rad[1] - oy, rad[2] - oz));
        }
        poses.put(poseName, newPose);
        try {
            Files.writeString(external, GSON_SAVE.toJson(root), StandardCharsets.UTF_8);
            PoseConfig.reload();
            tempOverrides.clear();
            return null;
        } catch (IOException e) { return "写入失败: " + e.getMessage(); }
    }

    private static String fmtDeg(float x, float y, float z) {
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)°", Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z));
    }

    // ═══ 反射 ═══
    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void invokeFloat(Object target, String methodName, float value) throws Exception {
        Method method = target.getClass().getMethod(methodName, float.class);
        method.setAccessible(true);
        method.invoke(target, value);
    }
}
