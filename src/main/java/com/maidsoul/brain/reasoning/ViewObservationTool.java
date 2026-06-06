package com.maidsoul.brain.reasoning;

/**
 * Runtime-provided view observation tool.
 *
 * <p>The core only exposes this as a planner tool. It does not infer user
 * intent with keyword rules. If the planner calls observe_view, the outer
 * runtime performs the platform-specific observation and returns a text
 * summary for the current reasoning round.</p>
 */
public interface ViewObservationTool {
    ViewObservationTool NONE = new ViewObservationTool() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String observe(String reason, long timeoutMillis) {
            return "view observation tool is not connected";
        }
    };

    boolean available();

    String observe(String reason, long timeoutMillis);
}
