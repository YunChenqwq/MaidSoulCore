package com.maidsoul.brain.forge;

import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.network.ModNetwork;
import com.maidsoul.brain.forge.registry.ModItems;
import com.maidsoul.brain.forge.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MaidSoulCoreForgeMod.MOD_ID)
public final class MaidSoulCoreForgeMod {
    public static final String MOD_ID = "maidsoulcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidSoulCoreForgeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModNetwork.register();
        ForgeBrainConfigInstaller.installIfMissing();
        MinecraftForge.EVENT_BUS.register(MaidSoulForgeEvents.class);
        LOGGER.info("MaidSoulCore Forge bridge loaded: Touhou Little Maid runtime adapter is available.");
    }
}
