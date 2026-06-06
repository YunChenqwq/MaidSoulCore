package com.maidsoul.brain.forge.network;

import com.maidsoul.brain.forge.vision.MaidVisionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record VisionCaptureResultPacket(
        UUID maidUuid,
        String reason,
        String sceneHint,
        String imageFormat,
        String imageBase64
) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUtf(reason);
        buffer.writeUtf(sceneHint == null ? "" : sceneHint, 4096);
        buffer.writeUtf(imageFormat);
        buffer.writeUtf(imageBase64, 262144);
    }

    public static VisionCaptureResultPacket decode(FriendlyByteBuf buffer) {
        return new VisionCaptureResultPacket(
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf(4096),
                buffer.readUtf(),
                buffer.readUtf(262144)
        );
    }

    public static void handle(VisionCaptureResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) {
                MaidVisionService.receiveImage(player, message.maidUuid(), message.reason(), message.imageFormat(), message.imageBase64(), message.sceneHint());
            }
        });
        context.setPacketHandled(true);
    }
}
