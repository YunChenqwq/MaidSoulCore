package com.maidsoul.brain.memory.v2;

/**
 * 统一检索命中。
 */
public record MemorySearchHit(
        String type,
        String id,
        String content,
        double score,
        String source,
        String metadata
) {
    String render(int index) {
        String clean = content == null ? "" : content.replace('\n', ' ').trim();
        if (clean.length() > 180) {
            clean = clean.substring(0, 180) + "...";
        }
        return index + ". [" + type + " score=" + String.format(java.util.Locale.ROOT, "%.2f", score) + "] " + clean;
    }
}
