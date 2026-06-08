package com.maidsoulcore.adapter.tlm;

import java.util.Map;

/**
 * 从 TLM 实体和世界里抓取运行时快照。
 * <p>
 * 这里的输出最终会进入黑板或上下文系统，
 * 供 Planner、Reply 和调试界面使用。
 */
public interface TlmSnapshotAdapter {
    /**
     * 采集当前女仆与主人相关的状态快照。
     *
     * @param maidId  女仆标识
     * @param ownerId 主人标识
     * @return 结构化状态字段
     */
    Map<String, Object> capture(String maidId, String ownerId);
}
