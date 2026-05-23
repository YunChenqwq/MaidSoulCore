package com.maidsoul.brain.memory;

import com.maidsoul.brain.affect.AffectProfile;
import com.maidsoul.brain.util.JsonText;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每日记忆整理。
 *
 * <p>当前版本先做本地规则摘要：把今天的高重要度事件、情绪高点和画像变化写成 daily summary。
 * 后续可以把 summary 文本替换成 LLM 归纳，但落盘格式保持不变。</p>
 */
public final class DailyMemoryConsolidator {
    private final Path dailyDir;

    public DailyMemoryConsolidator(Path dailyDir) {
        this.dailyDir = dailyDir;
    }

    public void refreshToday(List<LifeMemory> memories, AffectProfile affect, UserProfile profile) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<LifeMemory> todayMemories = memories.stream()
                .filter(memory -> isSameLocalDate(memory.time, today))
                .toList();
        if (todayMemories.isEmpty()) {
            return;
        }
        String summary = buildSummary(todayMemories);
        List<String> carePoints = buildCarePoints(todayMemories, affect, profile);
        write(today, summary, carePoints, affect);
    }

    private String buildSummary(List<LifeMemory> memories) {
        List<LifeMemory> important = memories.stream()
                .sorted(Comparator.comparingInt((LifeMemory memory) -> memory.importance).reversed())
                .limit(5)
                .toList();
        StringBuilder builder = new StringBuilder();
        builder.append("今天记录了 ").append(memories.size()).append(" 条经历。");
        Map<MemoryType, Long> counts = memories.stream()
                .collect(Collectors.groupingBy(memory -> memory.type, Collectors.counting()));
        if (!counts.isEmpty()) {
            builder.append(" 主要类型：");
            counts.forEach((type, count) -> builder.append(type.name().toLowerCase(Locale.ROOT)).append("=").append(count).append(" "));
        }
        if (!important.isEmpty()) {
            builder.append(" 重要片段：");
            for (LifeMemory memory : important) {
                builder.append("「").append(clip(memory.content, 80)).append("」");
            }
        }
        return builder.toString().trim();
    }

    private List<String> buildCarePoints(List<LifeMemory> memories, AffectProfile affect, UserProfile profile) {
        java.util.ArrayList<String> points = new java.util.ArrayList<>();
        boolean hasStress = memories.stream().anyMatch(memory -> memory.tags.contains("stress_response")
                || memory.tags.contains("repair_debt")
                || memory.tags.contains("affect_event"));
        boolean hasStyle = memories.stream().anyMatch(memory -> memory.tags.contains("conversation_style"));
        if (hasStress) {
            points.add("下次可以先轻轻确认玩家现在心情有没有好一点，不要一上来讲方案。");
        }
        if (hasStyle) {
            points.add("玩家在意聊天节奏和自然感，主动时要少而准。");
        }
        if (affect.hurt >= 30 || affect.anger >= 30) {
            points.add("关系里还有受伤或生气的残留，不要假装一切已经没发生。");
        }
        if (!profile.preferences.isEmpty()) {
            points.add("回复时优先参考用户画像里置信度最高的偏好。");
        }
        return points.stream().limit(5).toList();
    }

    private void write(LocalDate date, String summary, List<String> carePoints, AffectProfile affect) {
        try {
            Files.createDirectories(dailyDir);
            Path path = dailyDir.resolve(date + ".json");
            String json = "{\n"
                    + "  \"date\": \"" + date + "\",\n"
                    + "  \"summary\": \"" + JsonText.escape(summary) + "\",\n"
                    + "  \"affect\": \"" + JsonText.escape(affect.brief()) + "\",\n"
                    + "  \"carePoints\": \"" + JsonText.escape(String.join("\\n", carePoints)) + "\"\n"
                    + "}\n";
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("保存每日记忆整理失败: " + dailyDir, e);
        }
    }

    private static String clip(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static boolean isSameLocalDate(String time, LocalDate date) {
        if (time == null || time.isBlank()) {
            return false;
        }
        try {
            LocalDate local = Instant.parse(time).atZone(ZoneId.systemDefault()).toLocalDate();
            return date.equals(local);
        } catch (Exception ignored) {
            return time.startsWith(date.toString());
        }
    }
}
