package com.maidsoul.brain.forge.registry;

import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.menu.SoulBindingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MaidSoulCoreForgeMod.MOD_ID);

    public static final RegistryObject<MenuType<SoulBindingMenu>> SOUL_BINDING =
            MENUS.register("soul_binding", () -> IForgeMenuType.create(SoulBindingMenu::new));

    private ModMenus() {
    }
}
