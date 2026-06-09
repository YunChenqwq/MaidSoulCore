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
        sanitizeGrounding(completed);
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

    private static void sanitizeGrounding(StructuredEvent event) {
        if (event == null || event.sourceText == null || event.sourceText.isBlank()) {
            return;
        }
        String source = event.sourceText.trim();
        if (event.evidence != null && event.evidence.length() > 180) {
            event.evidence = source;
        }
        if (event.summary == null || event.summary.isBlank()) {
            event.summary = conservativeSummary(event, source);
            return;
        }
        if (groundingScore(event.summary, source) < 0.16D) {
            event.summary = conservativeSummary(event, source);
            event.importance = Math.min(event.importance, 0.80D);
        }
    }

    private static String conservativeSummary(StructuredEvent event, String source) {
        String type = event == null || event.type == null ? "neutral_world" : event.type;
        String cleanSource = source == null ? "" : source.trim();
        if (cleanSource.length() > 80) {
            cleanSource = cleanSource.substring(0, 80) + "...";
        }
        return "基于原文记录的事件：" + cleanSource + "（type=" + type + "）";
    }

    private static double groundingScore(String summary, String source) {
        String a = normalize(summary);
        String b = normalize(source);
        if (a.isBlank() || b.isBlank()) {
            return 0.0D;
        }
        if (a.contains(b) || b.contains(a)) {
            return 1.0D;
        }
        java.util.Set<String> left = ngrams(a, 2);
        java.util.Set<String> right = ngrams(b, 2);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0D;
        }
        int hit = 0;
        for (String value : left) {
            if (right.contains(value)) {
                hit++;
            }
        }
        return hit / (double) Math.max(1, Math.min(left.size(), right.size()));
    }

    private static java.util.Set<String> ngrams(String text, int n) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) {
            return out;
        }
        if (cps.length <= n) {
            out.add(text);
            return out;
        }
        for (int i = 0; i <= cps.length - n; i++) {
            out.add(new String(cps, i, n));
        }
        return out;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "").trim();
    }
}
