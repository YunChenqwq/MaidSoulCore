package com.maidsoul.brain.forge.menu;

import com.maidsoul.brain.forge.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class SoulBindingMenu extends AbstractContainerMenu {
    private final UUID maidUuid;

    public SoulBindingMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readUUID());
    }

    public SoulBindingMenu(int containerId, Inventory inventory, UUID maidUuid) {
        super(ModMenus.SOUL_BINDING.get(), containerId);
        this.maidUuid = maidUuid;
    }

    public UUID maidUuid() {
        return maidUuid;
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
