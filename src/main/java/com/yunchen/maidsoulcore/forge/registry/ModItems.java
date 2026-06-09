package com.yunchen.maidsoulcore.forge.registry;

import com.yunchen.maidsoulcore.MaidSoulCoreMod;
import com.yunchen.maidsoulcore.forge.item.SoulCoreItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MaidSoulCoreMod.MOD_ID);

    public static final RegistryObject<Item> SOUL_CORE = ITEMS.register("soul_core", () -> new SoulCoreItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}


