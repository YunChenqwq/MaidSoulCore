package com.maidsoul.brain.forge.client;

import com.maidsoul.brain.forge.menu.SoulBindingMenu;
import com.maidsoul.brain.forge.network.ModNetwork;
import com.maidsoul.brain.forge.network.SoulBindingActionPacket;
import com.maidsoul.brain.forge.network.SoulBindingListRequestPacket;
import com.maidsoul.brain.forge.network.SoulBindingListResponsePacket;
import com.maidsoul.brain.forge.soul.SoulBindingService;
import com.maidsoul.brain.forge.soul.SoulSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class SoulBindingScreen extends AbstractContainerScreen<SoulBindingMenu> {
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 6;
    private static final int PANEL = 0xDD101820;
    private static final int PANEL_SOFT = 0xAA17212B;
    private static final int ROW = 0x66304050;
    private static final int ROW_SELECTED = 0xFF2A5C7A;

    private EditBox soulIdBox;
    private EditBox displayNameBox;
    private int selectedIndex = -1;
    private int scrollOffset;

    public SoulBindingScreen(SoulBindingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 244;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 14;
        int y = topPos + 28;

        soulIdBox = new EditBox(font, x, y, 122, 20, Component.literal("soulId"));
        soulIdBox.setMaxLength(48);
        soulIdBox.setValue(SoulBindingService.DEFAULT_SOUL_ID);
        addRenderableWidget(soulIdBox);

        displayNameBox = new EditBox(font, x + 130, y, 86, 20, Component.literal("显示名"));
        displayNameBox.setMaxLength(32);
        displayNameBox.setValue(SoulBindingService.DEFAULT_DISPLAY_NAME);
        addRenderableWidget(displayNameBox);

        addRenderableWidget(Button.builder(Component.literal("刷新"), button ->
                ModNetwork.CHANNEL.sendToServer(new SoulBindingListRequestPacket(menu.maidUuid()))
        ).bounds(leftPos + 226, y, 42, 20).build());

        addRenderableWidget(Button.builder(Component.literal("创建并绑定"), button ->
                ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.create(menu.maidUuid(), soulIdBox.getValue(), displayNameBox.getValue()))
        ).bounds(leftPos + 14, topPos + 214, 86, 20).build());

        addRenderableWidget(Button.builder(Component.literal("绑定选中"), button -> {
            SoulSummary selected = selectedSoul();
            if (selected != null) {
                ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.bind(menu.maidUuid(), selected.soulId()));
            }
        }).bounds(leftPos + 108, topPos + 214, 72, 20).build());

        addRenderableWidget(Button.builder(Component.literal("解绑"), button ->
                ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.unbind(menu.maidUuid()))
        ).bounds(leftPos + 188, topPos + 214, 42, 20).build());

        addRenderableWidget(Button.builder(Component.literal("迁移旧记忆"), button -> {
            String targetSoulId = selectedSoul() == null ? soulIdBox.getValue() : selectedSoul().soulId();
            ModNetwork.CHANNEL.sendToServer(SoulBindingActionPacket.migrateLegacy(menu.maidUuid(), targetSoulId));
        }).bounds(leftPos + 238, topPos + 214, 80, 20).build());

        ModNetwork.CHANNEL.sendToServer(new SoulBindingListRequestPacket(menu.maidUuid()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(leftPos + 10, topPos + 54, leftPos + 214, topPos + 206, PANEL_SOFT);
        graphics.fill(leftPos + 222, topPos + 54, leftPos + imageWidth - 10, topPos + 206, PANEL_SOFT);
        graphics.fill(leftPos + 10, topPos + 208, leftPos + imageWidth - 10, topPos + 209, 0x664A6174);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        graphics.drawString(font, "女仆灵魂绑定", leftPos + 12, topPos + 10, 0xFFFFFF, false);
        graphics.drawString(font, "新灵魂ID", leftPos + 16, topPos + 18, 0x7FA0B8, false);
        graphics.drawString(font, "显示名", leftPos + 146, topPos + 18, 0x7FA0B8, false);
        String current = snapshot == null || snapshot.currentSoulId().isBlank() ? "未绑定" : snapshot.currentSoulId();
        graphics.drawString(font, "当前: " + clip(current, 22), leftPos + 276, topPos + 34, 0xD9E8FF, false);
        renderSoulList(graphics, snapshot);
        renderSoulDetails(graphics, snapshot);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderSoulList(GuiGraphics graphics, SoulBindingListResponsePacket.Snapshot snapshot) {
        List<SoulSummary> souls = snapshot == null ? List.of() : snapshot.souls();
        int startY = topPos + 70;
        graphics.drawString(font, "灵魂档案", leftPos + 18, topPos + 58, 0xD9E8FF, false);
        if (souls.isEmpty()) {
            graphics.drawString(font, "还没有灵魂档案。输入 soulId 后点击创建并绑定。", leftPos + 18, startY + 18, 0xA9B8C8, false);
            return;
        }
        clampScroll(souls.size());
        for (int row = 0; row < Math.min(VISIBLE_ROWS, souls.size()); row++) {
            int soulIndex = scrollOffset + row;
            SoulSummary soul = souls.get(soulIndex);
            int rowY = startY + row * ROW_HEIGHT;
            boolean selected = soulIndex == selectedIndex;
            graphics.fill(leftPos + 14, rowY, leftPos + 210, rowY + 19, selected ? ROW_SELECTED : ROW);
            graphics.drawString(font, clip(soul.displayName(), 14), leftPos + 20, rowY + 4, 0xFFFFFF, false);
            graphics.drawString(font, "[" + clip(soul.soulId(), 16) + "]", leftPos + 96, rowY + 4, 0xA9B8C8, false);
            if (snapshot != null && soul.soulId().equals(snapshot.currentSoulId())) {
                graphics.drawString(font, "已绑定", leftPos + 168, rowY + 4, 0x8DF5A6, false);
            }
        }
        if (souls.size() > VISIBLE_ROWS) {
            renderScrollBar(graphics, souls.size(), startY);
        }
    }

    private void renderScrollBar(GuiGraphics graphics, int size, int startY) {
        int barX = leftPos + 204;
        int barTop = startY;
        int barBottom = startY + VISIBLE_ROWS * ROW_HEIGHT - 3;
        int thumbHeight = Math.max(14, (barBottom - barTop) * VISIBLE_ROWS / size);
        int maxOffset = Math.max(1, size - VISIBLE_ROWS);
        int thumbY = barTop + (barBottom - barTop - thumbHeight) * scrollOffset / maxOffset;
        graphics.fill(barX, barTop, barX + 3, barBottom, 0x663C4C59);
        graphics.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xFF86A6BE);
    }

    private void renderSoulDetails(GuiGraphics graphics, SoulBindingListResponsePacket.Snapshot snapshot) {
        int x = leftPos + 230;
        int y = topPos + 58;
        graphics.drawString(font, "档案详情", x, y, 0xD9E8FF, false);
        if (snapshot == null) {
            graphics.drawString(font, "读取中...", x, y + 18, 0xA9B8C8, false);
            return;
        }
        SoulSummary selected = selectedSoul();
        if (selected == null) {
            graphics.drawString(font, "选择一个灵魂档案查看。", x, y + 18, 0xA9B8C8, false);
            drawField(graphics, x, y + 42, "当前世界", snapshot.worldId());
            return;
        }
        drawField(graphics, x, y + 18, "soulId", selected.soulId());
        drawField(graphics, x, y + 42, "显示名", selected.displayName());
        drawField(graphics, x, y + 66, "最后世界", selected.lastWorldId());
        drawField(graphics, x, y + 90, "最后身体", selected.lastMaidUuid());
        drawField(graphics, x, y + 114, "最后主人", selected.lastOwnerUuid());
        String state = selected.soulId().equals(snapshot.currentSoulId()) ? "正在承载" : conflictText(snapshot, selected);
        graphics.drawString(font, state, x, y + 136, state.equals("可绑定") ? 0x8DF5A6 : 0xFFD27D, false);
    }

    private void drawField(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(font, label + ":", x, y, 0x7FA0B8, false);
        graphics.drawString(font, clip(value == null || value.isBlank() ? "-" : value, 21), x, y + 10, 0xD5E0EA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        if (snapshot != null) {
            int startY = topPos + 70;
            for (int row = 0; row < Math.min(VISIBLE_ROWS, snapshot.souls().size()); row++) {
                int rowY = startY + row * ROW_HEIGHT;
                if (mouseX >= leftPos + 14 && mouseX <= leftPos + 210 && mouseY >= rowY && mouseY <= rowY + 19) {
                    selectedIndex = scrollOffset + row;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        if (snapshot != null && mouseX >= leftPos + 10 && mouseX <= leftPos + 214 && mouseY >= topPos + 54 && mouseY <= topPos + 206) {
            scrollOffset -= (int) Math.signum(delta);
            clampScroll(snapshot.souls().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private SoulSummary selectedSoul() {
        SoulBindingListResponsePacket.Snapshot snapshot = SoulBindingClientState.get(menu.maidUuid());
        if (snapshot == null || selectedIndex < 0 || selectedIndex >= snapshot.souls().size()) {
            return null;
        }
        return snapshot.souls().get(selectedIndex);
    }

    private void clampScroll(int size) {
        int max = Math.max(0, size - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));
        if (selectedIndex >= size) {
            selectedIndex = size - 1;
        }
    }

    private static String conflictText(SoulBindingListResponsePacket.Snapshot snapshot, SoulSummary selected) {
        if (selected.lastMaidUuid() == null || selected.lastMaidUuid().isBlank()) {
            return "可绑定";
        }
        String currentWorld = snapshot.worldId() == null ? "" : snapshot.worldId();
        if (!selected.lastWorldId().equals(currentWorld)) {
            return "将作为跨世界迁移";
        }
        return "会从旧身体转移";
    }

    private static String clip(String value, int max) {
        String clean = value == null || value.isBlank() ? "-" : value.trim();
        if (clean.length() <= max) {
            return clean;
        }
        return clean.substring(0, Math.max(1, max - 1)) + "...";
    }
}
