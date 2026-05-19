package com.maidsoul.brain.memory.v2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 记忆 v2 的稳定哈希工具。
 *
 * <p>A_Memorix 里大量对象靠 hash 做幂等和引用。这里先用 SHA-256 的前 32 位十六进制
 * 字符串做本地原型 ID，既足够稳定，也方便日志阅读。后续如果迁到 SQLite/向量库，
 * 这些 ID 可以继续作为主键。</p>
 */
final class MemoryHash {
    private MemoryHash() {
    }

    static String of(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < bytes.length && builder.length() < 32; i++) {
                builder.append(String.format(java.util.Locale.ROOT, "%02x", bytes[i] & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
