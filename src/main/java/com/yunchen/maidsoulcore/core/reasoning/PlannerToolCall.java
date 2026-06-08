package com.yunchen.maidsoulcore.core.reasoning;

import com.google.gson.JsonObject;

public final class PlannerToolCall {
    public String thought = "";
    public String tool = "";
    public JsonObject arguments = new JsonObject();

    public String argumentString(String name) {
        if (arguments == null || name == null || !arguments.has(name) || arguments.get(name).isJsonNull()) {
            return "";
        }
        return arguments.get(name).getAsString();
    }

    public int argumentInt(String name, int fallback) {
        if (arguments == null || name == null || !arguments.has(name) || arguments.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return arguments.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
