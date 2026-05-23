package com.maidsoul.brain.forge.client;

import com.maidsoul.brain.forge.network.SoulBindingListResponsePacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SoulBindingClientState {
    private static final Map<UUID, SoulBindingListResponsePacket.Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private SoulBindingClientState() {
    }

    public static void update(SoulBindingListResponsePacket packet) {
        SNAPSHOTS.put(packet.maidUuid(), packet.snapshot());
    }

    public static SoulBindingListResponsePacket.Snapshot get(UUID maidUuid) {
        return SNAPSHOTS.get(maidUuid);
    }
}
