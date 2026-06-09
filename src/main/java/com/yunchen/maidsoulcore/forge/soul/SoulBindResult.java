package com.yunchen.maidsoulcore.forge.soul;

public record SoulBindResult(
        SoulBindingData binding,
        SoulRecord record,
        String eventType,
        String eventDetail
) {
}


