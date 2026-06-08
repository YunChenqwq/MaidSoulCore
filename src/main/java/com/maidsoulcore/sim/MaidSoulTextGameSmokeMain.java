package com.maidsoulcore.sim;

import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.runtime.RuntimeConfig;

import java.nio.charset.StandardCharsets;

/**
 * 真实模型最小烟雾测试入口。
 * <p>
 * 用于在不启动 GUI 的情况下验证：
 * 1. 配置是否读取成功；
 * 2. 真实 planner / replyer 是否可调用；
 * 3. 至少能拿到一条角色回复。
 */
public final class MaidSoulTextGameSmokeMain {
    private MaidSoulTextGameSmokeMain() {
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", StandardCharsets.UTF_8.name());
        SimulationEnvironment environment = new SimulationEnvironment("maid-demo-01", "owner-demo-01", "小女仆", "主人");
        SimulationEngine engine = new SimulationEngine(RuntimeConfig.defaults(), environment);

        System.out.println("config.available=" + engine.runtimeModelConfig().available());
        System.out.println("config.status=" + engine.runtimeModelConfig().status());
        System.out.println("replyModel=" + engine.runtimeModelConfig().replyTask().primaryModelName());
        System.out.println("plannerModel=" + engine.runtimeModelConfig().plannerTask().primaryModelName());

        environment.setLastPlayerUtterance("你好，介绍一下你自己。");
        SimulationTurnResult result = engine.handle(engine.newEvent(
                "owner.talk",
                EventPriority.P1,
                java.util.Map.of("text", "你好，介绍一下你自己。")
        ));

        System.out.println("route=" + result.decision().route());
        System.out.println("plan=" + (result.plan() == null ? "null" : result.plan().summary()));
        System.out.println("tools=" + result.executionLogs());
        System.out.println("reply=" + result.reply());
    }
}
