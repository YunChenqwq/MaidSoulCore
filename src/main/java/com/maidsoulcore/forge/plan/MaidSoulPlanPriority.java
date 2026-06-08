package com.maidsoulcore.forge.plan;

/**
 * 计划优先级。
 * <p>
 * 数值越大表示越容易抢占当前计划。
 */
public enum MaidSoulPlanPriority {
    /**
     * 低优先级陪伴类计划，例如环境陪伴、非关键闲聊。
     */
    COMPANION(10),
    /**
     * 普通命令计划，例如主人口头下达的日常动作。
     */
    OWNER_COMMAND(50),
    /**
     * 关键插队计划，例如受击、高风险、紧急保护。
     */
    CRITICAL(100);

    private final int weight;

    MaidSoulPlanPriority(int weight) {
        this.weight = weight;
    }

    /**
     * 返回用于调度比较的权重。
     */
    public int weight() {
        return weight;
    }
}
