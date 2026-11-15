package com.jason.plugins.moderra.engine.gui.prompt;

/** How a prompt collects its text. */
public enum PromptKind {
    /** The player types into a sign editor (needs {@code Platform.supportsSignPrompt()}). */
    SIGN,
    /** The player's next chat message is captured (works everywhere). */
    CHAT
}
