package com.maidsoul.brain.reasoning;

record TimingDecision(String action, int waitSeconds, String reason) {
    static TimingDecision continueNow(String reason) {
        return new TimingDecision("continue", 0, reason);
    }
}

