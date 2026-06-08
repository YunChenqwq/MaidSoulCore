package com.maidsoulcore.sim;

import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.MaidEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SimulationAutoEventDirector {
    private static final String[] HOSTILES = {"zombie", "creeper", "skeleton", "spider"};
    private static final String[] FOODS = {"bread", "cake", "milk", "apple"};
    private static final String[] VISION_SCENES = {
            "The owner is in an open field and the area looks calm.",
            "There are torches and a chest nearby, so the area is safe.",
            "A hostile mob is moving in the distance and needs attention.",
            "The home area is quiet and suitable for standby."
    };

    private final Random random;

    public SimulationAutoEventDirector() {
        this(new Random());
    }

    public SimulationAutoEventDirector(Random random) {
        this.random = random;
    }

    public List<MaidEvent> beforePlayerTurn(SimulationEngine engine, SimulationEnvironment environment) {
        List<MaidEvent> events = new ArrayList<>();
        long delta = 20L + random.nextInt(80);
        boolean wasNight = environment.isNight();
        environment.advanceTick(delta);

        if (!wasNight && environment.isNight()) {
            events.add(engine.newEvent("world.night_falls", EventPriority.P1, Map.of("tick_delta", delta)));
        } else {
            events.add(engine.newEvent("system.proactive_tick", EventPriority.P2, Map.of("tick_delta", delta)));
        }

        if (environment.tick() - environment.lastCaptureTick() >= environment.captureIntervalTicks()) {
            String scene = pick(VISION_SCENES);
            environment.setLastVisionSummary(scene);
            events.add(engine.newEvent("vision.capture", EventPriority.P1, Map.of("summary", scene)));
        }

        if (environment.isNight() && !environment.maidSleeping() && environment.maidEnergy() < 0.22D) {
            events.add(engine.newEvent("maid.sleep.enter", EventPriority.P1, Map.of("reason", "low_energy_night")));
        } else if (!environment.isNight() && environment.maidSleeping()) {
            events.add(engine.newEvent("maid.sleep.exit", EventPriority.P1, Map.of("reason", "daylight")));
        }

        return events;
    }

    public List<MaidEvent> afterPlayerTurn(SimulationEngine engine, SimulationEnvironment environment) {
        List<MaidEvent> events = new ArrayList<>();

        if (environment.maidHunger() > 0.72D && random.nextDouble() < 0.35D) {
            String item = pick(FOODS);
            events.add(engine.newEvent("owner.feed", EventPriority.P1, Map.of("item", item, "source", "owner")));
            return events;
        }

        double roll = random.nextDouble();
        if (roll < 0.12D) {
            events.add(engine.newEvent("owner.interact", EventPriority.P1, Map.of("kind", "touch_head")));
        } else if (roll < 0.24D) {
            String hostile = pick(HOSTILES);
            environment.setNearbyThreat(hostile);
            events.add(engine.newEvent("world.hostile_detected", EventPriority.P1, Map.of("target", hostile)));
        } else if (roll < 0.31D) {
            String hostile = pick(HOSTILES);
            environment.setNearbyThreat(hostile);
            environment.setMaidHealth(environment.maidHealth() - (2.0D + random.nextInt(4)));
            environment.spendEnergy(0.08D);
            events.add(engine.newEvent("maid.attacked", EventPriority.P0, Map.of("source", hostile)));
        } else if (roll < 0.45D) {
            int offsetX = random.nextInt(9) - 4;
            int offsetZ = random.nextInt(9) - 4;
            environment.setOwnerPosition(new SimulationCoordinate(
                    environment.ownerPosition().x() + offsetX,
                    environment.ownerPosition().y(),
                    environment.ownerPosition().z() + offsetZ
            ));
            events.add(engine.newEvent("system.proactive_tick", EventPriority.P2, Map.of("reason", "owner_random_move")));
        }

        return events;
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
