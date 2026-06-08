package com.yunchen.maidsoulcore.core.memory;

public enum MemoryCategory {
    SHORT_CONTEXT("short_context"),
    OWNER_PROFILE("owner_profile"),
    RELATION_EVENT("relation_event"),
    MAID_SELF("maid_self"),
    WORLD_FACT("world_fact"),
    REPAIR_RECORD("repair_record"),
    PROMISE("promise"),
    MEMORY_ANCHOR("memory_anchor"),
    ERROR_MARK("error_mark");

    private final String id;

    MemoryCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
