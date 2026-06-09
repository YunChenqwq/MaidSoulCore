package com.maidsoul.brain.action.ysm;

import java.util.List;

/**
 * AI 动作调用统一入口。
 *
 * <p>这是 MaidSoulCore 灵魂核心调用身体动作系统的唯一接口。
 * 合作者（或其他 AI 工具）只需要通过这个类的方法来触发动作，
 * 不必关心底层是 YSM 反射还是其他渲染管线。
 *
 * <h3>API</h3>
 * <pre>
 *   // 静态定格
 *   ActionBridge.playPose("拥抱");   // 中文名
 *   ActionBridge.playPose("hug");    // 英文别名
 *
 *   // 关键帧动画
 *   ActionBridge.playAnimation("surprise1");
 *
 *   // 列表
 *   ActionBridge.listPoses();        // 返回所有动作名
 *   ActionBridge.listAnimations();   // 返回所有 .ymma 动画名
 *
 *   // 停止
 *   ActionBridge.clear();            // 停止一切动作
 * </pre>
 *
 * <h3>预留接口</h3>
 * 后续可以在这里扩展：
 * - playAnimationWithDuration(name, seconds)   // 自定义时长
 * - playBlend(fromPose, toPose, duration)       // 过渡动画
 * - getBoneAngle(boneName)                      // 读取骨骼角度
 * - applyBoneOverride(boneName, rx, ry, rz)     // AI 直接控制骨骼
 */
public final class ActionBridge {

    private ActionBridge() {}

    // ═══ 静态姿态 ═══

    /**
     * 播放姿态动作。支持中英文别名。
     * @param duration 秒。≤0 = 静态定格，>0 = smoothStep 缓入缓出动画
     */
    public static String playPose(String name, float duration) {
        String resolved = PoseConfig.resolve(name);
        if (resolved == null) return "未知动作: " + name;
        if (duration <= 0) {
            YsmBoneUtil.activePoseName = resolved;
            YsmBoneUtil.tempOverrides.clear();
        } else {
            AnimationState.play(resolved, Math.max(1, (int)(duration * 20)));
        }
        return null;
    }

    /** 静态定格（无动画）。兼容旧调用。 */
    public static String playPose(String name) {
        return playPose(name, 0);
    }

    // ═══ 关键帧动画 ═══

    /** 播放 .ymma 关键帧动画 */
    public static String playAnimation(String name) {
        return YsmAnimationPlayer.play(name);
    }

    // ═══ 查询 ═══

    public static List<String> listPoses() {
        return PoseConfig.getPoseNames();
    }

    public static List<String> listAnimations() {
        return YsmAnimationPlayer.list();
    }

    public static boolean isPlaying() {
        return YsmAnimationPlayer.isPlaying() || AnimationState.activeAnim != null || YsmBoneUtil.activePoseName != null;
    }

    public static String currentAction() {
        if (YsmAnimationPlayer.isPlaying()) return "anim:" + YsmAnimationPlayer.currentName();
        if (AnimationState.activeAnim != null) return "pose_anim:" + AnimationState.activeAnim;
        if (YsmBoneUtil.activePoseName != null) return "pose:" + YsmBoneUtil.activePoseName;
        return null;
    }

    // ═══ 停止 ═══

    public static void clear() {
        YsmAnimationPlayer.stop();
        AnimationState.stop();
        YsmBoneUtil.activePoseName = null;
        YsmBoneUtil.tempOverrides.clear();
    }
}
