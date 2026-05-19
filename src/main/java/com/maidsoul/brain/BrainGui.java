package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.config.FlowConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
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
    private final JTextField inputField = new JTextField();
    private final JLabel statusLabel = new JLabel("未启动");
    private final JCheckBox fastProactiveBox = new JCheckBox("快速主动测试", true);
    private final JButton startButton = new JButton("启动");
    private final JButton stopButton = new JButton("停止");
    private final JButton sendButton = new JButton("发送");
    private final JButton clearButton = new JButton("清空");

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

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                withTitle("聊天", new JScrollPane(chatArea)),
                withTitle("Trace", new JScrollPane(traceArea))
        );
        split.setResizeWeight(0.58);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(startButton);
        top.add(stopButton);
        top.add(clearButton);
        top.add(fastProactiveBox);
        top.add(statusLabel);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        frame.add(top, BorderLayout.NORTH);
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
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopRuntime();
            }
        });

        setRunningUi(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel withTitle(String title, JScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scrollPane, BorderLayout.CENTER);
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
}
