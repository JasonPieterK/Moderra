package com.jason.plugins.antibadwords.engine.discord;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;
import java.util.List;

/**
 * Settings of the DiscordSRV hook (Bukkit family only), in {@code discord.yml} next to the webhook settings. Everything about
 * Discord-to-Minecraft filtering and moderation from Discord is OFF by default.
 */
public final class DiscordSrvSettings {
    public static final String ACTION_BLOCK = "block";
    public static final String ACTION_CENSOR = "censor";
    public static final String ACTION_LOG_ONLY = "log-only";
    /** The source name of messages that come from Discord. */
    public static final String SOURCE = "discord-in";
    private static final String CATEGORY = "Discord";
    private static final String D = "discord.discordsrv.";
    private static final String I = "discord.inbound.";
    private static final String C = "discord.commands.";

    public static final Setting<String> ALERT_CHANNEL = s(Setting.string(D + "alert-channel", "alerts").title("DiscordSRV alerts channel")
        .description("BUKKIT FAMILY ONLY, needs DiscordSRV. The DiscordSRV channel NAME (from DiscordSRV's config.yml, option Channels,\n"
            + "for example global) that receives the kinds listed in discord.routing.alerts. Empty = none."));
    public static final Setting<String> LOG_CHANNEL = s(Setting.string(D + "log-channel", "").title("DiscordSRV log channel")
        .description("Same for the kinds listed in discord.routing.log (a quiet log channel). Empty = none."));

    public static final Setting<Boolean> INBOUND_ENABLED = s(Setting.bool(I + "enabled", false).title("Filter Discord to Minecraft")
        .description("BUKKIT FAMILY ONLY, needs DiscordSRV. Check messages that arrive from Discord with the same word lists before\n"
            + "they are shown in the game (source " + SOURCE + "). The link and bypass-trick filters only look at it when\n"
            + SOURCE + " is added to their sources list. Default off."));
    public static final Setting<String> INBOUND_ACTION = s(Setting.choice(I + "action", ACTION_BLOCK, ACTION_BLOCK, ACTION_CENSOR,
        ACTION_LOG_ONLY).title("Action for Discord messages")
        .description("block = the message is not shown in the game, censor = shown with the bad words cleaned (only when the server can\n"
            + "change the text; DiscordSRV cannot, so it is blocked instead), log-only = shown unchanged, only recorded."));
    public static final Setting<List<String>> INBOUND_CHANNELS = s(Setting.list(I + "channels", List.of("*"))
        .title("Discord channels that are checked")
        .description("Discord channel ids or names; \"*\" = every channel DiscordSRV relays. Anything not listed is not checked."));
    public static final Setting<List<String>> INBOUND_EXCEPT = s(Setting.list(I + "except-channels", List.of())
        .title("Discord channels that are not checked").description("Ids or names; wins over the list above."));
    public static final Setting<List<String>> BYPASS_ROLES = s(Setting.list(I + "bypass-roles", List.of())
        .title("Roles that are not checked").description("Discord ROLE ids (numbers). People with one of these roles are never filtered."));
    public static final Setting<List<String>> BYPASS_USERS = s(Setting.list(I + "bypass-users", List.of())
        .title("Discord users that are not checked").description("Discord USER ids (numbers)."));
    public static final Setting<Boolean> NOTIFY_SENDER = s(Setting.bool(I + "notify-sender", true).title("Tell the sender")
        .description("Send the person a direct message in Discord when their message was blocked or cleaned."));
    public static final Setting<String> NOTIFY_TEXT = s(Setting.string(I + "notify-text",
            "Your message was not delivered to the Minecraft chat because it contains words that are not allowed on this server.")
        .title("Text for the sender").description("The direct message. No placeholders; never put the blocked word in it."));

    public static final Setting<String> PREFIX = s(Setting.string(C + "prefix", "!abw").title("Command prefix in Discord")
        .description("Moderation from Discord works by typing a message that starts with this prefix, for example: !abw mute Steve 1h spam\n"
            + "(DiscordSRV slash commands are not used by this plugin.) A prefix message is never relayed to Minecraft."));
    public static final Setting<Boolean> MUTE = command("mute", "mute <player> [time] [reason]");
    public static final Setting<Boolean> UNMUTE = command("unmute", "unmute <player>");
    public static final Setting<Boolean> HISTORY = command("history", "history <player> [page]");
    public static final Setting<Boolean> STRIKES = command("strikes", "strikes <player>");
    public static final Setting<Boolean> CHECK = command("check", "check <text>");
    public static final Setting<Boolean> RELOAD = command("reload", "reload");
    public static final Setting<List<String>> ALLOWED_ROLES = s(Setting.list(C + "allowed-roles", List.of())
        .title("Roles that may use the commands").description("Discord ROLE ids (numbers). Empty list AND empty users list = nobody can\n"
            + "run any command (the safe default). Being allowed is not enough: the command itself must be switched on above."));
    public static final Setting<List<String>> ALLOWED_USERS = s(Setting.list(C + "allowed-users", List.of())
        .title("Discord users that may use the commands").description("Discord USER ids (numbers)."));
    public static final Setting<List<String>> COMMAND_CHANNELS = s(Setting.list(C + "channels", List.of())
        .title("Channels where commands are read").description("Discord channel ids or names where the prefix is understood.\n"
            + "Empty = nowhere (the safe default); \"*\" = every channel DiscordSRV sees. Use a private staff channel."));
    public static final Setting<Integer> RATE_MAX = s(Setting.integer(C + "rate.max", 5).range(1, 100).title("Commands per person")
        .description("How many commands one Discord user may run within the time below."));
    public static final Setting<Duration> RATE_WINDOW = s(Setting.duration(C + "rate.window", Duration.ofSeconds(30)).range(1, 3600)
        .title("Rate limit window").description("Time window for the limit above."));

    private DiscordSrvSettings() {
    }

    private static Setting<Boolean> command(String name, String usage) {
        return s(Setting.bool(C + name + ".enabled", false).title("Discord command: " + name)
            .description("Allow \"<prefix> " + usage + "\" from Discord (only for the allowed roles / users). Default off."));
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(DiscordSettings.FILE).category(CATEGORY).build();
    }

    /** The switch of a command word, or null for a word that cannot be run from Discord. */
    public static Setting<Boolean> commandSwitch(String word) {
        return switch (word) {
            case "mute" -> MUTE;
            case "unmute" -> UNMUTE;
            case "history" -> HISTORY;
            case "strikes" -> STRIKES;
            case "check" -> CHECK;
            case "reload" -> RELOAD;
            default -> null;
        };
    }

    public static void register(SettingsRegistry r) {
        List.of(ALERT_CHANNEL, LOG_CHANNEL, INBOUND_ENABLED, INBOUND_ACTION, INBOUND_CHANNELS, INBOUND_EXCEPT, BYPASS_ROLES, BYPASS_USERS,
            NOTIFY_SENDER, NOTIFY_TEXT, PREFIX, MUTE, UNMUTE, HISTORY, STRIKES, CHECK, RELOAD, ALLOWED_ROLES, ALLOWED_USERS, COMMAND_CHANNELS,
            RATE_MAX, RATE_WINDOW).forEach(r::register);
    }
}
