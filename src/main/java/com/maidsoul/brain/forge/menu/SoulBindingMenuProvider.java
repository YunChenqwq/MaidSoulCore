package com.maidsoul.brain.forge.menu;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class SoulBindingMenuProvider implements MenuProvider {
    private final EntityMaid maid;

    public SoulBindingMenuProvider(EntityMaid maid) {
        this.maid = maid;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("女仆灵魂绑定");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SoulBindingMenu(containerId, inventory, maid.getUUID());
    }
}
