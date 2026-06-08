package com.yunchen.maidsoulcore.forge.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class OwnerChatDebugEcho {
    private OwnerChatDebugEcho() {
    }

    public static void echo(EntityMaid maid, DialogueCoreConfig config, String stage, String detail) {
        if (maid == null || config == null || config.debug == null || !config.debug.echoTraceToOwnerChat) {
            return;
        }
        send(maid, config, stage, detail, ChatFormatting.GRAY);
    }

    public static void important(EntityMaid maid, DialogueCoreConfig config, String stage, String detail) {
        if (maid == null || config == null || config.debug == null) {
            return;
        }
        send(maid, config, stage, detail, ChatFormatting.AQUA);
    }

    private static void send(EntityMaid maid, DialogueCoreConfig config, String stage, String detail, ChatFormatting color) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        String text = "[MaidSoulCore] " + stage + " | " + abbreviate(detail, Math.max(40, config.debug.maxChatEchoChars));
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static String abbreviate(String text, int max) {
        String cleaned = text == null ? "" : text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
    }
}
