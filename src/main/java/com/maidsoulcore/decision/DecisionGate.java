package com.maidsoulcore.decision;

import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.MaidEvent;

public final class DecisionGate {
    public DecisionResult route(MaidEvent event, BlackboardView blackboard) {
        Object canStartWork = blackboard.state().getOrDefault("maid.can_start_work", Boolean.TRUE);
        Object energyState = blackboard.state().getOrDefault("maid.energy_state", "NORMAL");

        if (event.priority() == EventPriority.P0) {
            return new DecisionResult(DecisionRoute.LOCAL_RULE, "hard realtime event");
        }
        if ("maid.sleep.enter".equals(event.type()) || "maid.sleep.exit".equals(event.type())) {
            return new DecisionResult(DecisionRoute.REPLY_ONLY, "sleep edge event");
        }
        if ("owner.feed".equals(event.type()) || "owner.interact".equals(event.type()) || "owner.talk".equals(event.type())) {
            return new DecisionResult(DecisionRoute.HYBRID_PLAN, "social interaction");
        }
        if (Boolean.FALSE.equals(canStartWork) && !"maid.attacked".equals(event.type())) {
            return new DecisionResult(DecisionRoute.REPLY_ONLY, "low energy, avoid work");
        }
        if (event.priority() == EventPriority.P1) {
            return new DecisionResult(DecisionRoute.HYBRID_PLAN, "planner candidate");
        }
        if ("LOW".equals(energyState) || "EXHAUSTED".equals(energyState)) {
            return new DecisionResult(DecisionRoute.DROP, "low value passive event under low energy");
        }
        if (blackboard.mood().bond() > 0.2d) {
            return new DecisionResult(DecisionRoute.REPLY_ONLY, "social passive event");
        }
        return new DecisionResult(DecisionRoute.DROP, "low value passive event");
    }
}
