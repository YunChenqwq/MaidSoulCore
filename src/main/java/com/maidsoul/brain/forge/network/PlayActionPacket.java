package com.maidsoul.brain.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C：服务端→客户端动作命令。
 * 后期真 AI 通过同一网络包触发动作。
 */
public record PlayActionPacket(String actionType, String actionName, float duration) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(actionType);
        buf.writeUtf(actionName);
        buf.writeFloat(duration);
    }

    public static PlayActionPacket decode(FriendlyByteBuf buf) {
        return new PlayActionPacket(buf.readUtf(), buf.readUtf(), buf.readFloat());
    }

    /** 客户端收包后由 ClientActionHandler 统一分发 */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> ClientActionHandler.handle(this, context.getSender()));
        context.setPacketHandled(true);
    }
}
