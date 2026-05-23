package com.maidsoul.brain.forge.registry;

import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.item.SoulCoreItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MaidSoulCoreForgeMod.MOD_ID);

    public static final RegistryObject<Item> SOUL_CORE = ITEMS.register("soul_core", () -> new SoulCoreItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
