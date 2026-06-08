package com.maidsoulcore.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文本模拟器转录日志。
 * <p>
 * 所有对话和事件都会以 UTF-8 追加写入文件，
 * 这样即便终端炸了，日志文件仍然是干净可读的。
 */
public final class SimulationTranscriptLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logPath;

    public SimulationTranscriptLogger(Path logPath) {
        this.logPath = logPath;
    }

    public Path logPath() {
        return logPath;
    }

    /**
     * 追加一行日志。
     */
    public synchronized void append(String line) {
        try {
            Files.createDirectories(logPath.getParent());
            Files.writeString(
                    logPath,
                    "[" + LocalDateTime.now().format(FORMATTER) + "] " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }
}
