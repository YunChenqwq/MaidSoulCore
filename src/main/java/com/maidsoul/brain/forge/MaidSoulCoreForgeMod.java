package com.maidsoul.brain.forge;

import com.maidsoul.brain.forge.client.MaidSoulClientBootstrap;
import com.maidsoul.brain.forge.command.MaidSoulCommands;
import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.network.ModNetwork;
import com.maidsoul.brain.forge.registry.ModItems;
import com.maidsoul.brain.forge.registry.ModMenus;
import com.maidsoul.brain.forge.tlm.MaidSoulTlmBootstrapper;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MaidSoulCoreForgeMod.MOD_ID)
public final class MaidSoulCoreForgeMod {
    public static final String MOD_ID = "maidsoulcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidSoulCoreForgeMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.maidsoul.brain.forge.config.MaidSoulForgeConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MaidSoulClientBootstrap::registerConfigScreen);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreativeTabItems);

        ModNetwork.register();
        ForgeBrainConfigInstaller.installIfMissing();
        MaidSoulTlmBootstrapper.ensureRuntimeSite();
        com.maidsoul.brain.action.ysm.PoseConfig.init();
        MinecraftForge.EVENT_BUS.register(MaidSoulForgeEvents.class);
        MinecraftForge.EVENT_BUS.register(MaidSoulCommands.class);
        LOGGER.info("MaidSoulCore Forge bridge loaded: action system initialized, {} poses.", com.maidsoul.brain.action.ysm.PoseConfig.getPoseNames().size());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // TLM 会在自己的 common setup 里初始化 AI 站点表；这里排队再补一次，
        // 避免构造阶段注入的 maidsoul_runtime 被 AvailableSites.init() 清掉。
        event.enqueueWork(MaidSoulTlmBootstrapper::ensureRuntimeSite);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.SOUL_CORE.get());
        }
    }
}
