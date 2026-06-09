package com.yunchen.maidsoulcore.forge.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 客户端专用初始化。
 *
 * <p>Forge 的 COMMON config 只负责生成 toml；Mods 页面能不能点开配置，
 * 需要像 EasyTTS 插件那样显式注册 ConfigScreenFactory。</p>
 */
public final class MaidSoulClientBootstrap {
    private MaidSoulClientBootstrap() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new MaidSoulConfigScreen(parent))
        );
    }
}


