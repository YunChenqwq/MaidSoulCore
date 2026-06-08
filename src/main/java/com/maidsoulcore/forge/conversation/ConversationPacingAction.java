package com.maidsoulcore.forge.conversation;

/**
 * Local pacing decision for a conversation turn.
 */
public enum ConversationPacingAction {
    CONTINUE,
    WAIT,
    NO_REPLY,
    FINISH
}
