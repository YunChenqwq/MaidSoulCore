package com.yunchen.maidsoulcore.core.config;

public final class DialogueCoreConfig {
    public String botName = "灵汐";
    public String ownerName = "主人";
    public int historyWindow = 28;
    public long messageDebounceMillis = 900L;
    public int maxInternalRounds = 8;
    public int defaultWaitSeconds = 12;
    public long llmTimeoutMillis = 60000L;
    public boolean enableIndependentTimingGate = true;
    public long timingGateCooldownMillis = 1200L;
    public int replyRetryCount = 2;
    public DialogueDebugConfig debug = new DialogueDebugConfig();
    public DialogueModelConfig model = new DialogueModelConfig();
}
