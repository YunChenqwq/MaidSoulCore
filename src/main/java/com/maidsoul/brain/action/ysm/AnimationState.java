package com.maidsoul.brain.action.ysm;

/**
 * 客户端动画状态 — 复用 HugClientState 模式。
 * progress = smoothStep((tick - startTick) / (endTick - startTick))
 */
public final class AnimationState {

    public static long tick;
    public static String activeAnim;
    private static long startTick, endTick;

    private AnimationState() {}

    public static void play(String animName, int durationTicks) {
        activeAnim = animName;
        startTick = tick;
        endTick = tick + Math.max(1, durationTicks);
        YsmBoneUtil.tempOverrides.clear();
    }

    public static float progress() {
        if (activeAnim == null || endTick <= startTick) return 0f;
        if (tick >= endTick) { activeAnim = null; YsmBoneUtil.activePoseName = null; return 1f; }
        float raw = (float) (tick - startTick) / (float) (endTick - startTick);
        return smoothStep(raw);
    }

    public static void stop() {
        activeAnim = null;
        YsmBoneUtil.activePoseName = null;
        YsmBoneUtil.tempOverrides.clear();
    }

    private static float smoothStep(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
