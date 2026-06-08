package com.maidsoulcore.forge.client;

import com.maidsoulcore.forge.config.MaidSoulConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * MaidSoulCore 客户端初始化入口。
 * <p>
 * 当前它只做一件事：
 * 向 Forge 注册一个模组配置界面，
 * 方便在模组列表里直接打开 MaidSoulCore 设置面板。
 */
public final class MaidSoulClientBootstrap {
    private MaidSoulClientBootstrap() {
    }

    /**
     * 注册 Forge 模组配置界面工厂。
     */
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new MaidSoulConfigScreen(parent))
        );
    }
}
