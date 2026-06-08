package com.maidsoulcore.adapter.tlm;

/**
 * TLM 适配层的统一入口。
 * <p>
 * 这个接口的职责很单纯：
 * 把 MaidSoulCore 运行时需要的“事件、上下文、工具”注册到
 * Touhou Little Maid 提供的扩展点里。
 * <p>
 * 之所以单独留一个桥接接口，是为了把纯 Java 核心和
 * Forge/TLM 细节隔离开，后续即使换别的宿主也能复用核心层。
 */
public interface TlmBridge {
    /**
     * 注册 Forge / TLM 事件监听。
     */
    void registerEvents();

    /**
     * 注册给大模型按需读取的上下文分类与上下文项。
     */
    void registerContexts();

    /**
     * 注册可供大模型调用的原子工具。
     */
    void registerTools();
}
