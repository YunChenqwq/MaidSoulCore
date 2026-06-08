package com.yunchen.maidsoulcore.core.prompt;

import java.util.Map;

public final class PromptRenderer {
    private PromptRenderer() {
    }

    public static String render(String template, Map<String, String> values) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
