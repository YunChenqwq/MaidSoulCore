package com.yunchen.maidsoulcore.forge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.soul.LegacyMaidMemoryMigrator;
import com.yunchen.maidsoulcore.forge.soul.SoulBindingService;
import com.yunchen.maidsoulcore.forge.runtime.MaidBrainRuntimeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SoulBindingActionPacket(UUID maidUuid, String action, String soulId, String displayName) {
    public static SoulBindingActionPacket create(UUID maidUuid, String soulId, String displayName) {
        return new SoulBindingActionPacket(maidUuid, "create", soulId, displayName);
    }

    public static SoulBindingActionPacket bind(UUID maidUuid, String soulId) {
        return new SoulBindingActionPacket(maidUuid, "bind", soulId, "");
    }

    public static SoulBindingActionPacket unbind(UUID maidUuid) {
        return new SoulBindingActionPacket(maidUuid, "unbind", "", "");
    }

    public static SoulBindingActionPacket migrateLegacy(UUID maidUuid, String soulId) {
        return new SoulBindingActionPacket(maidUuid, "migrate_legacy", soulId, "");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(maidUuid);
        buffer.writeUtf(action);
        buffer.writeUtf(soulId);
        buffer.writeUtf(displayName);
    }

    public static SoulBindingActionPacket decode(FriendlyByteBuf buffer) {
        return new SoulBindingActionPacket(buffer.readUUID(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(maidUuid);
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        if (!SoulBindingService.canOperate(player, maid)) {
            SoulBindingService.sendNotOwnerMessage(player);
            return;
        }
        if ("unbind".equals(action)) {
            SoulBindingService.unbind(player, maid);
        } else if ("migrate_legacy".equals(action)) {
            LegacyMaidMemoryMigrator.Result result = LegacyMaidMemoryMigrator.migrateCurrentMaid(maid, soulId);
            if (result.success()) {
                MaidBrainRuntimeRegistry.receiveWorldEvent(maid, "soul.migrated_legacy_maid", result.eventDetail());
                player.sendSystemMessage(Component.literal(result.message()));
            } else {
                player.sendSystemMessage(Component.literal(result.message()));
            }
        } else {
            SoulBindingService.bind(player, maid, soulId, displayName);
        }
        ModNetwork.CHANNEL.sendTo(
                new SoulBindingListResponsePacket(maid.getUUID(), SoulBindingListResponsePacket.capture(maid)),
                player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
        );
    }
}


