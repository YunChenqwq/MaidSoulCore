package com.maidsoulcore.forge.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulPlanService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

import static com.maidsoulcore.forge.MaidSoulCoreMod.MOD_ID;

/**
 * 右上角任务链调试叠加层。
 * <p>
 * 当前版本优先服务于本地单机/集成服务端调试：
 * - 优先显示准星指向的己方女仆
 * - 否则显示附近最近一只存在活动计划的己方女仆
 */
@Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidSoulPlanOverlay {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_PADDING = 6;
    private static final int ROW_HEIGHT = 11;
    private static final int MAX_STEP_LINES = 8;

    private MaidSoulPlanOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }

        MaidSoulPlanService.PlanDebugSnapshot snapshot = resolveSnapshot(minecraft, player);
        if (snapshot == null || snapshot.steps().isEmpty()) {
            return;
        }

        renderSnapshot(event.getGuiGraphics(), minecraft.font, snapshot);
    }

    private static MaidSoulPlanService.PlanDebugSnapshot resolveSnapshot(Minecraft minecraft, LocalPlayer player) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        ClientLevel clientLevel = minecraft.level;
        if (server == null || clientLevel == null) {
            return null;
        }
        ServerLevel serverLevel = server.getLevel(clientLevel.dimension());
        if (serverLevel == null) {
            return null;
        }

        EntityMaid focused = focusedOwnedMaid(minecraft, player, serverLevel);
        if (focused != null) {
            MaidSoulPlanService.PlanDebugSnapshot snapshot = MaidSoulPlanService.snapshotFor(focused);
            if (snapshot != null) {
                return snapshot;
            }
        }

        return serverLevel.getEntitiesOfClass(
                        EntityMaid.class,
                        player.getBoundingBox().inflate(24.0d),
                        maid -> maid.getOwner() != null && maid.getOwner().getUUID().equals(player.getUUID())
                ).stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .map(MaidSoulPlanService::snapshotFor)
                .filter(snapshot -> snapshot != null && !snapshot.steps().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static EntityMaid focusedOwnedMaid(Minecraft minecraft, LocalPlayer player, ServerLevel serverLevel) {
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        Entity clientEntity = entityHitResult.getEntity();
        if (!(clientEntity instanceof EntityMaid)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(clientEntity.getUUID());
        if (!(entity instanceof EntityMaid maid)) {
            return null;
        }
        return maid.getOwner() != null && maid.getOwner().getUUID().equals(player.getUUID()) ? maid : null;
    }

    private static void renderSnapshot(GuiGraphics graphics, Font font, MaidSoulPlanService.PlanDebugSnapshot snapshot) {
        int lines = Math.min(MAX_STEP_LINES, snapshot.steps().size());
        int panelHeight = 38 + lines * ROW_HEIGHT;
        int left = graphics.guiWidth() - PANEL_WIDTH - 8;
        int top = 8;
        int right = left + PANEL_WIDTH;
        int bottom = top + panelHeight;

        graphics.fill(left, top, right, bottom, 0x90000000);
        graphics.fill(left, top, right, top + 1, 0x90FF8080);

        int textX = left + PANEL_PADDING;
        int textY = top + PANEL_PADDING;
        graphics.drawString(font, Component.literal("MaidSoul 任务链"), textX, textY, 0xFFF2F2F2, false);
        textY += ROW_HEIGHT;

        String objective = trim(font, snapshot.objective(), PANEL_WIDTH - PANEL_PADDING * 2);
        graphics.drawString(font, Component.literal(objective), textX, textY, 0xFFD8D8D8, false);
        textY += ROW_HEIGHT;

        String source = (snapshot.source() == null || snapshot.source().isBlank()) ? "unknown" : snapshot.source();
        String meta = "状态 " + snapshot.status() + " | 排队 " + snapshot.queuedPlanCount() + " | 来源 " + source;
        graphics.drawString(font, Component.literal(meta), textX, textY, 0xFFB8B8B8, false);
        textY += ROW_HEIGHT + 2;

        for (int index = 0; index < lines; index++) {
            MaidSoulPlanService.PlanStepView step = snapshot.steps().get(index);
            int color = colorForStep(step);
            String prefix = step.active() ? ">> " : (index < snapshot.currentStepIndex() ? "√ " : "- ");
            String line = prefix + (index + 1) + ". " + step.displayText();
            graphics.drawString(font, Component.literal(trim(font, line, PANEL_WIDTH - PANEL_PADDING * 2)), textX, textY, color, false);
            textY += ROW_HEIGHT;
        }
    }

    private static int colorForStep(MaidSoulPlanService.PlanStepView step) {
        if (step.active()) {
            return 0xFFFF5A5A;
        }
        return switch (step.status()) {
            case "COMPLETED" -> 0xFF8ED18E;
            case "FAILED", "CANCELLED" -> 0xFFFFA0A0;
            default -> 0xFFEAEAEA;
        };
    }

    private static String trim(Font font, String text, int width) {
        if (text == null) {
            return "";
        }
        return font.plainSubstrByWidth(text, Math.max(20, width));
    }
}
