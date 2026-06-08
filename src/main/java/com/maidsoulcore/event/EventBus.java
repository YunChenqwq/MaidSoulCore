package com.maidsoulcore.event;

import java.util.function.Consumer;

/**
 * 内部事件总线抽象。
 * <p>
 * 运行时通过它把采集层、决策层、调试层松耦合起来。
 */
public interface EventBus {
    /**
     * 发布一条内部事件。
     */
    void publish(MaidEvent event);

    /**
     * 订阅事件流。
     */
    void subscribe(Consumer<MaidEvent> listener);
}
