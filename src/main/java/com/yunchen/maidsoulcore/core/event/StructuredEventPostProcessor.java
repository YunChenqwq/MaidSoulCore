package com.yunchen.maidsoulcore.core.event;

public final class StructuredEventPostProcessor {
    public StructuredEvent complete(
            StructuredEvent event,
            String fallbackType,
            double fallbackConfidence,
            String fallbackEvidence,
            String defaultSubject,
            String defaultObject,
            String sourceText
    ) {
        StructuredEvent completed = event;
        if (completed == null || completed.type == null || completed.type.isBlank()) {
            completed = StructuredEvent.fromLegacy(
                    fallbackType,
                    fallbackConfidence,
                    fallbackEvidence,
                    defaultSubject,
                    defaultObject,
                    sourceText
            );
        }
        completed.normalize();
        if (completed.subject.isBlank()) {
            completed.subject = defaultSubject == null ? "" : defaultSubject;
        }
        if (completed.object.isBlank()) {
            completed.object = defaultObject == null ? "" : defaultObject;
        }
        if (completed.sourceText.isBlank()) {
            completed.sourceText = sourceText == null ? "" : sourceText;
        }
        if (completed.evidence.isBlank()) {
            completed.evidence = fallbackEvidence == null ? "" : fallbackEvidence;
        }
        if (completed.summary.isBlank()) {
            completed.summary = completed.evidence;
        }
        if (completed.confidence <= 0.0D) {
            completed.confidence = fallbackConfidence;
        }
        completed.normalize();

        StructuredEventType type = completed.typeEnum();
        if (completed.importance <= 0.0D && completed.isMeaningful()) {
            completed.importance = EventImportancePolicy.defaultImportance(type, completed.confidence);
        }
        completed.memoryCategory = EventImportancePolicy.defaultMemoryCategory(type, completed.importance);
        completed.shouldUpdateAffect = EventImportancePolicy.shouldUpdateAffect(type, completed.confidence);
        completed.shouldWriteMemory = EventImportancePolicy.shouldWriteMemory(type, completed.confidence, completed.importance);
        completed.normalize();
        return completed;
    }
}
