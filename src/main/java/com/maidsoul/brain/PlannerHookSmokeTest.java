package com.maidsoul.brain;

import com.maidsoul.brain.planner.hook.PlannerHookRegistry;
import com.maidsoul.brain.planner.hook.PlannerHookRunner;
import com.maidsoul.brain.planner.hook.PlannerAfterResponseResult;
import com.maidsoul.brain.reasoning.PlanDecision;

import java.util.List;

/**
 * planner hook 确定性测试。
 *
 * <p>验证未来外部信息源可以通过 before_request 进入 planner，
 * 也可以通过 after_response 调整最终决策。</p>
 */
public final class PlannerHookSmokeTest {
    private PlannerHookSmokeTest() {
    }

    public static void main(String[] args) {
        PlannerHookRegistry.global().clear();
        PlannerHookRunner runner = new PlannerHookRunner();

        PlannerHookRegistry.global().registerBeforeRequest(context ->
                new com.maidsoul.brain.planner.hook.PlannerBeforeRequestResult("[天气] 今天下雨。", List.of()));
        PlannerHookRunner.BeforeOutcome before = runner.beforeRequest("planner", "test", "最近聊天记录：空", List.of());
        if (!before.context().contains("今天下雨")) {
            throw new IllegalStateException("before_request 没有把外部信息追加到 planner 上下文。");
        }

        PlannerHookRegistry.global().registerAfterResponse(context ->
                new PlannerAfterResponseResult(PlanDecision.replyLatest("hook 覆盖为回复。", "外部信息已注入。")));
        PlanDecision decision = runner.afterResponse(
                "planner",
                "test",
                "",
                new PlanDecision("no_action", "", 0, "原始不发言。", ""),
                0,
                0,
                0
        );
        if (!"reply".equals(decision.action()) || !decision.referenceInfo().contains("外部信息")) {
            throw new IllegalStateException("after_response 没有覆盖 planner 决策。");
        }

        PlannerHookRegistry.global().clear();
        System.out.println("PLANNER_HOOK_SMOKE_OK");
    }
}
