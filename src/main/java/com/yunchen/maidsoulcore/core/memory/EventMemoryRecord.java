package com.yunchen.maidsoulcore.core.memory;

import java.time.Instant;
import java.util.UUID;

public final class EventMemoryRecord {
    public String id = UUID.randomUUID().toString();
    public String category = MemoryCategory.RELATION_EVENT.id();
    public String subject = "";
    public String object = "";
    public String eventType = "";
    public String summary = "";
    public String evidence = "";
    public double confidence = 0.0D;
    public double importance = 0.0D;
    public double salience = 0.0D;
    public double decayRate = 0.02D;
    public boolean pinned = false;
    public boolean contradicted = false;
    public boolean errorMarked = false;
    public String sourceMessageId = "";
    public long createdAtEpochMillis = System.currentTimeMillis();
    public long updatedAtEpochMillis = System.currentTimeMillis();
    public int mergeCount = 1;

    public void normalize() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        category = blankToDefault(category, MemoryCategory.RELATION_EVENT.id());
        subject = blankToEmpty(subject);
        object = blankToEmpty(object);
        eventType = blankToEmpty(eventType);
        summary = blankToEmpty(summary);
        evidence = blankToEmpty(evidence);
        confidence = clamp01(confidence);
        importance = clamp01(importance);
        salience = clamp01(salience <= 0.0D ? importance * confidence : salience);
        decayRate = Math.max(0.0D, decayRate);
        if (createdAtEpochMillis <= 0L) {
            createdAtEpochMillis = System.currentTimeMillis();
        }
        if (updatedAtEpochMillis <= 0L) {
            updatedAtEpochMillis = createdAtEpochMillis;
        }
        mergeCount = Math.max(1, mergeCount);
    }

    public String timeIso() {
        return Instant.ofEpochMilli(createdAtEpochMillis).toString();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
