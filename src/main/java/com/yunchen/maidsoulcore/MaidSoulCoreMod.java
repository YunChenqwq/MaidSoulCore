package com.yunchen.maidsoulcore;

import com.mojang.logging.LogUtils;
import com.yunchen.maidsoulcore.forge.MaidSoulForgeEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MaidSoulCoreMod.MOD_ID)
public final class MaidSoulCoreMod {
    public static final String MOD_ID = "maidsoulcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidSoulCoreMod() {
        MinecraftForge.EVENT_BUS.register(MaidSoulForgeEvents.class);
        LOGGER.info("Maid Soul Core loaded: structured dialogue runtime is available.");
    }
}
