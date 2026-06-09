package com.yunchen.maidsoulcore;

import com.mojang.logging.LogUtils;
import com.yunchen.maidsoulcore.forge.client.MaidSoulClientBootstrap;
import com.yunchen.maidsoulcore.forge.command.MaidSoulCommands;
import com.yunchen.maidsoulcore.forge.config.ForgeBrainConfigInstaller;
import com.yunchen.maidsoulcore.forge.config.MaidSoulForgeConfig;
import com.yunchen.maidsoulcore.forge.MaidSoulForgeEvents;
import com.yunchen.maidsoulcore.forge.network.ModNetwork;
import com.yunchen.maidsoulcore.forge.registry.ModItems;
import com.yunchen.maidsoulcore.forge.registry.ModMenus;
import com.yunchen.maidsoulcore.forge.tlm.MaidSoulTlmBootstrapper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MaidSoulCoreMod.MOD_ID)
public final class MaidSoulCoreMod {
    public static final String MOD_ID = "maidsoulcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidSoulCoreMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidSoulForgeConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MaidSoulClientBootstrap::registerConfigScreen);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreativeTabItems);

        ModNetwork.register();
        ForgeBrainConfigInstaller.installIfMissing();
        MaidSoulTlmBootstrapper.ensureRuntimeSite();
        MinecraftForge.EVENT_BUS.register(MaidSoulForgeEvents.class);
        MinecraftForge.EVENT_BUS.register(MaidSoulCommands.class);
        LOGGER.info("Maid Soul Core loaded: Touhou Little Maid adapter and structured runtime are available.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MaidSoulTlmBootstrapper::ensureRuntimeSite);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.SOUL_CORE.get());
        }
    }
}
