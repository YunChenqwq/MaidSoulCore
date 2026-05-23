package com.maidsoul.brain.forge.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.config.ForgeDebugOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class OwnerChatDebugEcho {
    private OwnerChatDebugEcho() {
    }

    public static void echo(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail) {
        if (debug == null || !debug.echoTraceToOwnerChat()) {
            return;
        }
        send(maid, debug, stage, detail, ChatFormatting.GRAY);
    }

    public static void echoImportant(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail) {
        send(maid, debug, stage, detail, ChatFormatting.AQUA);
    }

    private static void send(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail, ChatFormatting color) {
        if (maid == null || debug == null) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        int max = Math.max(40, debug.maxChatEchoChars());
        String text = "[" + MaidSoulCoreForgeMod.MOD_ID + "] " + stage + " | " + abbreviate(detail, max);
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
