package com.maidsoul.brain.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.client.SoulBindingClientState;
import com.maidsoul.brain.forge.soul.SoulBindingData;
import com.maidsoul.brain.forge.soul.SoulStore;
import com.maidsoul.brain.forge.soul.SoulSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record SoulBindingListResponsePacket(UUID maidUuid, Snapshot snapshot) {
    public static Snapshot capture(EntityMaid maid) {
        SoulBindingData binding = SoulBindingData.fromTag(maid.getPersistentData());
        return new Snapshot(
                binding.soulId(),
                binding.bindingId(),
                binding.worldId(),
                SoulStore.global().list()
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUtf(snapshot.currentSoulId());
        buffer.writeUtf(snapshot.bindingId());
        buffer.writeUtf(snapshot.worldId());
        buffer.writeVarInt(snapshot.souls().size());
        for (SoulSummary soul : snapshot.souls()) {
            buffer.writeUtf(soul.soulId());
            buffer.writeUtf(soul.displayName());
            buffer.writeUtf(soul.lastWorldId());
            buffer.writeUtf(soul.lastMaidUuid());
            buffer.writeUtf(soul.lastOwnerUuid());
            buffer.writeLong(soul.lastUsedAt());
        }
    }

    public static SoulBindingListResponsePacket decode(FriendlyByteBuf buffer) {
        UUID maidUuid = buffer.readUUID();
        String currentSoulId = buffer.readUtf();
        String bindingId = buffer.readUtf();
        String worldId = buffer.readUtf();
        int count = buffer.readVarInt();
        List<SoulSummary> souls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            souls.add(new SoulSummary(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readLong()
            ));
        }
        return new SoulBindingListResponsePacket(maidUuid, new Snapshot(currentSoulId, bindingId, worldId, souls));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        SoulBindingClientState.update(this);
    }

    public record Snapshot(String currentSoulId, String bindingId, String worldId, List<SoulSummary> souls) {
    }
}
