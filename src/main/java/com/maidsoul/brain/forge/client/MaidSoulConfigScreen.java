package com.maidsoul.brain.forge.client;

import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.config.MaidSoulForgeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class MaidSoulConfigScreen extends Screen {
    private static final int LABEL_COLOR = 0xFFDDDDDD;
    private static final int HELP_COLOR = 0xFF8F8F8F;
    private static final int STATUS_COLOR = 0xFFAAAAAA;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private Tab tab = Tab.BASIC;

    private EditBox baseUrlBox;
    private EditBox apiKeyBox;
    private EditBox modelBox;
    private EditBox plannerModelBox;
    private EditBox replyerModelBox;
    private EditBox debounceBox;
    private CycleButton<Boolean> traceButton;
    private CycleButton<Boolean> affectButton;
    private CycleButton<Boolean> replyEchoButton;
    private CycleButton<Boolean> visionEnabledButton;
    private EditBox visionBaseUrlBox;
    private EditBox visionApiKeyBox;
    private EditBox visionModelBox;
    private Component status = Component.literal("未保存");

    public MaidSoulConfigScreen(Screen parent) {
        super(Component.literal("MaidSoulCore 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        clearWidgets();

        int panelWidth = Math.min(560, this.width - 40);
        int left = (this.width - panelWidth) / 2;
        int top = 78;
        int labelWidth = 150;
        int inputWidth = panelWidth - labelWidth - 10;

        addRenderableWidget(new StringWidget(this.width / 2 - 130, 14, 260, 20,
                Component.literal("MaidSoulCore 配置").withStyle(ChatFormatting.GOLD), this.font));

        int tabWidth = 72;
        int tabGap = 8;
        int tabLeft = this.width / 2 - (tabWidth * Tab.values().length + tabGap * (Tab.values().length - 1)) / 2;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab target = Tab.values()[i];
            addRenderableWidget(Button.builder(tabLabel(target), button -> switchTab(target))
                    .bounds(tabLeft + (tabWidth + tabGap) * i, 42, tabWidth, 20)
                    .build());
        }

        if (tab == Tab.BASIC) {
            debounceBox = addTextRow(left, top, labelWidth, inputWidth, "输入合批毫秒", String.valueOf(MaidSoulForgeConfig.MESSAGE_DEBOUNCE_MILLIS.get()));
        } else if (tab == Tab.MODEL) {
            baseUrlBox = addTextRow(left, top, labelWidth, inputWidth, "模型接口", MaidSoulForgeConfig.BASE_URL.get());
            top += 28;
            apiKeyBox = addTextRow(left, top, labelWidth, inputWidth, "模型 API Key", MaidSoulForgeConfig.API_KEY.get());
            top += 28;
            modelBox = addTextRow(left, top, labelWidth, inputWidth, "默认模型", MaidSoulForgeConfig.MODEL.get());
            top += 28;
            plannerModelBox = addTextRow(left, top, labelWidth, inputWidth, "Planner 模型", MaidSoulForgeConfig.PLANNER_MODEL.get());
            top += 28;
            replyerModelBox = addTextRow(left, top, labelWidth, inputWidth, "Replyer 模型", MaidSoulForgeConfig.REPLYER_MODEL.get());
        } else if (tab == Tab.VISION) {
            visionEnabledButton = addBoolRow(left, top, labelWidth, inputWidth, "启用视觉摘要", MaidSoulForgeConfig.VISION_ENABLED.get());
            top += 28;
            visionBaseUrlBox = addTextRow(left, top, labelWidth, inputWidth, "视觉模型接口", MaidSoulForgeConfig.VISION_BASE_URL.get());
            top += 28;
            visionApiKeyBox = addTextRow(left, top, labelWidth, inputWidth, "视觉 API Key", MaidSoulForgeConfig.VISION_API_KEY.get());
            top += 28;
            visionModelBox = addTextRow(left, top, labelWidth, inputWidth, "视觉模型", MaidSoulForgeConfig.VISION_MODEL.get());
        } else {
            traceButton = addBoolRow(left, top, labelWidth, inputWidth, "聊天栏 trace", MaidSoulForgeConfig.ECHO_TRACE_TO_OWNER_CHAT.get());
            top += 28;
            affectButton = addBoolRow(left, top, labelWidth, inputWidth, "聊天栏情绪", MaidSoulForgeConfig.ECHO_AFFECT_TO_OWNER_CHAT.get());
            top += 28;
            replyEchoButton = addBoolRow(left, top, labelWidth, inputWidth, "兜底回复回显", MaidSoulForgeConfig.ECHO_REPLY_TO_OWNER_CHAT.get());
        }

        int buttonWidth = 96;
        int gap = 10;
        int buttonLeft = this.width / 2 - (buttonWidth * 3 + gap * 2) / 2;
        int buttonY = this.height - 54;
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> saveConfig())
                .bounds(buttonLeft, buttonY, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("显示目录"), button -> status = Component.literal(
                "配置目录: " + ForgeBrainConfigInstaller.configRoot().toAbsolutePath().normalize()
        )).bounds(buttonLeft + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(buttonLeft + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20)
                .build());
    }

    private EditBox addTextRow(int left, int top, int labelWidth, int inputWidth, String label, String value) {
        EditBox box = addRenderableWidget(new EditBox(this.font, left + labelWidth + 10, top, inputWidth, 20, Component.literal(label)));
        box.setMaxLength(1024);
        box.setValue(value == null ? "" : value);
        rows.add(new Row(Component.literal(label), box));
        return box;
    }

    private CycleButton<Boolean> addBoolRow(int left, int top, int labelWidth, int inputWidth, String label, boolean value) {
        CycleButton<Boolean> button = addRenderableWidget(CycleButton.<Boolean>builder(this::displayBoolean)
                .withValues(List.of(false, true))
                .withInitialValue(value)
                .create(left + labelWidth + 10, top, inputWidth, 20, Component.literal(label)));
        rows.add(new Row(Component.literal(label), button));
        return button;
    }

    private void saveConfig() {
        if (baseUrlBox != null) {
            MaidSoulForgeConfig.BASE_URL.set(baseUrlBox.getValue().trim());
        }
        if (apiKeyBox != null) {
            MaidSoulForgeConfig.API_KEY.set(apiKeyBox.getValue().trim());
        }
        if (modelBox != null) {
            MaidSoulForgeConfig.MODEL.set(modelBox.getValue().trim());
        }
        if (plannerModelBox != null) {
            MaidSoulForgeConfig.PLANNER_MODEL.set(plannerModelBox.getValue().trim());
        }
        if (replyerModelBox != null) {
            MaidSoulForgeConfig.REPLYER_MODEL.set(replyerModelBox.getValue().trim());
        }
        if (debounceBox != null) {
            MaidSoulForgeConfig.MESSAGE_DEBOUNCE_MILLIS.set(parseLong(debounceBox.getValue(), 250L));
        }
        if (visionEnabledButton != null) {
            MaidSoulForgeConfig.VISION_ENABLED.set(visionEnabledButton.getValue());
        }
        if (visionBaseUrlBox != null) {
            MaidSoulForgeConfig.VISION_BASE_URL.set(visionBaseUrlBox.getValue().trim());
        }
        if (visionApiKeyBox != null) {
            MaidSoulForgeConfig.VISION_API_KEY.set(visionApiKeyBox.getValue().trim());
        }
        if (visionModelBox != null) {
            MaidSoulForgeConfig.VISION_MODEL.set(visionModelBox.getValue().trim());
        }
        if (traceButton != null) {
            MaidSoulForgeConfig.ECHO_TRACE_TO_OWNER_CHAT.set(traceButton.getValue());
        }
        if (affectButton != null) {
            MaidSoulForgeConfig.ECHO_AFFECT_TO_OWNER_CHAT.set(affectButton.getValue());
        }
        if (replyEchoButton != null) {
            MaidSoulForgeConfig.ECHO_REPLY_TO_OWNER_CHAT.set(replyEchoButton.getValue());
        }
        MaidSoulForgeConfig.SPEC.save();
        ForgeBrainConfigInstaller.installIfMissing();
        ForgeBrainConfigInstaller.syncForgeConfigToCoreFiles();
        status = Component.literal("已保存，并同步到 config/maidsoulcore");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Row row : rows) {
            if (row.widget() != null) {
                graphics.drawString(font, row.label(), row.widget().getX() - 160, row.widget().getY() + 6, LABEL_COLOR, false);
            }
        }
        renderHelpText(graphics);
        graphics.drawCenteredString(font, status, this.width / 2, this.height - 28, STATUS_COLOR);
    }

    private void renderHelpText(GuiGraphics graphics) {
        int x = this.width / 2 - 220;
        int y = 212;
        if (tab == Tab.BASIC) {
            graphics.drawString(font, "基础页只放聊天节奏相关选项。模型、视觉和调试分别在各自分页里。", x, y, HELP_COLOR, false);
        } else if (tab == Tab.MODEL) {
            graphics.drawString(font, "模型 API Key 留空时不会覆盖 config/maidsoulcore/model/llm.properties 里的已有 key。", x, y, HELP_COLOR, false);
        } else if (tab == Tab.VISION) {
            graphics.drawString(font, "视觉摘要会请求客户端截图，再由视觉模型转成世界事件写入女仆核心。", x, y, HELP_COLOR, false);
            graphics.drawString(font, "视觉 API Key 留空时不会覆盖已有视觉 key；视觉配置为空时仍可沿用主模型 key。", x, y + 14, HELP_COLOR, false);
        } else {
            graphics.drawString(font, "调试输出可能刷屏，只建议定位问题时打开。", x, y, HELP_COLOR, false);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private Component displayBoolean(Boolean value) {
        return Component.literal(Boolean.TRUE.equals(value) ? "开" : "关");
    }

    private Component tabLabel(Tab target) {
        String text = target.title;
        return Component.literal(tab == target ? "[" + text + "]" : text);
    }

    private void switchTab(Tab target) {
        this.tab = target;
        this.baseUrlBox = null;
        this.apiKeyBox = null;
        this.modelBox = null;
        this.plannerModelBox = null;
        this.replyerModelBox = null;
        this.debounceBox = null;
        this.traceButton = null;
        this.affectButton = null;
        this.replyEchoButton = null;
        this.visionEnabledButton = null;
        this.visionBaseUrlBox = null;
        this.visionApiKeyBox = null;
        this.visionModelBox = null;
        init();
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record Row(Component label, AbstractWidget widget) {
    }

    private enum Tab {
        BASIC("基础"),
        MODEL("模型"),
        VISION("视觉"),
        DEBUG("调试");

        private final String title;

        Tab(String title) {
            this.title = title;
        }
    }
}
