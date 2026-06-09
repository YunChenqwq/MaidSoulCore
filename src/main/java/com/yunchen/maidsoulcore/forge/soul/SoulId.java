package com.yunchen.maidsoulcore.forge.soul;

import java.util.Locale;

public final class SoulId {
    private SoulId() {
    }

    public static String sanitize(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') {
                builder.append(ch);
            } else if (Character.isWhitespace(ch) || ch == '.' || ch == '/') {
                builder.append('-');
            }
        }
        String id = builder.toString().replaceAll("-+", "-");
        if (id.startsWith("-")) {
            id = id.substring(1);
        }
        if (id.endsWith("-")) {
            id = id.substring(0, id.length() - 1);
        }
        return id.isBlank() ? "soul-" + Long.toUnsignedString(System.currentTimeMillis(), 36) : id;
    }
}


