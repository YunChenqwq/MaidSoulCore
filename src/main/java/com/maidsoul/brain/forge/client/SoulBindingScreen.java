package com.maidsoul.brain.forge.client;

import com.maidsoul.brain.forge.menu.SoulBindingMenu;
import com.maidsoul.brain.forge.network.ModNetwork;
import com.maidsoul.brain.forge.network.SoulBindingActionPacket;
import com.maidsoul.brain.forge.network.SoulBindingListRequestPacket;
import com.maidsoul.brain.forge.network.SoulBindingListResponsePacket;
import com.maidsoul.brain.forge.soul.SoulSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class SoulBindingScreen extends AbstractContainerScreen<SoulBindingMenu> {
    private EditBox soulIdBox;
    private EditBox displayNameBox;
    private int selectedIndex = -1;

    public SoulBindingScreen(SoulBindingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 300;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 14;
        int y = topPos + 26;
        soulIdBox = new EditBox(font, x, y, 120, 20, Component.literal("soulId"));
        soulIdBox.setMaxLength(48);
        soulIdBox.setValue("jiuhu");
        addRenderableWidget(soulIdBox);

        displayNameBox = new EditBox(font, x + 128, y, 120, 20, Component.literal("displayName"));
        displayNameBox.setMaxLength(32);
        displayNameBox.setValue("酒狐");
        addRenderableWidget(displayNameBox);

        addRenderableWidget(Button.builder(Component.literal("刷新"), button ->
                ModNetwork.CHANNEL.sendToServer(new SoulBindingListRequestPacket(menu.maidUuid()))
        ).bounds(leftPos + 254, y, 36, 20).build());

        addRenderableWidget(Button.builder(Component.literal("创建并绑定"), button ->
                ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.create(menu.maidUuid(), soulIdBox.getValue(), displayNameBox.getValue()))
        ).bounds(leftPos + 14, topPos + 190, 86, 20).build());

        addRenderableWidget(Button.builder(Component.literal("绑定选中"), button -> {
            SoulSummary selected = selectedSoul();
            if (selected != null) {
                ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.bind(menu.maidUuid(), selected.soulId()));
            }
        }).bounds(leftPos + 108, topPos + 190, 72, 20).build());

        addRenderableWidget(Button.builder(Component.literal("解绑"), button ->
                ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.unbind(menu.maidUuid()))
        ).bounds(leftPos + 188, topPos + 190, 42, 20).build());

        ModNetwork.CHANNEL.sendToServer(new SoulBindingListRequestPacket(menu.maidUuid()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD101820);
        graphics.fill(leftPos + 10, topPos + 52, leftPos + imageWidth - 10, topPos + 184, 0xAA17212B);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        graphics.drawString(font, "女仆灵魂绑定", leftPos + 12, topPos + 10, 0xFFFFFF, false);
        String current = snapshot == null || snapshot.currentSoulId().isBlank() ? "未绑定" : snapshot.currentSoulId();
        graphics.drawString(font, "当前灵魂: " + current, leftPos + 14, topPos + 52, 0xD9E8FF, false);
        graphics.drawString(font, "世界: " + (snapshot == null ? "读取中" : snapshot.worldId()), leftPos + 150, topPos + 52, 0x9FB2C8, false);
        renderSoulList(graphics, snapshot);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderSoulList(GuiGraphics graphics, SoulBindingListResponsePacket.Snapshot snapshot) {
        List<SoulSummary> souls = snapshot == null ? List.of() : snapshot.souls();
        int startY = topPos + 68;
        if (souls.isEmpty()) {
            graphics.drawString(font, "还没有灵魂数据。输入 soulId 后点击创建并绑定。", leftPos + 18, startY + 18, 0xA9B8C8, false);
            return;
        }
        int max = Math.min(6, souls.size());
        for (int i = 0; i < max; i++) {
            SoulSummary soul = souls.get(i);
            int rowY = startY + i * 18;
            int color = i == selectedIndex ? 0xFF2A5C7A : 0x66304050;
            graphics.fill(leftPos + 14, rowY, leftPos + imageWidth - 14, rowY + 16, color);
            graphics.drawString(font, soul.displayName() + "  [" + soul.soulId() + "]", leftPos + 20, rowY + 4, 0xFFFFFF, false);
            graphics.drawString(font, trimWorld(soul.lastWorldId()), leftPos + 190, rowY + 4, 0xA9B8C8, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        if (snapshot != null) {
            int startY = topPos + 68;
            for (int i = 0; i < Math.min(6, snapshot.souls().size()); i++) {
                int rowY = startY + i * 18;
                if (mouseX >= leftPos + 14 && mouseX <= leftPos + imageWidth - 14 && mouseY >= rowY && mouseY <= rowY + 16) {
                    selectedIndex = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private SoulSummary selectedSoul() {
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        if (snapshot == null || selectedIndex < 0 || selectedIndex >= snapshot.souls().size()) {
            return null;
        }
        return snapshot.souls().get(selectedIndex);
    }

    private static String trimWorld(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 18 ? value : value.substring(0, 18);
    }
}
