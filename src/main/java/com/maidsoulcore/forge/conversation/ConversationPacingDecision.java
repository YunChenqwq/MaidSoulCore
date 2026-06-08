package com.maidsoulcore.forge.conversation;

/**
 * Result of lightweight turn pacing.
 */
public record ConversationPacingDecision(
        ConversationPacingAction action,
        String reason,
        long waitMillis
) {
    public static ConversationPacingDecision continueNow(String reason) {
        return new ConversationPacingDecision(ConversationPacingAction.CONTINUE, reason, 0L);
    }

    public static ConversationPacingDecision waitFor(String reason, long waitMillis) {
        return new ConversationPacingDecision(ConversationPacingAction.WAIT, reason, Math.max(0L, waitMillis));
    }

    public static ConversationPacingDecision noReply(String reason) {
        return new ConversationPacingDecision(ConversationPacingAction.NO_REPLY, reason, 0L);
    }

    public static ConversationPacingDecision finish(String reason) {
        return new ConversationPacingDecision(ConversationPacingAction.FINISH, reason, 0L);
    }
}
