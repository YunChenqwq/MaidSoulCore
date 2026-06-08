package com.maidsoulcore.event;

/**
 * 事件优先级分级。
 */
public enum EventPriority {
    /** 生存/战斗类高优先级事件。 */
    P0,
    /** 行为策略类中优先级事件。 */
    P1,
    /** 社交叙事类低优先级事件。 */
    P2
}
