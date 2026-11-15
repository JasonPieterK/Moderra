package com.jason.plugins.antibadwords.engine.gui.prompt;

import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;
import java.util.List;

/** Settings of the text prompts used by the menu ({@code config.yml}, section {@code gui.prompt}). */
public final class PromptSettings {
    private static final String CATEGORY = "General";

    public static final Setting<String> MODE = Setting.choice("gui.prompt.mode", "auto", "auto", "sign", "chat")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input method")
        .description("How the menu asks a player to type a value. auto = a sign editor where the server software supports it,\n"
            + "otherwise the chat. chat = always the chat (the player's next chat message is taken, never shown to anyone,\n"
            + "never checked or saved). sign = sign editor, falls back to chat where it is not available.").build();
    public static final Setting<Duration> TIMEOUT = Setting.duration("gui.prompt.timeout", Duration.ofSeconds(60)).range(5, 3600)
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input timeout")
        .description("A text input is cancelled when the player does not answer within this time.").build();
    public static final Setting<String> CANCEL_WORD = Setting.string("gui.prompt.cancel-word", "cancel")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input cancel word")
        .description("Typing this word (any letter case) in the chat prompt aborts it.").build();
    public static final Setting<String> MESSAGE_CHAT = Setting.string("gui.prompt.messages.chat",
            "&8[&6&lAntiBadWords&8]&r &b&lInput needed. &7Type the new value for &f{title}&7 in the chat. &7&o{hint}\\n&7Type &f{cancel}&7 to cancel. Time limit: &f{seconds}&7 seconds.")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input: chat text")
        .description("Shown when a chat input starts. Placeholders: {title}, {hint}, {cancel}, {seconds}. \\n = new line.").build();
    public static final Setting<String> MESSAGE_SIGN = Setting.string("gui.prompt.messages.sign",
            "&8[&6&lAntiBadWords&8]&r &b&lInput needed. &7Write the new value for &f{title}&7 on the sign. The lines are joined with spaces. &7&o{hint}\\n&7Leave the sign empty to cancel. Time limit: &f{seconds}&7 seconds.")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input: sign text")
        .description("Shown when a sign input starts. Placeholders: {title}, {hint}, {seconds}. \\n = new line.").build();
    public static final Setting<String> MESSAGE_ERROR = Setting.string("gui.prompt.messages.error",
            "&8[&6&lAntiBadWords&8]&r &c&lInvalid value. &7{error} &7Try again, or type &f{cancel}&7 to cancel.")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input: error text")
        .description("Shown when the typed value is not valid. Placeholders: {error}, {cancel}.").build();
    public static final Setting<String> MESSAGE_CANCELLED = Setting.string("gui.prompt.messages.cancelled", "&8[&6&lAntiBadWords&8]&r &e&lCancelled. &7The input is cancelled.")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input: cancelled text")
        .description("Shown when the player aborts a text input.").build();
    public static final Setting<String> MESSAGE_TIMEOUT = Setting.string("gui.prompt.messages.timeout",
            "&8[&6&lAntiBadWords&8]&r &e&lCancelled. &7The time limit ended. The input is cancelled.")
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Text input: timeout text")
        .description("Shown when a text input ran out of time.").build();

    private PromptSettings() {
    }

    public static void register(SettingsRegistry r) {
        for (Setting<?> s : List.of(MODE, TIMEOUT, CANCEL_WORD, MESSAGE_CHAT, MESSAGE_SIGN, MESSAGE_ERROR, MESSAGE_CANCELLED,
            MESSAGE_TIMEOUT)) {
            r.register(s);
        }
    }
}
