package com.maidsoul.brain.reasoning;

/**
 * Runtime-provided structured Minecraft environment scanner.
 *
 * <p>This is the cheap, deterministic sibling of {@link ViewObservationTool}:
 * it reads game state through platform APIs instead of asking a vision model.
 * The planner can call it when it needs nearby entities, focus target, weather,
 * time, owner state, or maid identity without paying for a screenshot/VLM round.</p>
 */
public interface EnvironmentObservationTool {
    EnvironmentObservationTool NONE = new EnvironmentObservationTool() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String scan(String reason) {
            return "environment observation tool is not connected";
        }
    };

    boolean available();

    String scan(String reason);
}
