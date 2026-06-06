package com.maidsoul.brain.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.tlm.MaidSoulTlmBootstrapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 打开车万女仆原生 AI 聊天框，但在同步界面数据前先完成 MaidSoulCore 接管。
 *
 * <p>TLM 自己的 OpenMaidAIChatMessage 会直接把女仆 AI 数据同步给客户端；
 * 如果客户端或服务端上女仆还没有被切到 maidsoul_runtime，打开后的聊天框会继续使用旧站点，
 * 甚至因为客户端 LLM 开关没开而根本不发送。这个包只做一件事：先确保接管，再复用
 * TLM 的 SyncMaidAIDataMessage 打开原版界面。</p>
 */
public record OpenMaidSoulChatPacket(int maidEntityId) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(maidEntityId);
    }

    public static OpenMaidSoulChatPacket decode(FriendlyByteBuf buffer) {
        return new OpenMaidSoulChatPacket(buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        Entity entity = player.level().getEntity(maidEntityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !maid.isOwnedBy(player)) {
            return;
        }
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
        ModNetwork.CHANNEL.sendTo(
                new MaidSoulChatScreenPacket(maid, player),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
