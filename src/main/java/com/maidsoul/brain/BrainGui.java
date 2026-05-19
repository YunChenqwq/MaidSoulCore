package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.config.FlowConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;
import com.maidsoul.brain.util.JsonText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

/**
 * 本地聊天大脑测试窗。
 *
 * <p>这个界面只服务原型调试：左边看可见聊天，右边看内部 trace。
 * 你可以发一句话以后不动，观察主动节奏是否会从“现场观察”进入同一条规划链路。</p>
 */
public final class BrainGui {
    private final Path root = Path.of("").toAbsolutePath();
    private final JTextArea chatArea = new JTextArea();
    private final JTextArea traceArea = new JTextArea();
    private final JTextArea characterArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JLabel statusLabel = new JLabel("未启动");
    private final JCheckBox fastProactiveBox = new JCheckBox("快速主动测试", true);
    private final JButton startButton = new JButton("启动");
    private final JButton stopButton = new JButton("停止");
    private final JButton sendButton = new JButton("发送");
    private final JButton clearButton = new JButton("清空");
    private final JButton presetFirstMeetButton = new JButton("初识");
    private final JButton presetFamiliarButton = new JButton("熟悉");
    private final JButton presetAmbiguousButton = new JButton("暧昧");
    private final JButton presetLoverButton = new JButton("恋人");
    private final JButton presetHurtButton = new JButton("受伤");
    private final JButton presetRepairedButton = new JButton("修复后");
    private final JButton refreshCharacterButton = new JButton("刷新角色包");

    private BrainConfig config;
    private ConversationRuntime runtime;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BrainGui().show());
    }

    private void show() {
        JFrame frame = new JFrame("MaidSoulCore Brain Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 680));

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

        traceArea.setEditable(false);
        traceArea.setLineWrap(true);
        traceArea.setWrapStyleWord(true);
        traceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        characterArea.setEditable(false);
        characterArea.setLineWrap(true);
        characterArea.setWrapStyleWord(true);
        characterArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.addTab("Trace", new JScrollPane(traceArea));
        rightTabs.addTab("角色包", new JScrollPane(characterArea));

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                withTitle("聊天", new JScrollPane(chatArea)),
                withTitle("调试", rightTabs)
        );
        split.setResizeWeight(0.58);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(startButton);
        top.add(stopButton);
        top.add(clearButton);
        top.add(fastProactiveBox);
        top.add(statusLabel);

        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetPanel.setBorder(BorderFactory.createTitledBorder("关系快速测试"));
        presetPanel.add(new JLabel("预设："));
        presetPanel.add(presetFirstMeetButton);
        presetPanel.add(presetFamiliarButton);
        presetPanel.add(presetAmbiguousButton);
        presetPanel.add(presetLoverButton);
        presetPanel.add(presetHurtButton);
        presetPanel.add(presetRepairedButton);
        presetPanel.add(refreshCharacterButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(presetPanel, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        frame.add(north, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        startButton.addActionListener(event -> startRuntime());
        stopButton.addActionListener(event -> stopRuntime());
        clearButton.addActionListener(event -> {
            chatArea.setText("");
            traceArea.setText("");
        });
        sendButton.addActionListener(event -> sendMessage());
        inputField.addActionListener(event -> sendMessage());
        presetFirstMeetButton.addActionListener(event -> applyStatePreset(StatePreset.firstMeet()));
        presetFamiliarButton.addActionListener(event -> applyStatePreset(StatePreset.familiar()));
        presetAmbiguousButton.addActionListener(event -> applyStatePreset(StatePreset.ambiguous()));
        presetLoverButton.addActionListener(event -> applyStatePreset(StatePreset.lover()));
        presetHurtButton.addActionListener(event -> applyStatePreset(StatePreset.hurtPreset()));
        presetRepairedButton.addActionListener(event -> applyStatePreset(StatePreset.repaired()));
        refreshCharacterButton.addActionListener(event -> refreshCharacterPackageView());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopRuntime();
            }
        });

        setRunningUi(false);
        refreshCharacterPackageView();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel withTitle(String title, java.awt.Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void startRuntime() {
        if (runtime != null) {
            return;
        }
        try {
            BrainConfig loaded = BrainConfig.load(root.resolve("config"));
            if (loaded.model().apiKey() == null || loaded.model().apiKey().isBlank()) {
                JOptionPane.showMessageDialog(null, "缺少 API Key：请先配置 config/model/llm.properties。");
                return;
            }
            config = fastProactiveBox.isSelected() ? withFastProactiveRhythm(loaded) : loaded;
            PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
            runtime = new ConversationRuntime(
                    config,
                    prompts,
                    new OpenAiCompatibleClient(config.model()),
                    this::appendBotSegment,
                    this::appendTrace
            );
            runtime.start();
            appendSystem("聊天大脑已启动。");
            if (fastProactiveBox.isSelected()) {
                appendSystem("快速主动测试：轻续话 6 秒，主动推进 14 秒，环境观察 24 秒。");
            }
            setRunningUi(true);
        } catch (Exception e) {
            runtime = null;
            JOptionPane.showMessageDialog(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void stopRuntime() {
        ConversationRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
            appendSystem("聊天大脑已停止。");
        }
        setRunningUi(false);
    }

    private void sendMessage() {
        if (runtime == null || config == null) {
            return;
        }
        String text = inputField.getText().trim();
        if (text.isBlank()) {
            return;
        }
        inputField.setText("");
        appendChat(config.identity().ownerName(), text);
        runtime.receiveUserMessage(config.identity().ownerName(), text);
    }

    private void setRunningUi(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        sendButton.setEnabled(running);
        inputField.setEnabled(running);
        fastProactiveBox.setEnabled(!running);
        statusLabel.setText(running ? "运行中" : "未启动");
    }

    private void applyStatePreset(StatePreset preset) {
        boolean wasRunning = runtime != null;
        try {
            BrainConfig loaded = config != null ? config : BrainConfig.load(root.resolve("config"));
            Path characterDir = root.resolve(loaded.memory().characterRoot())
                    .resolve(loaded.memory().maidId())
                    .toAbsolutePath()
                    .normalize();

            // 当前 MemoryRuntime 会在启动时读取角色包、关系和心情状态。
            // 因此 GUI 快速测试不直接改内存对象，而是先落盘，再按需重启运行时。
            Files.createDirectories(characterDir);
            Files.writeString(characterDir.resolve("affect_state.json"), preset.affectJson(), StandardCharsets.UTF_8);
            Files.writeString(characterDir.resolve("relationship.json"), preset.relationshipJson(), StandardCharsets.UTF_8);

            if (wasRunning) {
                stopRuntime();
                startRuntime();
                appendSystem("已切到关系预设：" + preset.name() + "，运行时已重启并重新加载角色状态。");
            } else {
                appendSystem("已切到关系预设：" + preset.name() + "。启动后会加载这个状态。");
            }
            refreshCharacterPackageView();
            appendTrace("GUI_PRESET", "preset=" + preset.name() + " characterDir=" + characterDir);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void refreshCharacterPackageView() {
        try {
            BrainConfig loaded = config != null ? config : BrainConfig.load(root.resolve("config"));
            Path characterDir = root.resolve(loaded.memory().characterRoot())
                    .resolve(loaded.memory().maidId())
                    .toAbsolutePath()
                    .normalize();

            // 角色包视图只读展示落盘文件，方便确认“长期人设”和“短期状态”到底注入了什么。
            // 后续如果要做编辑器，可以在这里继续拆成结构化表单；当前先保持原文可见，便于调试。
            StringBuilder builder = new StringBuilder();
            builder.append("角色包目录\n")
                    .append(characterDir)
                    .append("\n\n");
            appendFileSection(builder, characterDir.resolve("character.properties"), "character.properties / 稳定人设");
            appendFileSection(builder, characterDir.resolve("traits.properties"), "traits.properties / 人格参数");
            appendFileSection(builder, characterDir.resolve("relationship.json"), "relationship.json / 长期关系参考");
            appendFileSection(builder, characterDir.resolve("affect_state.json"), "affect_state.json / 当前心情状态");
            appendFileSection(builder, characterDir.resolve("memories").resolve("core_memories.jsonl"), "memories/core_memories.jsonl / 核心记忆");

            SwingUtilities.invokeLater(() -> {
                characterArea.setText(builder.toString());
                characterArea.setCaretPosition(0);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> characterArea.setText(
                    "角色包加载失败：\n" + e.getClass().getSimpleName() + ": " + e.getMessage()
            ));
        }
    }

    private static void appendFileSection(StringBuilder builder, Path path, String title) {
        builder.append("==== ").append(title).append(" ====\n");
        builder.append(path).append("\n");
        if (!Files.exists(path)) {
            builder.append("(文件不存在)\n\n");
            return;
        }
        try {
            builder.append(Files.readString(path, StandardCharsets.UTF_8)).append("\n\n");
        } catch (Exception e) {
            builder.append("(读取失败：")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append(")\n\n");
        }
    }

    private void appendBotSegment(String segment) {
        appendChat(config.identity().botName(), segment);
    }

    private void appendChat(String speaker, String text) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[" + LocalTime.now().withNano(0) + "] " + speaker + "：\n");
            chatArea.append(text + "\n\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void appendSystem(String text) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[系统] " + text + "\n\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void appendTrace(String stage, String detail) {
        SwingUtilities.invokeLater(() -> {
            traceArea.append("[" + LocalTime.now().withNano(0) + "] " + stage + " | " + detail + "\n");
            traceArea.setCaretPosition(traceArea.getDocument().getLength());
        });
    }

    private static BrainConfig withFastProactiveRhythm(BrainConfig base) {
        FlowConfig old = base.flow();
        FlowConfig flow = new FlowConfig(
                old.historyWindow(),
                old.messageDebounceMillis(),
                old.maxInternalRounds(),
                old.enableIndependentTimingGate(),
                old.defaultWaitSeconds(),
                old.talkFrequency(),
                old.plannerInterruptMaxConsecutiveCount(),
                old.timingGateNonContinueCooldownMillis(),
                old.directReplyOnUserMessage(),
                true,
                old.proactiveMaxVisibleReplies(),
                3,
                6,
                14,
                24,
                36,
                30,
                old.proactiveMaxLongSilenceChecks()
        );
        return new BrainConfig(base.identity(), base.model(), flow, base.splitter(), base.memory(), base.debug());
    }

    private record StatePreset(
            String name,
            int mood,
            int anger,
            int hurt,
            int tension,
            int trust,
            int familiarity,
            int affection,
            int security,
            int curiosity,
            String level,
            int bondDepth,
            boolean romanticConfirmed,
            int trustHistory,
            int affectionHistory,
            int repairDebt,
            String importantMilestones
    ) {
        private static StatePreset firstMeet() {
            return new StatePreset("初识", 60, 0, 0, 10, 50, 20, 50, 55, 45,
                    "初识", 20, false, 50, 50, 0, "GUI 关系测试预设：初识。");
        }

        private static StatePreset familiar() {
            return new StatePreset("熟悉", 72, 0, 0, 5, 62, 55, 58, 60, 50,
                    "熟悉", 55, false, 62, 58, 0, "GUI 关系测试预设：熟悉。");
        }

        private static StatePreset ambiguous() {
            return new StatePreset("暧昧", 78, 0, 0, 8, 70, 68, 72, 65, 55,
                    "暧昧", 72, false, 70, 72, 0, "GUI 关系测试预设：暧昧。");
        }

        private static StatePreset lover() {
            return new StatePreset("恋人", 82, 0, 0, 4, 82, 80, 84, 75, 50,
                    "恋人", 86, true, 82, 84, 0, "GUI 关系测试预设：恋人。");
        }

        private static StatePreset hurtPreset() {
            return new StatePreset("受伤", 38, 25, 65, 70, 45, 55, 55, 35, 20,
                    "熟悉", 55, false, 45, 55, 35, "GUI 关系测试预设：关系受伤，需要修复。");
        }

        private static StatePreset repaired() {
            return new StatePreset("修复后", 62, 5, 25, 30, 58, 58, 60, 55, 35,
                    "熟悉", 58, false, 58, 60, 10, "GUI 关系测试预设：冲突后已初步修复。");
        }

        private String affectJson() {
            // affect_state.json 是短期可变状态：用于快速影响当轮 mood、信赖、熟悉度等投影。
            return "{\n"
                    + "  \"mood\": " + mood + ",\n"
                    + "  \"anger\": " + anger + ",\n"
                    + "  \"hurt\": " + hurt + ",\n"
                    + "  \"tension\": " + tension + ",\n"
                    + "  \"trust\": " + trust + ",\n"
                    + "  \"familiarity\": " + familiarity + ",\n"
                    + "  \"affection\": " + affection + ",\n"
                    + "  \"security\": " + security + ",\n"
                    + "  \"curiosity\": " + curiosity + "\n"
                    + "}\n";
        }

        private String relationshipJson() {
            // relationship.json 是长期关系参考：让角色包投影知道当前关系阶段和修复债。
            // knownBoundaries 保留长期边界，避免测试预设把用户明确表达过的偏好冲掉。
            String knownBoundaries = "用户不喜欢机械模板式关心，也不喜欢把角色变成固定口癖集合。";
            return "{\n"
                    + "  \"level\": \"" + JsonText.escape(level) + "\",\n"
                    + "  \"bondDepth\": " + bondDepth + ",\n"
                    + "  \"romanticConfirmed\": " + romanticConfirmed + ",\n"
                    + "  \"trustHistory\": " + trustHistory + ",\n"
                    + "  \"affectionHistory\": " + affectionHistory + ",\n"
                    + "  \"repairDebt\": " + repairDebt + ",\n"
                    + "  \"knownBoundaries\": \"" + JsonText.escape(knownBoundaries) + "\",\n"
                    + "  \"importantMilestones\": \"" + JsonText.escape(importantMilestones) + "\"\n"
                    + "}\n";
        }
    }
}
