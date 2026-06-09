package com.yunchen.maidsoulcore.forge.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.menu.SoulBindingMenuProvider;
import com.yunchen.maidsoulcore.forge.soul.SoulBindResult;
import com.yunchen.maidsoulcore.forge.soul.SoulBindingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

public final class SoulCoreItem extends Item {
    public SoulCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("\u7528\u7075\u9b42\u6838\u5fc3\u53f3\u952e\u4f60\u7684\u8f66\u4e07\u5973\u4ec6\u5373\u53ef\u7ed1\u5b9a\uff1b\u6f5c\u884c\u53f3\u952e\u5973\u4ec6\u4f1a\u76f4\u63a5\u7ed1\u5b9a\u9ed8\u8ba4\u89d2\u8272\u5305\u3002"));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
        if (!SoulBindingService.canOperate(serverPlayer, maid)) {
            SoulBindingService.sendNotOwnerMessage(serverPlayer);
            return InteractionResult.CONSUME;
        }

        // 潜行右键是快速路径：不开 GUI，直接把默认角色包绑定到这只女仆。
        if (player.isShiftKeyDown()) {
            SoulBindResult result = SoulBindingService.bindDefault(serverPlayer, maid);
            serverPlayer.sendSystemMessage(Component.literal(
                    "\u5df2\u5c06\u7075\u9b42\u6838\u5fc3 ["
                            + result.record().displayName()
                            + "] \u7ed1\u5b9a\u5230\u8fd9\u53ea\u5973\u4ec6\u3002\u4e8b\u4ef6: "
                            + result.eventType()
            ));
            return InteractionResult.CONSUME;
        }

        NetworkHooks.openScreen(serverPlayer, new SoulBindingMenuProvider(maid), buffer -> buffer.writeUUID(maid.getUUID()));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\u53f3\u952e\u5973\u4ec6\uff1a\u6253\u5f00\u7075\u9b42\u7ed1\u5b9a\u754c\u9762"));
        tooltip.add(Component.literal("\u6f5c\u884c\u53f3\u952e\u5973\u4ec6\uff1a\u5feb\u901f\u7ed1\u5b9a\u9ed8\u8ba4\u89d2\u8272\u5305"));
    }
}


