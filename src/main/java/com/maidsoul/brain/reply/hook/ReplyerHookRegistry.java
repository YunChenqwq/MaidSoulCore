package com.maidsoul.brain.reply.hook;

import java.util.ArrayList;
import java.util.List;

/**
 * replyer hook 注册表。
 *
 * <p>原型机暂时没有完整插件运行时，所以先提供进程内注册表。默认没有任何 hook，
 * 行为等价于 上游参考系统 没有插件拦截时的 replyer。</p>
 */
public final class ReplyerHookRegistry {
    private static final ReplyerHookRegistry GLOBAL = new ReplyerHookRegistry();
    private final List<ReplyerAfterResponseHook> hooks = new ArrayList<>();

    public static ReplyerHookRegistry global() {
        return GLOBAL;
    }

    public synchronized void register(ReplyerAfterResponseHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
    }

    public synchronized List<ReplyerAfterResponseHook> hooks() {
        return List.copyOf(hooks);
    }

    public synchronized void clear() {
        hooks.clear();
    }
}
