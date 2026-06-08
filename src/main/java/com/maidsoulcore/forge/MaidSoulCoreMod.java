package com.maidsoulcore.forge;

import com.maidsoulcore.forge.client.MaidSoulClientBootstrap;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge 模组主入口。
 * <p>
 * 这里只做最顶层的事情：
 * 注册配置、注册全局 Forge 事件监听。
 * 真正的 AI 扩展点接入由 {@code @LittleMaidExtension} 类负责。
 */
@Mod(MaidSoulCoreMod.MOD_ID)
public final class MaidSoulCoreMod {
    public static final String MOD_ID = "maidsoulcore";

    public MaidSoulCoreMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidSoulCommonConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(MaidSoulForgeEvents.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MaidSoulClientBootstrap::registerConfigScreen);
    }
}
