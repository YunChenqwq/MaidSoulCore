package com.maidsoul.brain.planner.hook;

import java.util.ArrayList;
import java.util.List;

/**
 * planner hook 注册表。
 */
public final class PlannerHookRegistry {
    private static final PlannerHookRegistry GLOBAL = new PlannerHookRegistry();

    private final List<PlannerBeforeRequestHook> beforeRequestHooks = new ArrayList<>();
    private final List<PlannerAfterResponseHook> afterResponseHooks = new ArrayList<>();

    public static PlannerHookRegistry global() {
        return GLOBAL;
    }

    public synchronized void registerBeforeRequest(PlannerBeforeRequestHook hook) {
        if (hook != null) {
            beforeRequestHooks.add(hook);
        }
    }

    public synchronized void registerAfterResponse(PlannerAfterResponseHook hook) {
        if (hook != null) {
            afterResponseHooks.add(hook);
        }
    }

    public synchronized List<PlannerBeforeRequestHook> beforeRequestHooks() {
        return List.copyOf(beforeRequestHooks);
    }

    public synchronized List<PlannerAfterResponseHook> afterResponseHooks() {
        return List.copyOf(afterResponseHooks);
    }

    public synchronized void clear() {
        beforeRequestHooks.clear();
        afterResponseHooks.clear();
    }
}
