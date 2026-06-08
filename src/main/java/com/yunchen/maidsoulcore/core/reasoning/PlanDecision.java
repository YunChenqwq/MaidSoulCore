package com.yunchen.maidsoulcore.core.reasoning;

import com.yunchen.maidsoulcore.core.event.StructuredEvent;

public final class PlanDecision {
    public String action = "reply";
    public String target_message_id = "";
    public int wait_seconds = 0;
    public String memory_query = "";
    public String reference_info = "";
    public String reason = "";

    /**
     * Planner 对“最新用户话语在关系/情绪层意味着什么”的结构化判断。
     *
     * <p>这里故意只保存事件 id，不在情绪引擎里做任何关键词匹配。也就是说：
     * 文本语义由 planner/工具循环理解，AffectEngine 只消费这个结构事件并更新数值。</p>
     *
     * <p>可用值见 AffectiveEvent.id()，常用：
     * affection、apology、reject、fight、care、initiate、neutral_world。</p>
     */
    public String affect_event = "";

    /**
     * planner 对 affect_event 的置信度，0.0 到 1.0。
     *
     * <p>运行时会设置门槛，低置信度只作为规划理由，不会真的写入情绪状态，
     * 避免模型随手猜测导致关系状态乱跳。</p>
     */
    public double affect_confidence = 0.0D;

    /**
     * 一句简短证据，写明 planner 为什么认为这是该事件。
     *
     * <p>这不是给玩家看的台词，而是给 trace、记忆证据和后续调试看的。</p>
     */
    public String affect_evidence = "";

    /**
     * 新版统一结构事件包。
     *
     * <p>后续情绪、记忆、图谱都优先消费这个对象。上面的 affect_event 三字段保留为
     * 迁移期 fallback，避免某些模型暂时没有按新 schema 输出时整条链路断掉。</p>
     */
    public StructuredEvent event;
}
