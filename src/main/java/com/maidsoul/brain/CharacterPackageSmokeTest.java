package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.memory.MemoryRuntime;

import java.nio.file.Path;

/**
 * 角色包接入烟测。
 *
 * <p>这个测试不验证 LLM 输出，因为角色包不是台词模板。它只确认角色目录里的稳定人设、
 * 人格参数、长期关系状态和核心参考记忆，会作为“内部状态投影”进入主链路上下文。
 * 只要这个投影存在，planner/replyer 就能在同一个角色状态基础上做决策和表达。</p>
 */
public final class CharacterPackageSmokeTest {
    public static void main(String[] args) {
        BrainConfig config = BrainConfig.load(Path.of("config"));
        MemoryRuntime runtime = new MemoryRuntime(config.memory());
        String block = runtime.renderPromptBlock("用户不喜欢模板式关心");

        requireContains(block, "[角色包核心定义]");
        requireContains(block, "核心驱动力=");
        requireContains(block, "[人格参数]");
        requireContains(block, "[长期关系状态]");
        requireContains(block, "模板式关心");

        System.out.println("CHARACTER_PACKAGE_SMOKE_OK");
    }

    private static void requireContains(String text, String expected) {
        if (text == null || !text.contains(expected)) {
            throw new AssertionError("角色包投影缺少内容: " + expected + "\n实际内容:\n" + text);
        }
    }
}
