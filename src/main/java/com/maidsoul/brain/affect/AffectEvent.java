package com.maidsoul.brain.affect;

/**
 * 情绪层的结构化输入事件。
 *
 * <p>核心情绪积分器只接受这种事件，不直接读取自然语言文本。这样后续无论事件来自
 * planner、工具调用、视觉观察、回复效果追踪，还是更完整的语义分析器，都可以走同一条入口。</p>
 */
public record AffectEvent(
        AffectEventKind kind,
        int intensity,
        String source,
        String note
) {
    public AffectEvent {
        intensity = Math.max(0, Math.min(100, intensity));
        source = source == null ? "" : source;
        note = note == null ? "" : note;
    }

    public static AffectEvent of(AffectEventKind kind, int intensity, String source, String note) {
        return new AffectEvent(kind, intensity, source, note);
    }
}
