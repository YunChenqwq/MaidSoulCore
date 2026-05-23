package com.maidsoul.brain.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;
import com.maidsoul.brain.forge.soul.SoulBindResult;
import com.maidsoul.brain.forge.soul.SoulBindingData;
import com.maidsoul.brain.forge.soul.SoulStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SoulBindingActionPacket(UUID maidUuid, String action, String soulId, String displayName) {
    public static SoulBindingActionPacket create(UUID maidUuid, String soulId, String displayName) {
        return new SoulBindingActionPacket(maidUuid, "create", soulId, displayName);
    }

    public static SoulBindingActionPacket bind(UUID maidUuid, String soulId) {
        return new SoulBindingActionPacket(maidUuid, "bind", soulId, "");
    }

    public static SoulBindingActionPacket unbind(UUID maidUuid) {
        return new SoulBindingActionPacket(maidUuid, "unbind", "", "");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUtf(action);
        buffer.writeUtf(soulId);
        buffer.writeUtf(displayName);
    }

    public static SoulBindingActionPacket decode(FriendlyByteBuf buffer) {
        return new SoulBindingActionPacket(buffer.readUUID(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(maidUuid);
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        if (maid.getOwner() != null && !maid.getOwner().getUUID().equals(player.getUUID())) {
            return;
        }
        if ("unbind".equals(action)) {
            SoulBindingData.clear(maid.getPersistentData());
            MaidBrainRuntimeRegistry.invalidate(maid);
            MaidBrainRuntimeRegistry.receiveWorldEvent(maid, "soul.unbound", "maidUuid=" + maid.getUUID() + ", ownerUuid=" + player.getUUID());
        } else {
            SoulBindingData previous = SoulBindingData.fromTag(maid.getPersistentData());
            SoulBindingData next = SoulBindingData.create(soulId, player.getUUID(), maid.getUUID(), MaidBrainRuntimeRegistry.worldIdFor(maid));
            next.writeTo(maid.getPersistentData());
            SoulBindResult result = SoulStore.global().bind(soulId, displayName, previous, next);
            MaidBrainRuntimeRegistry.invalidate(maid);
            MaidBrainRuntimeRegistry.receiveWorldEvent(maid, result.eventType(), result.eventDetail());
        }
        ModNetwork.CHANNEL.sendTo(
                new SoulBindingListResponsePacket(maid.getUUID(), SoulBindingListResponsePacket.capture(maid)),
                player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
