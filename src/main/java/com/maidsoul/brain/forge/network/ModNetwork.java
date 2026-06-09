package com.maidsoul.brain.forge.network;

import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MaidSoulCoreForgeMod.MOD_ID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SoulBindingListRequestPacket.class, id++)
                .encoder(SoulBindingListRequestPacket::encode)
                .decoder(SoulBindingListRequestPacket::decode)
                .consumerMainThread(SoulBindingListRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(SoulBindingListResponsePacket.class, id++)
                .encoder(SoulBindingListResponsePacket::encode)
                .decoder(SoulBindingListResponsePacket::decode)
                .consumerMainThread(SoulBindingListResponsePacket::handle)
                .add();
        CHANNEL.messageBuilder(SoulBindingActionPacket.class, id++)
                .encoder(SoulBindingActionPacket::encode)
                .decoder(SoulBindingActionPacket::decode)
                .consumerMainThread(SoulBindingActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenMaidSoulChatPacket.class, id++)
                .encoder(OpenMaidSoulChatPacket::encode)
                .decoder(OpenMaidSoulChatPacket::decode)
                .consumerMainThread(OpenMaidSoulChatPacket::handle)
                .add();
        CHANNEL.messageBuilder(MaidSoulChatScreenPacket.class, id++)
                .encoder(MaidSoulChatScreenPacket::encode)
                .decoder(MaidSoulChatScreenPacket::decode)
                .consumerMainThread(MaidSoulChatScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(MaidSoulOwnerChatPacket.class, id++)
                .encoder(MaidSoulOwnerChatPacket::encode)
                .decoder(MaidSoulOwnerChatPacket::decode)
                .consumerMainThread(MaidSoulOwnerChatPacket::handle)
                .add();
        CHANNEL.messageBuilder(VisionCaptureRequestPacket.class, id++)
                .encoder(VisionCaptureRequestPacket::encode)
                .decoder(VisionCaptureRequestPacket::decode)
                .consumerMainThread(VisionCaptureRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(VisionCaptureResultPacket.class, id++)
                .encoder(VisionCaptureResultPacket::encode)
                .decoder(VisionCaptureResultPacket::decode)
                .consumerMainThread(VisionCaptureResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(VisionProxyImagePacket.class, id++)
                .encoder(VisionProxyImagePacket::encode)
                .decoder(VisionProxyImagePacket::decode)
                .consumerMainThread(VisionProxyImagePacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayActionPacket.class, id++)
                .encoder(PlayActionPacket::encode)
                .decoder(PlayActionPacket::decode)
                .consumerMainThread(PlayActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(ActionResultPacket.class, id++)
                .encoder(ActionResultPacket::encode)
                .decoder(ActionResultPacket::decode)
                .consumerMainThread(ActionResultPacket::handle)
                .add();
    }
}
