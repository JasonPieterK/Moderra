package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Chat-channel plugins (ChatControl, VentureChat, HuskChat, CarbonChat): settings ({@code integrations.chatchannels.*} in
 * {@code integrations.yml}) and the pure logic that maps a channel name to a rule profile of {@code worlds.yml}.
 *
 * <p>A channel name reaches the engine as {@link com.jason.plugins.antibadwords.engine.Message#channel()}: a loader sets it when it
 * knows it, or the engine derives it from the command a player typed (the {@code channel-commands} map, such as {@code g -> global}).
 */
public final class ChatChannels {
    public static final String FILE = "integrations.yml";
    private static final String CATEGORY = "Integrations";
    private static final String C = "integrations.chatchannels.";
    private static final String BUKKIT = "BUKKIT FAMILY ONLY. ";
    public static final String PRIORITY_AUTO = "auto";
    public static final String PRIORITY_LOWEST = "lowest";
    public static final String PRIORITY_HIGHEST = "highest";

    /** Plugin names as {@code plugin.yml} spells them. */
    public static final List<String> PLUGIN_NAMES = List.of("ChatControl", "VentureChat", "HuskChat", "CarbonChat");

    public static final Setting<Boolean> ENABLED = s(Setting.bool(C + "enabled", true).title("Chat-channel plugins")
        .description("Master switch for everything about chat-channel plugins (ChatControl, VentureChat, HuskChat, CarbonChat):\n"
            + "the chat priority below and the channel profiles. false = ignore their presence and the maps below."));
    public static final Setting<String> PRIORITY = s(Setting.choice(C + "chat-priority", PRIORITY_AUTO, PRIORITY_AUTO, PRIORITY_LOWEST,
        PRIORITY_HIGHEST).title("Chat check priority with channel plugins")
        .description(BUKKIT + "When the chat check runs if one of the plugins above is installed. Those plugins format and send chat on\n"
            + "their own, so our check must run before them: auto = LOWEST when one of them is found (otherwise the setting\n"
            + "integrations.essentials.chat-priority decides); lowest / highest = always. Needs a restart. Which priority these\n"
            + "plugins really use was NOT verified against them; if a blocked line still appears, set this to lowest and report it."));
    public static final Setting<Boolean> CHATCONTROL = plugin("chatcontrol", "ChatControl");
    public static final Setting<Boolean> VENTURECHAT = plugin("venturechat", "VentureChat");
    public static final Setting<Boolean> HUSKCHAT = plugin("huskchat", "HuskChat");
    public static final Setting<Boolean> CARBONCHAT = plugin("carbonchat", "CarbonChat");

    public static final Section CHANNEL_PROFILES = new Section(C + "channel-profiles", FILE, CATEGORY, "Profile by chat channel",
        "Which rule profile (see worlds.yml) a chat channel gets: channel name (or pattern with *) on the left, profile on the right.\n"
            + "The first line that matches wins. Order of choosing a profile: permission antibadwords.profile.<name>, LuckPerms\n"
            + "meta or group, then THIS map, then the world rules, then the default.\n"
            + "Example:\n"
            + "  channel-profiles:\n"
            + "    global: strict\n"
            + "    staff: relaxed\n"
            + "A profile that does not exist in worlds.yml is reported at start and ignored.",
        "{}\n", false);
    public static final Section CHANNEL_COMMANDS = new Section(C + "channel-commands", FILE, CATEGORY, "Channel by command",
        "Which chat channel a command belongs to: command name without the slash on the left, channel name on the right.\n"
            + "Use it for plugins where a player talks in a channel with a command (/g hello). A command listed here is judged with\n"
            + "that channel's profile above. The command must also be in filters.scan.scanned-commands to be scanned at all.\n"
            + "Example:\n"
            + "  channel-commands:\n"
            + "    g: global\n"
            + "    sc: staff",
        "{}\n", false);

    private ChatChannels() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    private static Setting<Boolean> plugin(String key, String name) {
        return s(Setting.bool(C + key, true).title(name + " detection")
            .description(BUKKIT + "Look for " + name + " and log it at start (and use the early chat priority). false = ignore it."));
    }

    public static void register(SettingsRegistry r) {
        List.of(ENABLED, PRIORITY, CHATCONTROL, VENTURECHAT, HUSKCHAT, CARBONCHAT).forEach(r::register);
        r.register(CHANNEL_PROFILES);
        r.register(CHANNEL_COMMANDS);
    }

    /** The detection switch of one plugin from {@link #PLUGIN_NAMES}. */
    public static Setting<Boolean> switchOf(String pluginName) {
        return switch (pluginName) {
            case "ChatControl" -> CHATCONTROL;
            case "VentureChat" -> VENTURECHAT;
            case "HuskChat" -> HUSKCHAT;
            default -> CARBONCHAT;
        };
    }

    /**
     * The profile for a channel, or {@code null} when no line matches or the profile does not exist.
     *
     * @param channelProfiles channel pattern -> profile name, in file order
     * @param profileNames    the profiles that exist (lower case)
     */
    public static String profileFor(Map<String, Object> channelProfiles, String channel, Set<String> profileNames) {
        if (channel == null || channel.isBlank() || channelProfiles.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> e : channelProfiles.entrySet()) {
            String profile = String.valueOf(e.getValue()).trim().toLowerCase(Locale.ROOT);
            if (profileNames.contains(profile) && glob(e.getKey().trim(), channel.trim())) {
                return profile;
            }
        }
        return null;
    }

    /** The channel a command line belongs to ({@code "/g hi"} or {@code "g hi"}), or an empty string. */
    public static String channelOfCommand(Map<String, Object> channelCommands, String commandLine) {
        if (commandLine == null || channelCommands.isEmpty()) {
            return "";
        }
        String line = commandLine.strip();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        int space = line.indexOf(' ');
        String name = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':'); // plugin:command
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        for (Map.Entry<String, Object> e : channelCommands.entrySet()) {
            if (e.getKey().trim().toLowerCase(Locale.ROOT).equals(name)) {
                return String.valueOf(e.getValue()).trim();
            }
        }
        return "";
    }

    /** Reports channel profile lines that point to a profile that does not exist. */
    public static void validate(Map<String, Object> channelProfiles, Set<String> profileNames, Consumer<String> warn) {
        for (Map.Entry<String, Object> e : channelProfiles.entrySet()) {
            String profile = String.valueOf(e.getValue()).trim().toLowerCase(Locale.ROOT);
            if (!profileNames.contains(profile)) {
                warn.accept(CHANNEL_PROFILES.key() + "." + e.getKey() + ": profile '" + e.getValue()
                    + "' does not exist in worlds.yml; this line is ignored.");
            }
        }
    }

    private static boolean glob(String pattern, String text) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            regex.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).matches();
    }
}
