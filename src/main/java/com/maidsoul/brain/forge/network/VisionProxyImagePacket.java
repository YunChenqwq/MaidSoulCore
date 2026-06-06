package com.maidsoul.brain.forge.network;

import com.maidsoul.brain.forge.vision.MaidVisionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端代理模式的图片回传包。
 *
 * <p>默认的 client_direct 模式不会使用这个包。它只在 vision.mode=server_proxy 时启用，
 * 用于服务器统一持有视觉模型 API Key 的特殊场景。</p>
 */
public record VisionProxyImagePacket(
        UUID maidUuid,
        UUID requestId,
        String reason,
        String sceneHint,
        String imageFormat,
        String imageBase64
) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUUID(requestId);
        buffer.writeUtf(reason);
        buffer.writeUtf(sceneHint == null ? "" : sceneHint, 4096);
        buffer.writeUtf(imageFormat);
        buffer.writeUtf(imageBase64, 262144);
    }

    public static VisionProxyImagePacket decode(FriendlyByteBuf buffer) {
        return new VisionProxyImagePacket(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf(4096),
                buffer.readUtf(),
                buffer.readUtf(262144)
        );
    }

    public static void handle(VisionProxyImagePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) {
                MaidVisionService.receiveProxyImage(player, message.maidUuid(), message.requestId(), message.reason(), message.imageFormat(), message.imageBase64(), message.sceneHint());
            }
        });
        context.setPacketHandled(true);
    }
}
