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
    }
}
