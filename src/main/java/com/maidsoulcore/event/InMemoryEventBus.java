package com.maidsoulcore.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 最简单的内存事件总线实现。
 * <p>
 * 适合当前单 JVM 内部的同步分发场景。
 * 后续如需跨线程/跨进程，可替换为队列或网络总线。
 */
public final class InMemoryEventBus implements EventBus {
    private final List<Consumer<MaidEvent>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(MaidEvent event) {
        for (Consumer<MaidEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    @Override
    public void subscribe(Consumer<MaidEvent> listener) {
        listeners.add(listener);
    }
}
