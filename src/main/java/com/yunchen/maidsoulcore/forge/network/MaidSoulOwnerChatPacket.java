package com.yunchen.maidsoulcore.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.runtime.MaidBrainRuntimeRegistry;
import com.yunchen.maidsoulcore.forge.soul.SoulBindingService;
import com.yunchen.maidsoulcore.forge.tlm.MaidSoulTlmBootstrapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * MaidSoulCore 自己的主人输入包。
 *
 * <p>这条链路不经过 TLM 的 SendUserChatMessage / MaidAIChatManager.chat，
 * 因而不会再被 TLM 的 LLM 站点、人设生成、上下文包装或 token 检查吞掉。</p>
 */
public record MaidSoulOwnerChatPacket(int maidEntityId, String message) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(maidEntityId);
        buffer.writeUtf(message);
    }

    public static MaidSoulOwnerChatPacket decode(FriendlyByteBuf buffer) {
        return new MaidSoulOwnerChatPacket(buffer.readVarInt(), buffer.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        Entity entity = player.level().getEntity(maidEntityId);
        if (!(entity instanceof EntityMaid maid) || !SoulBindingService.isRegisteredFor(player, maid)) {
            player.sendSystemMessage(Component.literal("这只女仆还没有注册灵魂核心，不能进入 MaidSoulCore 对话。"));
            return;
        }
        String clean = message == null ? "" : message.strip();
        if (clean.isBlank()) {
            return;
        }
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
        MaidBrainRuntimeRegistry.beginThinking(maid);
        maid.getAiChatManager().addUserHistory(clean);
        MaidBrainRuntimeRegistry.receiveOwnerChat(maid, clean);
    }
}


