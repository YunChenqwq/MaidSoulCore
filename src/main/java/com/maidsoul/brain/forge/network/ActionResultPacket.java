package com.maidsoul.brain.forge.network;

import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端→服务端动作执行结果。
 */
public record ActionResultPacket(String actionName, boolean success, String reason) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(actionName);
        buf.writeBoolean(success);
        buf.writeUtf(reason);
    }

    public static ActionResultPacket decode(FriendlyByteBuf buf) {
        return new ActionResultPacket(buf.readUtf(), buf.readBoolean(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            String status = success ? "SUCCESS" : "FAIL";
            MaidSoulCoreForgeMod.LOGGER.info("[ActionResult] {} {} — {}", status, actionName, reason);
        });
        context.setPacketHandled(true);
    }
}
