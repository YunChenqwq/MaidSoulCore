package com.maidsoulcore.adapter.tlm;

import com.maidsoulcore.event.MaidEvent;

/**
 * 将 TLM / Forge 原生事件转换成 MaidSoulCore 内部统一事件。
 * <p>
 * 内部运行时只认识 {@link MaidEvent}，
 * 因此任何宿主事件在进入核心层之前都应该先做一次标准化。
 */
public interface TlmEventAdapter {
    /**
     * 把外部事件对象转换成内部事件。
     *
     * @param sourceEvent 原始宿主事件
     * @return 统一后的内部事件
     */
    MaidEvent adapt(Object sourceEvent);
}
