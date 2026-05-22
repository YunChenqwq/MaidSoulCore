package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;

/**
 * 记忆维护报告。
 *
 * <p>维护循环会改变记忆状态，所以必须留下可读日志。GUI 和 smoke test 都读取这个对象，
 * 用来确认去重、降权、固化、错误标记有没有真实发生。</p>
 */
public record MemoryMaintenanceReport(
        int scanned,
        int deduplicated,
        int decayed,
        int solidified,
        int correctionMarked,
        int forgotten,
        String detail
) {
    public String toHumanText() {
        return "扫描=" + scanned
                + ", 去重=" + deduplicated
                + ", 降权=" + decayed
                + ", 固化=" + solidified
                + ", 错误/修正标记=" + correctionMarked
                + ", 软删除=" + forgotten
                + (detail == null || detail.isBlank() ? "" : "\n" + detail);
    }

    String toJsonLine() {
        return "{"
                + "\"scanned\":" + scanned + ","
                + "\"deduplicated\":" + deduplicated + ","
                + "\"decayed\":" + decayed + ","
                + "\"solidified\":" + solidified + ","
                + "\"correctionMarked\":" + correctionMarked + ","
                + "\"forgotten\":" + forgotten + ","
                + "\"detail\":\"" + JsonText.escape(detail == null ? "" : detail) + "\","
                + "\"createdAt\":" + MemoryParagraph.now()
                + "}";
    }
}
