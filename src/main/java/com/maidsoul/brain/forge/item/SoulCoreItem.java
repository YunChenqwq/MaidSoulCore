package com.maidsoul.brain.forge.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.menu.SoulBindingMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class SoulCoreItem extends Item {
    public SoulCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (maid.getOwner() != null && !maid.getOwner().getUUID().equals(player.getUUID())) {
            serverPlayer.sendSystemMessage(Component.literal("这只女仆已经有主人了，不能绑定她的灵魂。"));
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, new SoulBindingMenuProvider(maid), buffer -> buffer.writeUUID(maid.getUUID()));
        return InteractionResult.CONSUME;
    }
}
