package com.maidsoul.brain.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SoulBindingListRequestPacket(UUID maidUuid) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
    }

    public static SoulBindingListRequestPacket decode(FriendlyByteBuf buffer) {
        return new SoulBindingListRequestPacket(buffer.readUUID());
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
        ModNetwork.CHANNEL.sendTo(
                new SoulBindingListResponsePacket(maidUuid, SoulBindingListResponsePacket.capture(maid)),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
