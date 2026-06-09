package com.maidsoul.brain.text;

import com.maidsoul.brain.config.SplitterConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 自然分句器。
 *
 * <p>分句只影响输出节奏，不改变回复含义。它优先按中文/日文/英文句末标点切开，
 * 并用最大长度兜底，避免一整段文字塞进一个气泡。</p>
 */
public final class SentenceSplitter {
    private final SplitterConfig config;

    public SentenceSplitter(SplitterConfig config) {
        this.config = config;
    }

    public List<String> split(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            return List.of();
        }
        if (!config.enable()) {
            return List.of(text);
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current.append(ch);
            if (ch == '（' || ch == '(' || ch == '「' || ch == '『') {
                bracketDepth++;
            } else if ((ch == '）' || ch == ')' || ch == '」' || ch == '』') && bracketDepth > 0) {
                bracketDepth--;
            }
            if (bracketDepth == 0 && shouldCut(ch, current.length())) {
                addSegment(result, current.toString());
                current.setLength(0);
                if (result.size() >= config.maxSentenceNum()) {
                    break;
                }
            }
        }
        if (current.length() > 0 && result.size() < config.maxSentenceNum()) {
            addSegment(result, current.toString());
        }
        return result.isEmpty() ? List.of(text) : result;
    }

    private boolean shouldCut(char ch, int currentLength) {
        if (currentLength >= Math.max(10, config.maxLength())) {
            return true;
        }
        return ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '\n';
    }

    private void addSegment(List<String> result, String segment) {
        String text = segment == null ? "" : segment.trim();
        if (text.length() < Math.max(1, config.minSegmentLength())) {
            return;
        }
        result.add(text);
    }
}

