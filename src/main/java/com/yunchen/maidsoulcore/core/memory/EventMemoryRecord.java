package com.yunchen.maidsoulcore.core.memory;

import java.time.Instant;
import java.util.UUID;

public final class EventMemoryRecord {
    public String id = UUID.randomUUID().toString();
    public String category = MemoryCategory.RELATION_EVENT.id();
    public String subject = "";
    public String object = "";
    public String eventType = "";
    public String knowledgeType = "structured";
    public String sourceKind = "dialogue_event";
    public String relationPredicate = "";
    public String summary = "";
    public String evidence = "";
    public String vectorText = "";
    public String vectorState = "none";
    public String writePolicy = "";
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
        knowledgeType = blankToDefault(knowledgeType, "structured");
        sourceKind = blankToDefault(sourceKind, "dialogue_event");
        relationPredicate = blankToDefault(relationPredicate, eventType);
        summary = blankToEmpty(summary);
        evidence = blankToEmpty(evidence);
        vectorState = blankToDefault(vectorState, "none");
        writePolicy = blankToEmpty(writePolicy);
        if (vectorText == null || vectorText.isBlank()) {
            vectorText = buildRelationVectorText(subject, relationPredicate, object, summary);
        } else {
            vectorText = vectorText.trim();
        }
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

    private static String buildRelationVectorText(String subject, String predicate, String object, String summary) {
        String s = blankToEmpty(subject);
        String p = blankToEmpty(predicate);
        String o = blankToEmpty(object);
        String body = blankToEmpty(summary);
        if (s.isBlank() && o.isBlank()) {
            return body;
        }
        return (s + " " + p + " " + o + "\n" + body).trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
