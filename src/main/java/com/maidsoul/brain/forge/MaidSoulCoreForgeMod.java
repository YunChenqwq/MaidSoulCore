package com.maidsoul.brain.forge;

import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MaidSoulCoreForgeMod.MOD_ID)
public final class MaidSoulCoreForgeMod {
    public static final String MOD_ID = "maidsoulcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidSoulCoreForgeMod() {
        ForgeBrainConfigInstaller.installIfMissing();
        MinecraftForge.EVENT_BUS.register(MaidSoulForgeEvents.class);
        LOGGER.info("MaidSoulCore Forge bridge loaded: Touhou Little Maid runtime adapter is available.");
    }
}
