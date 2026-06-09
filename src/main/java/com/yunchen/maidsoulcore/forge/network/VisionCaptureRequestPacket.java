package com.yunchen.maidsoulcore.forge.network;

import com.yunchen.maidsoulcore.forge.client.VisionCaptureClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record VisionCaptureRequestPacket(
        UUID maidUuid,
        UUID requestId,
        String reason,
        String sceneHint,
        int maxWidth,
        int maxHeight,
        float jpegQuality
) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUUID(requestId);
        buffer.writeUtf(reason);
        buffer.writeUtf(sceneHint == null ? "" : sceneHint, 4096);
        buffer.writeVarInt(maxWidth);
        buffer.writeVarInt(maxHeight);
        buffer.writeFloat(jpegQuality);
    }

    public static VisionCaptureRequestPacket decode(FriendlyByteBuf buffer) {
        return new VisionCaptureRequestPacket(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf(4096),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readFloat()
        );
    }

    public static void handle(VisionCaptureRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VisionCaptureClient.captureAndSend(message)));
        context.setPacketHandled(true);
    }
}


