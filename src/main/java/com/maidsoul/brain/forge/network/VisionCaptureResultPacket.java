package com.maidsoul.brain.forge.network;

import com.maidsoul.brain.forge.vision.MaidVisionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record VisionCaptureResultPacket(
        UUID maidUuid,
        UUID requestId,
        String reason,
        String sceneHint,
        String summary
) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUUID(requestId);
        buffer.writeUtf(reason);
        buffer.writeUtf(sceneHint == null ? "" : sceneHint, 4096);
        buffer.writeUtf(summary == null ? "" : summary, 8192);
    }

    public static VisionCaptureResultPacket decode(FriendlyByteBuf buffer) {
        return new VisionCaptureResultPacket(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf(4096),
                buffer.readUtf(8192)
        );
    }

    public static void handle(VisionCaptureResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) {
                MaidVisionService.receiveSummary(player, message.maidUuid(), message.requestId(), message.reason(), message.summary(), message.sceneHint());
            }
        });
        context.setPacketHandled(true);
    }
}
