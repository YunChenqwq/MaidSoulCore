package com.maidsoul.brain.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class ConfigFiles {
    private ConfigFiles() {
    }

    static Properties load(Path path) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置失败: " + path, e);
        }
    }

    static String text(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(text(properties, key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static long number(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(text(properties, key, Long.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static double decimal(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(text(properties, key, Double.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static boolean bool(Properties properties, String key, boolean fallback) {
        String value = text(properties, key, Boolean.toString(fallback)).toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on");
    }
}

