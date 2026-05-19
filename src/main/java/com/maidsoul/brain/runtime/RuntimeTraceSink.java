package com.maidsoul.brain.runtime;

import java.time.LocalTime;

@FunctionalInterface
public interface RuntimeTraceSink {
    void trace(String stage, String detail);

    static RuntimeTraceSink noop() {
        return (stage, detail) -> {
        };
    }

    static RuntimeTraceSink console(int maxChars) {
        return (stage, detail) -> {
            String text = detail == null ? "" : detail;
            if (maxChars > 0 && text.length() > maxChars) {
                text = text.substring(0, maxChars) + "...";
            }
            System.out.println("[trace " + LocalTime.now().withNano(0) + "] " + stage + " | " + text);
        };
    }
}

