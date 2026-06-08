package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.mood.MoodState;
import com.maidsoulcore.runtime.RuntimeConfig;
import com.maidsoulcore.trace.TraceEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Swing 版文字游戏入口。
 * <p>
 * 这一版重点解决两件事：
 * 1. 控制台乱码问题；
 * 2. 等待状态与角色状态可视化问题。
 */
public final class MaidSoulTextGameGuiMain {
    private MaidSoulTextGameGuiMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MaidSoulTextGameGuiMain::createAndShow);
    }

    private static void createAndShow() {
        SimulationEnvironment environment = new SimulationEnvironment("maid-demo-01", "owner-demo-01", "小女仆", "主人");
        SimulationEngine engine = new SimulationEngine(RuntimeConfig.defaults(), environment);
        SimulationAutoEventDirector director = new SimulationAutoEventDirector();
        SimulationTranscriptLogger logger = new SimulationTranscriptLogger(Path.of("logs", "text-game.log"));

        JFrame frame = new JFrame("MaidSoulCore 陪伴式文字模拟器");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1080, 760));

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
        chatArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextField inputField = new JTextField();
        inputField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
        JButton sendButton = new JButton("发送");
        JButton statusButton = new JButton("状态");
        JButton traceButton = new JButton("Trace");
        JButton homeButton = new JButton("回家");

        JLabel waitingLabel = new JLabel("空闲");
        waitingLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        waitingLabel.setForeground(new Color(0x2E7D32));

        JProgressBar moodBar = createBar("心情");
        JProgressBar bondBar = createBar("好感");
        JProgressBar energyBar = createBar("能量");
        JLabel moodText = new JLabel("心情: 平静");
        JLabel bondText = new JLabel("好感: 0%");
        JLabel energyText = new JLabel("能量: 0%");

        JPanel topPanel = new JPanel(new BorderLayout(12, 12));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JPanel statusHeader = new JPanel(new BorderLayout());
        statusHeader.add(new JLabel("女仆状态面板"), BorderLayout.WEST);
        statusHeader.add(waitingLabel, BorderLayout.EAST);

        JPanel barsPanel = new JPanel();
        barsPanel.setLayout(new BoxLayout(barsPanel, BoxLayout.Y_AXIS));
        barsPanel.add(wrapBar("心情", moodBar, moodText));
        barsPanel.add(wrapBar("好感", bondBar, bondText));
        barsPanel.add(wrapBar("能量", energyBar, energyText));

        topPanel.add(statusHeader, BorderLayout.NORTH);
        topPanel.add(barsPanel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel();
        actionPanel.add(statusButton);
        actionPanel.add(traceButton);
        actionPanel.add(homeButton);
        actionPanel.add(sendButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        append(chatArea, logger, "系统", "欢迎进入 MaidSoulCore 陪伴式模拟器。");
        append(chatArea, logger, "系统", "现在世界会自动随机发生事件，你只需要正常聊天。");
        append(chatArea, logger, "系统", "日志文件: " + logger.logPath().toAbsolutePath());
        refreshStatus(engine, environment, moodBar, bondBar, energyBar, moodText, bondText, energyText);

        Runnable initialBoot = () -> runAsync(
                engine,
                director,
                environment,
                chatArea,
                logger,
                inputField,
                sendButton,
                statusButton,
                traceButton,
                homeButton,
                waitingLabel,
                moodBar,
                bondBar,
                energyBar,
                moodText,
                bondText,
                energyText,
                () -> playAutoEvents(engine, director.beforePlayerTurn(engine, environment), chatArea, logger)
        );
        initialBoot.run();

        Runnable sendAction = () -> {
            String text = inputField.getText().trim();
            if (text.isBlank()) {
                return;
            }
            inputField.setText("");
            append(chatArea, logger, "你", text);
            environment.setLastPlayerUtterance(text);
            runAsync(
                    engine,
                    director,
                    environment,
                    chatArea,
                    logger,
                    inputField,
                    sendButton,
                    statusButton,
                    traceButton,
                    homeButton,
                    waitingLabel,
                    moodBar,
                    bondBar,
                    energyBar,
                    moodText,
                    bondText,
                    energyText,
                    () -> {
                        SimulationTurnResult userTurn = engine.handle(engine.newEvent(
                                "owner.talk",
                                EventPriority.P1,
                                Map.of("text", text)
                        ));
                        appendTurn(chatArea, logger, userTurn, "你的发言");
                        playAutoEvents(engine, director.afterPlayerTurn(engine, environment), chatArea, logger);
                        playAutoEvents(engine, director.beforePlayerTurn(engine, environment), chatArea, logger);
                    }
            );
        };

        sendButton.addActionListener(event -> sendAction.run());
        inputField.addActionListener(event -> sendAction.run());
        statusButton.addActionListener(event -> append(chatArea, logger, "状态", environment.describe()));
        traceButton.addActionListener(event -> append(chatArea, logger, "Trace", traceText(engine.traceSnapshot())));
        homeButton.addActionListener(event -> runAsync(
                engine,
                director,
                environment,
                chatArea,
                logger,
                inputField,
                sendButton,
                statusButton,
                traceButton,
                homeButton,
                waitingLabel,
                moodBar,
                bondBar,
                energyBar,
                moodText,
                bondText,
                energyText,
                () -> {
                    SimulationTurnResult result = engine.handle(engine.newEvent("owner.command.return_home", EventPriority.P1, Map.of()));
                    appendTurn(chatArea, logger, result, "快捷命令");
                }
        ));

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * 后台执行模型调用，避免阻塞 Swing 线程。
     */
    private static void runAsync(
            SimulationEngine engine,
            SimulationAutoEventDirector director,
            SimulationEnvironment environment,
            JTextArea chatArea,
            SimulationTranscriptLogger logger,
            JTextField inputField,
            JButton sendButton,
            JButton statusButton,
            JButton traceButton,
            JButton homeButton,
            JLabel waitingLabel,
            JProgressBar moodBar,
            JProgressBar bondBar,
            JProgressBar energyBar,
            JLabel moodText,
            JLabel bondText,
            JLabel energyText,
            Runnable task
    ) {
        setBusy(true, inputField, sendButton, statusButton, traceButton, homeButton, waitingLabel);
        Thread worker = new Thread(() -> {
            try {
                task.run();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    refreshStatus(engine, environment, moodBar, bondBar, energyBar, moodText, bondText, energyText);
                    setBusy(false, inputField, sendButton, statusButton, traceButton, homeButton, waitingLabel);
                });
            }
        }, "maidsoulcore-gui-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 切换等待中状态。
     */
    private static void setBusy(
            boolean busy,
            JTextField inputField,
            JButton sendButton,
            JButton statusButton,
            JButton traceButton,
            JButton homeButton,
            JLabel waitingLabel
    ) {
        inputField.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        statusButton.setEnabled(!busy);
        traceButton.setEnabled(!busy);
        homeButton.setEnabled(!busy);
        waitingLabel.setText(busy ? "等待模型响应中..." : "空闲");
        waitingLabel.setForeground(busy ? new Color(0xEF6C00) : new Color(0x2E7D32));
    }

    /**
     * 刷新顶部状态条。
     */
    private static void refreshStatus(
            SimulationEngine engine,
            SimulationEnvironment environment,
            JProgressBar moodBar,
            JProgressBar bondBar,
            JProgressBar energyBar,
            JLabel moodText,
            JLabel bondText,
            JLabel energyText
    ) {
        BlackboardView blackboard = engine.currentBlackboard();
        MoodState moodState = blackboard.mood();
        int moodValue = clampPercent((moodState.valence() + 1.0D) * 50.0D);
        int bondValue = clampPercent((moodState.bond() + 1.0D) * 50.0D);
        int energyValue = clampPercent(environment.maidEnergy() * 100.0D);

        moodBar.setValue(moodValue);
        bondBar.setValue(bondValue);
        energyBar.setValue(energyValue);

        moodText.setText("心情: " + moodLabel(moodState.valence()) + " (" + moodValue + "%)");
        bondText.setText("好感: " + bondValue + "%");
        energyText.setText("能量: " + energyValue + "%");
    }

    private static JProgressBar createBar(String title) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setString(title);
        bar.setPreferredSize(new Dimension(320, 24));
        return bar;
    }

    private static JPanel wrapBar(String title, JProgressBar bar, JLabel textLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 6));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setPreferredSize(new Dimension(64, 24));
        row.add(titleLabel, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(textLabel, BorderLayout.EAST);
        return row;
    }

    /**
     * 处理自动事件。
     */
    private static void playAutoEvents(
            SimulationEngine engine,
            List<MaidEvent> events,
            JTextArea chatArea,
            SimulationTranscriptLogger logger
    ) {
        for (MaidEvent event : events) {
            SimulationTurnResult result = engine.handle(event);
            appendTurn(chatArea, logger, result, "世界事件");
        }
    }

    /**
     * 把一轮结果写入聊天区。
     */
    private static void appendTurn(
            JTextArea chatArea,
            SimulationTranscriptLogger logger,
            SimulationTurnResult result,
            String title
    ) {
        String reply = result.reply().isBlank() ? "(本轮未对外发言)" : result.reply();
        append(chatArea, logger, title, reply);
        append(chatArea, logger, "调试", "route=" + result.decision().route()
                + " mood=" + result.blackboard().mood()
                + " action=" + result.blackboard().state().getOrDefault("memory.last_action", "无"));
        if (result.plan() != null) {
            append(chatArea, logger, "计划", result.plan().actions().stream().map(action -> action.actionType()).toList().toString());
        }
        if (!result.executionLogs().isEmpty()) {
            append(chatArea, logger, "工具", result.executionLogs().toString());
        }
    }

    private static String traceText(List<TraceEvent> traceEvents) {
        StringBuilder builder = new StringBuilder();
        for (TraceEvent event : traceEvents) {
            builder.append('#').append(event.sequence())
                    .append(" stage=").append(event.stage())
                    .append(" type=").append(event.type())
                    .append(" priority=").append(event.priority())
                    .append(" reason=").append(event.reason())
                    .append(System.lineSeparator());
        }
        return builder.isEmpty() ? "暂无 trace" : builder.toString();
    }

    private static void append(JTextArea chatArea, SimulationTranscriptLogger logger, String role, String text) {
        SwingUtilities.invokeLater(() -> {
            String line = "【" + role + "】" + text;
            chatArea.append(line + System.lineSeparator() + System.lineSeparator());
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            logger.append(line);
        });
    }

    private static int clampPercent(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }

    private static String moodLabel(double valence) {
        if (valence >= 0.45D) {
            return "开心";
        }
        if (valence >= 0.10D) {
            return "平静";
        }
        if (valence >= -0.20D) {
            return "低落";
        }
        return "难受";
    }
}
