package com.maidsoul.brain.forge.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.AIChatScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.network.MaidSoulOwnerChatPacket;
import com.maidsoul.brain.forge.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * 复用 TLM 原版 AIChatScreen 的外观和大部分交互，但把“发送消息”这一步换成 MaidSoulCore 自己的协议。
 *
 * <p>TLM 的原版发送链路会进入 SendUserChatMessage，再进入 MaidAIChatManager.chat(...)。
 * 那条链路会先经过 TLM 自己的 LLM 站点、自动人设、上下文拼接、token 检查等逻辑；
 * 我们这里只想借它的输入框 UI，所以按下 Enter 时直接发 MaidSoulOwnerChatPacket。</p>
 */
public final class MaidSoulAIChatScreen extends AIChatScreen {
    private static final Field INPUT_FIELD = findInputField();
    private final EntityMaid maid;

    public MaidSoulAIChatScreen(EntityMaid maid) {
        super(maid);
        this.maid = maid;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            sendMaidSoulMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendMaidSoulMessage() {
        EditBox input = input();
        LocalPlayer player = Minecraft.getInstance().player;
        if (input == null || player == null) {
            onClose();
            return;
        }
        String value = input.getValue();
        if (StringUtils.isNotBlank(value)) {
            ModNetwork.CHANNEL.sendToServer(new MaidSoulOwnerChatPacket(maid.getId(), value));
            String format = "<%s> %s".formatted(player.getScoreboardName(), value);
            player.sendSystemMessage(Component.literal(format).withStyle(ChatFormatting.GRAY));
        }
        onClose();
    }

    private EditBox input() {
        if (INPUT_FIELD == null) {
            return null;
        }
        try {
            Object value = INPUT_FIELD.get(this);
            return value instanceof EditBox editBox ? editBox : null;
        } catch (IllegalAccessException e) {
            MaidSoulCoreForgeMod.LOGGER.warn("Failed to access TLM AIChatScreen input field for MaidSoulCore chat.", e);
            return null;
        }
    }

    private static Field findInputField() {
        try {
            Field field = AIChatScreen.class.getDeclaredField("input");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            for (Field field : AIChatScreen.class.getDeclaredFields()) {
                if (EditBox.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            MaidSoulCoreForgeMod.LOGGER.warn("TLM AIChatScreen input field was not found; MaidSoulCore chat send will be disabled.", e);
            return null;
        }
    }
}
