package com.maidsoul.brain.tool;

public enum ToolParamType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object");

    private final String schemaName;

    ToolParamType(String schemaName) {
        this.schemaName = schemaName;
    }

    public String schemaName() {
        return schemaName;
    }

    public static ToolParamType normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        return switch (value) {
            case "integer", "int" -> INTEGER;
            case "number", "float" -> NUMBER;
            case "boolean", "bool" -> BOOLEAN;
            case "array" -> ARRAY;
            case "object" -> OBJECT;
            default -> STRING;
        };
    }
}

