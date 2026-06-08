package com.maidsoulcore.sim;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SimulationEnvironment {
    private static final long DEFAULT_CAPTURE_INTERVAL_TICKS = 80L;
    private static final long CHAT_SESSION_SPEAK_COOLDOWN_TICKS = 5L;
    private static final long CHAT_SESSION_KEEPALIVE_TICKS = 200L;

    private final String maidId;
    private final String ownerId;
    private final String maidName;
    private final String ownerName;

    private long tick;
    private double maidHealth;
    private double maidEnergy;
    private double maidHunger;
    private boolean maidSleeping;
    private boolean maidSitting;
    private String nearbyThreat;
    private String lastVisionSummary;
    private String lastPlayerUtterance;
    private String lastReply;
    private String lastActionSummary;
    private String currentWorkTask;
    private String currentSchedule;

    private SimulationCoordinate maidPosition;
    private SimulationCoordinate ownerPosition;
    private SimulationCoordinate homePosition;

    private int favorabilityTotal;
    private int favorabilityDailyGain;
    private int favorabilityChatGain;
    private int favorabilityActionGain;
    private int favorabilityCareGain;
    private LocalDate favorabilityResetDay;

    private String followPolicy;
    private String followPolicySource;
    private String followPolicyReason;
    private String lastOwnerExplicitCommand;

    private boolean chatSessionActive;
    private long chatSessionExpireTick;
    private long lastReplyTick;
    private long lastCaptureTick;

    private boolean previousSleeping;
    private long sleepCycleId;
    private boolean sleepEnterAnnounced;
    private boolean sleepExitAnnounced;

    private final Map<String, Long> lastReplyTickByEventType = new HashMap<>();
    private final Map<String, Long> lastFavorabilityGainTickByBucket = new HashMap<>();

    public SimulationEnvironment(String maidId, String ownerId, String maidName, String ownerName) {
        this.maidId = maidId;
        this.ownerId = ownerId;
        this.maidName = maidName;
        this.ownerName = ownerName;
        this.tick = 1000L;
        this.maidHealth = 20.0D;
        this.maidEnergy = 0.82D;
        this.maidHunger = 0.28D;
        this.maidSleeping = false;
        this.maidSitting = false;
        this.nearbyThreat = "none";
        this.lastVisionSummary = "Area is calm and no obvious danger is visible.";
        this.lastPlayerUtterance = "";
        this.lastReply = "";
        this.lastActionSummary = "idle";
        this.currentWorkTask = "idle";
        this.currentSchedule = "ALL";
        this.maidPosition = new SimulationCoordinate(0, 64, 0);
        this.ownerPosition = new SimulationCoordinate(2, 64, 1);
        this.homePosition = new SimulationCoordinate(0, 64, 0);

        this.favorabilityTotal = 35;
        this.favorabilityDailyGain = 0;
        this.favorabilityChatGain = 0;
        this.favorabilityActionGain = 0;
        this.favorabilityCareGain = 0;
        this.favorabilityResetDay = LocalDate.now();

        this.followPolicy = "DEFAULT_FOLLOW";
        this.followPolicySource = "system_default";
        this.followPolicyReason = "follow owner by default";
        this.lastOwnerExplicitCommand = "";

        this.chatSessionActive = false;
        this.chatSessionExpireTick = 0L;
        this.lastReplyTick = Long.MIN_VALUE / 4;
        this.lastCaptureTick = tick;

        this.previousSleeping = false;
        this.sleepCycleId = 0L;
        this.sleepEnterAnnounced = false;
        this.sleepExitAnnounced = false;
    }

    public String maidId() { return maidId; }
    public String ownerId() { return ownerId; }
    public String maidName() { return maidName; }
    public String ownerName() { return ownerName; }
    public long tick() { return tick; }

    public void advanceTick(long delta) {
        long safeDelta = Math.max(1L, delta);
        this.tick += safeDelta;
        resetFavorabilityIfNeeded();
        refreshChatSessionState();
        double passiveEnergyChange = maidSleeping ? 0.0035D * safeDelta : 0.0008D * safeDelta;
        this.maidEnergy = clamp01(this.maidEnergy + passiveEnergyChange);
        this.maidHunger = clamp01(this.maidHunger + 0.0025D * safeDelta);
    }

    public double maidHealth() { return maidHealth; }
    public void setMaidHealth(double maidHealth) { this.maidHealth = Math.max(0.0D, Math.min(20.0D, maidHealth)); }
    public double maidEnergy() { return maidEnergy; }
    public void setMaidEnergy(double maidEnergy) { this.maidEnergy = clamp01(maidEnergy); }
    public double maidHunger() { return maidHunger; }
    public void setMaidHunger(double maidHunger) { this.maidHunger = clamp01(maidHunger); }
    public boolean maidSleeping() { return maidSleeping; }

    public void setMaidSleeping(boolean maidSleeping) {
        this.previousSleeping = this.maidSleeping;
        this.maidSleeping = maidSleeping;
        if (!this.previousSleeping && maidSleeping) {
            this.sleepCycleId++;
            this.sleepEnterAnnounced = false;
            this.sleepExitAnnounced = false;
        }
    }

    public boolean maidSitting() { return maidSitting; }
    public void setMaidSitting(boolean maidSitting) { this.maidSitting = maidSitting; }
    public String nearbyThreat() { return nearbyThreat; }
    public void setNearbyThreat(String nearbyThreat) { this.nearbyThreat = blankToDefault(nearbyThreat, "none"); }
    public String lastVisionSummary() { return lastVisionSummary; }
    public void setLastVisionSummary(String lastVisionSummary) { this.lastVisionSummary = blankToDefault(lastVisionSummary, "no vision input"); }
    public String lastPlayerUtterance() { return lastPlayerUtterance; }
    public void setLastPlayerUtterance(String lastPlayerUtterance) { this.lastPlayerUtterance = blankToDefault(lastPlayerUtterance, ""); }
    public String lastReply() { return lastReply; }
    public void setLastReply(String lastReply) { this.lastReply = blankToDefault(lastReply, ""); }
    public String lastActionSummary() { return lastActionSummary; }
    public void setLastActionSummary(String lastActionSummary) { this.lastActionSummary = blankToDefault(lastActionSummary, "idle"); }
    public String currentWorkTask() { return currentWorkTask; }
    public void setCurrentWorkTask(String currentWorkTask) { this.currentWorkTask = blankToDefault(currentWorkTask, "idle"); }
    public String currentSchedule() { return currentSchedule; }
    public void setCurrentSchedule(String currentSchedule) { this.currentSchedule = blankToDefault(currentSchedule, "ALL"); }
    public SimulationCoordinate maidPosition() { return maidPosition; }
    public void setMaidPosition(SimulationCoordinate maidPosition) { this.maidPosition = maidPosition; }
    public SimulationCoordinate ownerPosition() { return ownerPosition; }
    public void setOwnerPosition(SimulationCoordinate ownerPosition) { this.ownerPosition = ownerPosition; }
    public SimulationCoordinate homePosition() { return homePosition; }
    public void setHomePosition(SimulationCoordinate homePosition) { this.homePosition = homePosition; }
    public int favorabilityTotal() { return favorabilityTotal; }
    public int favorabilityDailyGain() { return favorabilityDailyGain; }
    public int favorabilityChatGain() { return favorabilityChatGain; }
    public int favorabilityActionGain() { return favorabilityActionGain; }
    public int favorabilityCareGain() { return favorabilityCareGain; }
    public String followPolicy() { return followPolicy; }
    public String followPolicySource() { return followPolicySource; }
    public String followPolicyReason() { return followPolicyReason; }
    public String lastOwnerExplicitCommand() { return lastOwnerExplicitCommand; }

    public boolean chatSessionActive() {
        refreshChatSessionState();
        return chatSessionActive;
    }

    public String energyState() {
        if (maidEnergy < 0.20D) return "EXHAUSTED";
        if (maidEnergy < 0.50D) return "LOW";
        if (maidEnergy < 0.80D) return "NORMAL";
        return "HIGH";
    }

    public boolean canStartWorkTask() {
        return maidEnergy >= 0.20D;
    }

    public boolean shouldDefaultFollow() {
        return !"EXPLICIT_STAY".equals(followPolicy);
    }

    public void setExplicitStayPolicy(String reason) {
        this.followPolicy = "EXPLICIT_STAY";
        this.followPolicySource = "owner_command";
        this.followPolicyReason = blankToDefault(reason, "owner asked maid to stay");
        this.lastOwnerExplicitCommand = "stay_here";
    }

    public void setDefaultFollowPolicy(String reason) {
        this.followPolicy = "DEFAULT_FOLLOW";
        this.followPolicySource = "owner_command";
        this.followPolicyReason = blankToDefault(reason, "owner asked maid to follow");
        this.lastOwnerExplicitCommand = "follow_owner";
    }

    public void startChatSession() {
        this.chatSessionActive = true;
        this.chatSessionExpireTick = tick + CHAT_SESSION_KEEPALIVE_TICKS;
    }

    public void markCapture() { this.lastCaptureTick = tick; }
    public long lastCaptureTick() { return lastCaptureTick; }
    public long captureIntervalTicks() { return DEFAULT_CAPTURE_INTERVAL_TICKS; }
    public long currentSpeakCooldownTicks() { return chatSessionActive() ? CHAT_SESSION_SPEAK_COOLDOWN_TICKS : captureIntervalTicks(); }

    public boolean canSpeakForEvent(String eventType) {
        long globalCooldown = currentSpeakCooldownTicks();
        if (tick - lastReplyTick < globalCooldown) return false;
        long eventCooldown = switch (eventType) {
            case "maid.attacked" -> 120L;
            case "owner.feed", "owner.interact" -> 80L;
            case "maid.sleep.enter", "maid.sleep.exit" -> Long.MAX_VALUE / 8;
            default -> 0L;
        };
        long lastByType = lastReplyTickByEventType.getOrDefault(eventType, Long.MIN_VALUE / 4);
        return tick - lastByType >= eventCooldown;
    }

    public void markSpoke(String eventType) {
        this.lastReplyTick = tick;
        this.lastReplyTickByEventType.put(eventType, tick);
    }

    public void spendEnergy(double amount) { this.maidEnergy = clamp01(this.maidEnergy - Math.max(0.0D, amount)); }
    public void recoverEnergy(double amount) { this.maidEnergy = clamp01(this.maidEnergy + Math.max(0.0D, amount)); }

    public boolean tryGainFavorability(String bucket, int amount, String reason) {
        resetFavorabilityIfNeeded();
        if (amount <= 0 || favorabilityDailyGain >= 30) return false;
        long cooldown = switch (bucket) {
            case "chat" -> 200L;
            case "action" -> 120L;
            case "care" -> 300L;
            default -> 0L;
        };
        long lastTick = lastFavorabilityGainTickByBucket.getOrDefault(bucket, Long.MIN_VALUE / 4);
        if (tick - lastTick < cooldown) return false;
        int bucketValue = switch (bucket) {
            case "chat" -> favorabilityChatGain;
            case "action" -> favorabilityActionGain;
            case "care" -> favorabilityCareGain;
            default -> 0;
        };
        if (bucketValue >= 10) return false;
        int actual = Math.min(amount, Math.min(10 - bucketValue, 30 - favorabilityDailyGain));
        if (actual <= 0) return false;
        this.favorabilityTotal += actual;
        this.favorabilityDailyGain += actual;
        switch (bucket) {
            case "chat" -> this.favorabilityChatGain += actual;
            case "action" -> this.favorabilityActionGain += actual;
            case "care" -> this.favorabilityCareGain += actual;
            default -> { }
        }
        this.lastFavorabilityGainTickByBucket.put(bucket, tick);
        this.lastActionSummary = "favor+" + actual + " reason=" + reason;
        return true;
    }

    public String energyLabel() {
        return switch (energyState()) {
            case "EXHAUSTED" -> "drained";
            case "LOW" -> "tired";
            case "NORMAL" -> "normal";
            default -> "high";
        };
    }

    public boolean isNight() { return tick % 24000L >= 12000L; }

    public String ownerDistanceLabel() {
        int dx = maidPosition.x() - ownerPosition.x();
        int dy = maidPosition.y() - ownerPosition.y();
        int dz = maidPosition.z() - ownerPosition.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return String.format(Locale.ROOT, "%.1f blocks", distance);
    }

    public long sleepCycleId() { return sleepCycleId; }
    public boolean sleepEnterAnnounced() { return sleepEnterAnnounced; }
    public void setSleepEnterAnnounced(boolean sleepEnterAnnounced) { this.sleepEnterAnnounced = sleepEnterAnnounced; }
    public boolean sleepExitAnnounced() { return sleepExitAnnounced; }
    public void setSleepExitAnnounced(boolean sleepExitAnnounced) { this.sleepExitAnnounced = sleepExitAnnounced; }

    /**
     * 给扫描类工具返回世界摘要。
     */
    public String worldStateSummary() {
        return "time=" + (isNight() ? "night" : "day")
                + ", schedule=" + currentSchedule
                + ", threat=" + nearbyThreat
                + ", vision=" + lastVisionSummary;
    }

    /**
     * 给扫描类工具返回女仆自身状态。
     */
    public String selfStateSummary() {
        return "hp=" + String.format(Locale.ROOT, "%.1f/20", maidHealth)
                + ", energy=" + Math.round(maidEnergy * 100.0D) + "%(" + energyState() + ")"
                + ", hunger=" + Math.round(maidHunger * 100.0D) + "%"
                + ", sleeping=" + maidSleeping
                + ", sitting=" + maidSitting
                + ", follow=" + followPolicy
                + ", task=" + currentWorkTask
                + ", schedule=" + currentSchedule;
    }

    /**
     * 给主人视角工具返回一个简化状态。
     */
    public String ownerStateSummary() {
        String ownerHealth = nearbyThreat.equals("none") ? "20/20" : "17/20";
        String ownerMainHand = nearbyThreat.equals("none") ? "minecraft:bread" : "minecraft:iron_sword";
        return "name=" + ownerName
                + ", health=" + ownerHealth
                + ", mainhand=" + ownerMainHand
                + ", distance=" + ownerDistanceLabel();
    }

    /**
     * 位置关系摘要。
     */
    public String positionStateSummary() {
        return "maid=" + maidPosition.shortText()
                + ", owner=" + ownerPosition.shortText()
                + ", home=" + homePosition.shortText()
                + ", distance_to_owner=" + ownerDistanceLabel()
                + ", light=" + (isNight() ? 6 : 12);
    }

    /**
     * 用一个轻量摘要模拟 TLM 的装备/背包上下文。
     */
    public String inventoryStateSummary() {
        return "mainhand=" + inferMainHandByTask()
                + ", bag=[torch x16, wheat_seeds x24, fishing_rod x1, shears x1, bucket x1, bread x8]";
    }

    /**
     * 用一个轻量摘要模拟附近实体上下文。
     */
    public String nearbyEntitiesSummary() {
        if (!"none".equals(nearbyThreat)) {
            return "hostile=" + nearbyThreat + " distance=5.0 owner_distance=6.0";
        }
        return "entities=[cow distance=8.0, sheep distance=10.0, chicken distance=6.0]";
    }

    public String describe() {
        return """
                owner=%s %s
                maid=%s hp=%.1f/20 energy=%.0f%%(%s) hunger=%.0f%% sleeping=%s sitting=%s
                work: task=%s schedule=%s
                favor: total=%d daily=%d chat=%d action=%d care=%d
                follow: %s source=%s reason=%s
                pos: maid=%s owner=%s home=%s distance=%s
                world: time=%s(%d) threat=%s
                vision: %s
                memory: last_text=\"%s\" last_action=%s last_reply=\"%s\"
                """.formatted(
                ownerName, ownerId, maidName, maidHealth, maidEnergy * 100.0D, energyLabel(), maidHunger * 100.0D,
                maidSleeping ? "yes" : "no", maidSitting ? "yes" : "no",
                currentWorkTask, currentSchedule,
                favorabilityTotal, favorabilityDailyGain, favorabilityChatGain, favorabilityActionGain, favorabilityCareGain,
                followPolicy, followPolicySource, followPolicyReason,
                maidPosition.shortText(), ownerPosition.shortText(), homePosition.shortText(), ownerDistanceLabel(),
                isNight() ? "night" : "day", tick, nearbyThreat, lastVisionSummary,
                lastPlayerUtterance, lastActionSummary, lastReply
        );
    }

    private void resetFavorabilityIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(favorabilityResetDay)) {
            favorabilityResetDay = today;
            favorabilityDailyGain = 0;
            favorabilityChatGain = 0;
            favorabilityActionGain = 0;
            favorabilityCareGain = 0;
            lastFavorabilityGainTickByBucket.clear();
        }
    }

    private void refreshChatSessionState() {
        if (chatSessionActive && tick > chatSessionExpireTick) {
            chatSessionActive = false;
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String inferMainHandByTask() {
        return switch (currentWorkTask) {
            case "attack" -> "minecraft:iron_sword";
            case "ranged_attack" -> "minecraft:bow";
            case "crossbow_attack" -> "minecraft:crossbow";
            case "danmaku_attack" -> "hakurei_gohei";
            case "trident_attack" -> "minecraft:trident";
            case "fishing" -> "minecraft:fishing_rod";
            case "shears" -> "minecraft:shears";
            case "milk" -> "minecraft:bucket";
            default -> "minecraft:air";
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
