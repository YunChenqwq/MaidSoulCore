package com.maidsoul.brain.action.ysm;

import com.maidsoul.brain.planner.hook.*;
import com.maidsoul.brain.tool.ToolSpec;

import java.util.List;
import java.util.Map;

/**
 * 通过 Hook 向 Planner 注入动作工具声明。
 *
 * <p>不修改 BuiltinToolSet，工具由 beforeRequest 动态注入。
 */
public final class ActionToolHook implements PlannerBeforeRequestHook {

    private static final ToolSpec PLAY_POSE = new ToolSpec(
            "play_pose",
            "让女仆播放一个预设的身体姿态动作。用于表达情绪或做出肢体回应，如拥抱、惊讶、挥手等。调用后动作在客户端播放，不影响对话继续。多次调用时后一次会覆盖前一次。",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "poseName", Map.of("type", "string",
                                    "description", "动作名称。可用动作: hug(拥抱), blowkiss(飞吻), crossarms(抱臂), standingsleep(打盹), laugh(捂嘴笑), huff(赌气), surprised1-3(惊讶1-3), lookback1-2(回眸1-2), kick(踢腿), bang(捶桌), giggle(傻笑), startled(吓一跳), 或中文名如 拥抱、飞吻 等。"),
                            "duration", Map.of("type", "number",
                                    "description", "持续时间（秒）。≤0=定格，>0=缓入缓出动画。默认2。"),
                            "reason", Map.of("type", "string",
                                    "description", "为什么选择这个动作。")
                    ),
                    "required", List.of("poseName")
            ),
            Map.of("stage", "action")
    );

    private static final ToolSpec PLAY_ANIMATION = new ToolSpec(
            "play_animation",
            "让女仆播放一个关键帧动画（.ymma格式），用于表达更复杂的动作序列。",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "animName", Map.of("type", "string",
                                    "description", "动画文件名（不含.ymma后缀）。如 surprise1, blowkiss, standingsleep 等。"),
                            "reason", Map.of("type", "string",
                                    "description", "为什么选择这个动画。")
                    ),
                    "required", List.of("animName")
            ),
            Map.of("stage", "action")
    );

    @Override
    public PlannerBeforeRequestResult beforeRequest(PlannerRequestContext context) {
        return new PlannerBeforeRequestResult("", List.of(PLAY_POSE, PLAY_ANIMATION));
    }
}
