package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import java.util.List;

/** Settings of the outgoing-text scan ({@code integrations.yml}, keys under {@code integrations.outgoing-scan.}). Bukkit family only. */
public final class OutgoingScanSettings {
    private static final String CATEGORY = "Integrations";
    private static final String BUKKIT = "BUKKIT FAMILY ONLY. ";

    public static final String ACTION_LOG = "log";
    public static final String ACTION_MASK = "mask";
    public static final String ACTION_CANCEL = "cancel";
    public static final String SOURCE_SYSTEM_CHAT = "system-chat";
    public static final String SOURCE_PLAYER_CHAT = "player-chat";
    public static final String SOURCE_TEAM = "team";
    public static final String SOURCE_TAB_LIST = "tab-list";

    public static final Setting<Boolean> ENABLED = s(Setting.bool("integrations.outgoing-scan.enabled", false).title("Outgoing text scan on")
        .description(BUKKIT + "Checks the chat text that OTHER plugins send to players (for example VentureChat or HuskChat chat, or a plugin\n"
            + "that broadcasts player text). Needs ProtocolLib or PacketEvents. The text is only judged: no strike is counted and no\n"
            + "player is punished. Messages of this plugin are never touched. Needs a restart."));
    public static final Setting<String> ACTION = s(Setting.choice("integrations.outgoing-scan.action", ACTION_LOG, ACTION_LOG, ACTION_MASK,
        ACTION_CANCEL).title("Outgoing scan action")
        .description("log = write one line to the server log (default). mask = replace the offending text with *.\n"
            + "cancel = do not send the packet; team and tab-list packets are never cancelled (that would break the scoreboard), they are masked."));
    public static final Setting<List<String>> SOURCES = s(Setting.list("integrations.outgoing-scan.sources", List.of(SOURCE_SYSTEM_CHAT, SOURCE_PLAYER_CHAT, SOURCE_TEAM, SOURCE_TAB_LIST))
        .title("Outgoing scan sources")
        .description("Packet kinds that are checked: system-chat (messages from plugins and the server), player-chat (the text of a player chat\n"
            + "packet), team (team display name, prefix and suffix, as set by TAB or NametagEdit) and tab-list (tab-list display names)."));
    public static final Setting<List<String>> IGNORE = s(Setting.list("integrations.outgoing-scan.ignore-containing", List.of("[Moderra]"))
        .title("Outgoing scan ignore list")
        .description("A message that contains one of these texts is never checked. The default keeps the messages of this plugin untouched.\n"
            + "Add the prefix of your own message texts here when you changed it."));
    public static final Setting<Integer> LOG_LIMIT = s(Setting.integer("integrations.outgoing-scan.log-limit-per-minute", 30).range(1, 10000)
        .title("Outgoing scan log limit")
        .description("Maximum log lines of this scan per minute. Further hits in the same minute are counted and shown in one summary line."));

    private OutgoingScanSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(ExtraIntegrationSettings.FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry r) {
        List.of(ENABLED, ACTION, SOURCES, IGNORE, LOG_LIMIT).forEach(r::register);
    }
}
