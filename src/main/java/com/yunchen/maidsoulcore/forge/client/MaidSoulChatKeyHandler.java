package com.yunchen.maidsoulcore.forge.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.MaidSoulCoreMod;
import com.yunchen.maidsoulcore.forge.network.ModNetwork;
import com.yunchen.maidsoulcore.forge.network.OpenMaidSoulChatPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * MaidSoulCore 自己的女仆聊天快捷键。
 *
 * <p>它不再抢原版聊天键，也不再依赖 TLM 对 T 键的判断链路。
 * 玩家按下快捷键时，客户端只发送一个很薄的打开请求。准星指向自己拥有的女仆时优先指定这只；
 * 没有指向女仆时，服务端会选择玩家当前世界中第一只已经用灵魂核心注册过的女仆。</p>
 */
@Mod.EventBusSubscriber(modid = MaidSoulCoreMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MaidSoulChatKeyHandler {
    public static final KeyMapping OPEN_CHAT_KEY = new KeyMapping(
            "key.maidsoulcore.open_chat",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.maidsoulcore"
    );

    private MaidSoulChatKeyHandler() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAT_KEY);
    }

    @Mod.EventBusSubscriber(modid = MaidSoulCoreMod.MOD_ID, value = Dist.CLIENT)
    public static final class ClientInput {
        private ClientInput() {
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (!isOpenChatPress(event) || !isInGame()) {
                return;
            }
            OPEN_CHAT_KEY.consumeClick();
            EntityMaid maid = pointedOwnedMaid();
            ModNetwork.CHANNEL.sendToServer(new OpenMaidSoulChatPacket(maid == null ? -1 : maid.getId()));
        }

        private static boolean isOpenChatPress(InputEvent.Key event) {
            return event.getAction() == GLFW.GLFW_PRESS
                    && OPEN_CHAT_KEY.matches(event.getKey(), event.getScanCode());
        }

        private static EntityMaid pointedOwnedMaid() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return null;
            }
            HitResult hitResult = Minecraft.getInstance().hitResult;
            if (!(hitResult instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof EntityMaid maid)) {
                return null;
            }
            return maid.isOwnedBy(player) ? maid : null;
        }

        private static boolean isInGame() {
            Minecraft mc = Minecraft.getInstance();
            return mc.getOverlay() == null
                    && mc.screen == null
                    && mc.mouseHandler.isMouseGrabbed()
                    && mc.isWindowActive();
        }
    }
}


