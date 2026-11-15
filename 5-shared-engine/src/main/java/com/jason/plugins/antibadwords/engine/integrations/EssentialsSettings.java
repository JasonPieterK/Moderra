package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;

/** Settings of the plugin integrations ({@code integrations.yml}). Everything is optional and does nothing when the other plugin is missing. */
public final class EssentialsSettings {
    public static final String FILE = "integrations.yml";
    private static final String CATEGORY = "Integrations";
    private static final String E = "integrations.essentials.";
    private static final String BUKKIT = "BUKKIT FAMILY ONLY (EssentialsX has no Fabric or Forge version). ";

    public static final String MUTE_OWN = "own";
    public static final String MUTE_ESSENTIALS = "essentials";
    public static final String MUTE_BOTH = "both";
    public static final String NICK_OFF = "off";
    public static final String NICK_ONLY = "nickname";
    public static final String NICK_BOTH = "both";
    public static final String PRIORITY_AUTO = "auto";
    public static final String PRIORITY_LOWEST = "lowest";
    public static final String PRIORITY_HIGHEST = "highest";
    public static final String NICK_LOG = "log";
    public static final String NICK_WARN = "warn";
    public static final String NICK_RESET = "reset";

    public static final Setting<Boolean> ESSENTIALS = s(Setting.bool(E + "enabled", true).title("EssentialsX hook on")
        .description(BUKKIT + "Master switch of everything in this section. It only does something when EssentialsX is installed.\n"
            + "Needs a restart (the plugin looks for EssentialsX while it starts)."));
    public static final Setting<String> CHAT_PRIORITY = s(Setting.choice(E + "chat-priority", PRIORITY_AUTO, PRIORITY_AUTO, PRIORITY_LOWEST,
        PRIORITY_HIGHEST).title("Chat check priority")
        .description(BUKKIT + "When the chat check runs. EssentialsX Chat formats and sends chat from the LOWEST priority on, so our check must be\n"
            + "first: auto = LOWEST when EssentialsChat is installed, otherwise HIGHEST (the old behaviour); lowest / highest = always.\n"
            + "A blocked message is cancelled AND its recipient list is emptied, so nothing re-sends it. Needs a restart."));
    public static final Setting<String> MUTE_MODE = s(Setting.choice(E + "mute-mode", MUTE_OWN, MUTE_OWN, MUTE_ESSENTIALS, MUTE_BOTH)
        .title("Which mute is used")
        .description(BUKKIT + "own = only the built-in mute (EssentialsX mutes are ignored). essentials = the ladder and /abw mute use the Essentials\n"
            + "mute (its own storage and messages) and Essentials-muted players are held back here too. both = both at once.\n"
            + "Falls back to the built-in mute when Essentials is not there or refuses."));
    public static final Setting<String> NICKNAME_IN_MESSAGES = s(Setting.choice(E + "nickname-in-messages", NICK_OFF, NICK_OFF, NICK_ONLY, NICK_BOTH)
        .title("Nicknames in our messages")
        .description(BUKKIT + "off = {player} is always the real name. nickname = the /nick nickname is shown when there is one in the warning and\n"
            + "punishment broadcasts and the staff notice. both = \"Nick (RealName)\". Console commands and events always use the real name."));
    public static final Setting<Boolean> HIDE_VANISHED = s(Setting.bool(E + "hide-vanished", true).title("Do not expose vanished players")
        .description(BUKKIT + "Broadcast warnings and punishments about a vanished player (Essentials /vanish, or Bukkit metadata \"vanished\")\n"
            + "go only to players with antibadwords.notify instead of everybody."));
    public static final Setting<List<String>> SCAN_COMMANDS = s(Setting.list(E + "scan-commands",
            List.of("nick", "nickname", "msg", "m", "tell", "t", "w", "whisper", "r", "reply", "mail", "me", "action", "broadcast", "bc",
                "bcast", "helpop", "ac"))
        .title("Essentials commands that are scanned")
        .description(BUKKIT + "Added to commands.scanned-commands while EssentialsX is installed (arguments are checked like chat; a blocked command\n"
            + "never runs, so social spy, mail and helpop never see the text). commands.ignored-commands still wins."));
    public static final Setting<Boolean> CHECK_NICKNAME = s(Setting.bool(E + "check-nickname-on-join", true).title("Check nicknames on join")
        .description(BUKKIT + "A nickname that was set earlier is checked with the word lists when its owner joins (a new /nick is checked as a command)."));
    public static final Setting<String> NICKNAME_ACTION = s(Setting.choice(E + "nickname-action", NICK_WARN, NICK_LOG, NICK_WARN, NICK_RESET)
        .title("Action for a bad nickname")
        .description("log = only the console and the event history, warn = also tell the player (message below), reset = also remove the nickname."));
    public static final Setting<String> NICKNAME_MESSAGE = s(Setting.string(E + "nickname-message",
            "&8[&6&lAntiBadWords&8]&r &c&lNickname rejected. &7Your nickname contains a word that is not permitted. &7&oChoose a different nickname.").title("Message for a bad nickname")
        .description("Sent to the player for the actions warn and reset. No placeholders."));

    private EssentialsSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry r) {
        List.of(ESSENTIALS, CHAT_PRIORITY, MUTE_MODE, NICKNAME_IN_MESSAGES, HIDE_VANISHED, SCAN_COMMANDS, CHECK_NICKNAME, NICKNAME_ACTION,
            NICKNAME_MESSAGE).forEach(r::register);
    }
}
