package com.yunchen.maidsoulcore.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.soul.SoulBindingService;
import com.yunchen.maidsoulcore.forge.tlm.MaidSoulTlmBootstrapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
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
        Optional<EntityMaid> selected = selectMaid(player);
        if (selected.isEmpty()) {
            player.sendSystemMessage(Component.literal("还没有可对话的灵魂女仆。请先用灵魂核心右键你的车万女仆完成第一次注册。"));
            return;
        }
        EntityMaid maid = selected.get();
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
        ModNetwork.CHANNEL.sendTo(
                new MaidSoulChatScreenPacket(maid, player),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }

    private Optional<EntityMaid> selectMaid(ServerPlayer player) {
        if (maidEntityId >= 0) {
            Entity entity = player.level().getEntity(maidEntityId);
            if (entity instanceof EntityMaid maid) {
                if (SoulBindingService.isRegisteredFor(player, maid)) {
                    return Optional.of(maid);
                }
                player.sendSystemMessage(Component.literal("这只女仆还没有注册灵魂核心，不能直接对话。先用灵魂核心右键她完成注册。"));
            }
            return Optional.empty();
        }
        return SoulBindingService.firstRegisteredMaid(player);
    }
}


