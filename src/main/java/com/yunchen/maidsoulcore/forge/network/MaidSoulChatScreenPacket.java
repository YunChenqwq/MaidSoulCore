package com.yunchen.maidsoulcore.forge.network;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.ClientAvailableSitesSync;
import com.github.tartaricacid.touhoulittlemaid.capability.ChatTokensCapability;
import com.github.tartaricacid.touhoulittlemaid.capability.ChatTokensCapabilityProvider;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.client.MaidSoulAIChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 打开 MaidSoulCore 专用聊天屏。
 *
 * <p>数据格式刻意贴近 TLM 的 SyncMaidAIDataMessage：仍然同步女仆 AI 配置、站点列表和 token 信息，
 * 让原版 AIChatScreen 的下拉框、历史按钮等 UI 继续能正常初始化；区别只在客户端最终打开的是
 * MaidSoulAIChatScreen，它会把 Enter 发送改到 MaidSoulCore 自己的 packet。</p>
 */
public record MaidSoulChatScreenPacket(int entityId, CompoundTag configData, int currentTokens, int maxTokens) {
    public MaidSoulChatScreenPacket(EntityMaid maid, ServerPlayer player) {
        this(maid.getId(), maid.getAiChatManager().writeToTag(new CompoundTag()),
                player.getCapability(ChatTokensCapabilityProvider.CHAT_TOKENS_CAP).map(ChatTokensCapability::getCount).orElse(0),
                AIConfig.MAX_TOKENS_PER_PLAYER.get());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeNbt(configData);
        ClientAvailableSitesSync.writeToNetwork(buffer);
        buffer.writeVarInt(currentTokens);
        buffer.writeVarInt(maxTokens);
    }

    public static MaidSoulChatScreenPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        CompoundTag configData = Objects.requireNonNullElse(buffer.readNbt(), new CompoundTag());
        ClientAvailableSitesSync.readFromNetwork(buffer);
        int currentTokens = buffer.readVarInt();
        int maxTokens = buffer.readVarInt();
        return new MaidSoulChatScreenPacket(entityId, configData, currentTokens, maxTokens);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> handleClient(this));
        }
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(MaidSoulChatScreenPacket message) {
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        if (level == null || player == null) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        Entity entity = level.getEntity(message.entityId);
        if (entity instanceof EntityMaid maid) {
            maid.getAiChatManager().readFromTag(message.configData);
            MaidSoulAIChatScreen chatScreen = new MaidSoulAIChatScreen(maid);
            chatScreen.updateTokens(message.currentTokens, message.maxTokens);
            Minecraft.getInstance().setScreen(chatScreen);
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }
}


