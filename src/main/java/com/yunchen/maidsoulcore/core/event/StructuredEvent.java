package com.yunchen.maidsoulcore.core.event;

import java.util.UUID;

public final class StructuredEvent {
    public String id = UUID.randomUUID().toString();
    public String type = StructuredEventType.NEUTRAL_WORLD.id();
    public String scope = StructuredEventScope.UNKNOWN.id();
    public String subject = "";
    public String object = "";
    public String summary = "";
    public String evidence = "";
    public String sourceText = "";
    public double confidence = 0.0D;
    public double importance = 0.0D;
    public String memoryCategory = "";
    public boolean shouldWriteMemory = false;
    public boolean shouldUpdateAffect = false;
    public long happenedAtEpochMillis = System.currentTimeMillis();

    public StructuredEventType typeEnum() {
        return StructuredEventType.fromId(type);
    }

    public StructuredEventScope scopeEnum() {
        return StructuredEventScope.fromId(scope);
    }

    public void normalize() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        type = StructuredEventType.fromId(type).id();
        scope = StructuredEventScope.fromId(scope).id();
        subject = blankToEmpty(subject);
        object = blankToEmpty(object);
        summary = blankToEmpty(summary);
        evidence = blankToEmpty(evidence);
        sourceText = blankToEmpty(sourceText);
        memoryCategory = blankToEmpty(memoryCategory);
        confidence = clamp01(confidence);
        importance = clamp01(importance);
        if (happenedAtEpochMillis <= 0L) {
            happenedAtEpochMillis = System.currentTimeMillis();
        }
    }

    public boolean isMeaningful() {
        normalize();
        return typeEnum() != StructuredEventType.NEUTRAL_WORLD
                && typeEnum() != StructuredEventType.OWNER_MESSAGE
                && typeEnum() != StructuredEventType.LONG_MESSAGE;
    }

    public String brief() {
        normalize();
        return "type=" + type
                + " scope=" + scope
                + " subject=" + subject
                + " object=" + object
                + " confidence=" + String.format(java.util.Locale.ROOT, "%.2f", confidence)
                + " importance=" + String.format(java.util.Locale.ROOT, "%.2f", importance)
                + " category=" + memoryCategory
                + " summary=" + summary
                + " evidence=" + evidence;
    }

    public static StructuredEvent fromLegacy(
            String type,
            double confidence,
            String evidence,
            String subject,
            String object,
            String sourceText
    ) {
        StructuredEvent event = new StructuredEvent();
        event.type = type;
        event.scope = StructuredEventScope.OWNER_TO_MAID.id();
        event.subject = subject;
        event.object = object;
        event.evidence = evidence;
        event.summary = evidence;
        event.sourceText = sourceText;
        event.confidence = confidence;
        event.importance = EventImportancePolicy.defaultImportance(StructuredEventType.fromId(type), confidence);
        event.memoryCategory = EventImportancePolicy.defaultMemoryCategory(StructuredEventType.fromId(type));
        event.shouldUpdateAffect = event.isMeaningful();
        event.shouldWriteMemory = EventImportancePolicy.shouldWriteMemory(StructuredEventType.fromId(type), confidence, event.importance);
        event.normalize();
        return event;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
