package com.jason.plugins.antibadwords.engine.filters;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Settings of feature "anti-spam and flood control" ({@code filters/spam.yml}). */
public final class SpamSettings {
    public static final String FILE = "filters/spam.yml";
    public static final String PERMISSION_BYPASS = "antibadwords.bypass.spam";
    private static final String CATEGORY = "Spam";
    private static final String P = "filters.spam.";
    private static final List<Setting<?>> ALL = new ArrayList<>();

    /** The five settings every spam rule has. */
    public record Rule(String name, Setting<Boolean> enabled, Setting<String> action, Setting<String> message, Setting<Integer> weight,
                       Setting<List<String>> sources) {
    }

    public static final Setting<Boolean> ENABLED = add(Setting.bool(P + "enabled", false).title("Spam filter on")
        .description("Master switch for everything in this file. Off by default: switch it on, then switch off the rules you do not want."));
    public static final Setting<Integer> MAX_TRACKED = add(Setting.integer(P + "max-tracked-players", 5000).range(10, 1_000_000)
        .title("Players remembered at most")
        .description("For how many players message times are remembered at the same time (memory limit). When more players are\n"
            + "active, the ones who were quiet the longest are forgotten first."), false);
    public static final Setting<Duration> FORGET_AFTER = add(Setting.duration(P + "forget-after", Duration.ofMinutes(10)).range(10, 86_400)
        .title("Forget quiet players after")
        .description("A player who sent nothing for this long is forgotten (players are also forgotten when they leave)."), false);

    // ---- too many messages ----
    public static final Rule RATE = rule("rate", "Too many messages", "A player sends messages faster than allowed.",
        "&cYou are sending messages too fast. Please slow down.", FilterAction.BLOCK, false, List.of("chat"));
    public static final Setting<Integer> RATE_MAX = add(Setting.integer(P + "rate.max-messages", 5).range(1, 1000).title("Messages allowed per window")
        .description("How many messages a player may send within the time window. The next one is caught."));
    public static final Setting<Duration> RATE_WINDOW = add(Setting.duration(P + "rate.window", Duration.ofSeconds(5)).range(1, 3600)
        .title("Time window").description("Length of the time window for the line above."));

    // ---- repeated messages ----
    public static final Rule REPEAT = rule("repeat", "Repeated messages", "A player sends the same (or nearly the same) message again and again.",
        "&cPlease do not repeat the same message.", FilterAction.BLOCK, false, List.of("chat"));
    public static final Setting<Integer> REPEAT_MAX = add(Setting.integer(P + "repeat.max-repeats", 2).range(1, 100).title("Same message allowed")
        .description("How many times the same message may be sent within the time window. The next one is caught."));
    public static final Setting<Duration> REPEAT_WINDOW = add(Setting.duration(P + "repeat.window", Duration.ofSeconds(30)).range(1, 86_400)
        .title("Time window").description("How long a message is remembered for this comparison."));
    public static final Setting<Double> REPEAT_SIMILARITY = add(Setting.decimal(P + "repeat.similarity", 0.85).range(0.5, 1.0)
        .title("How similar counts as the same")
        .description("1.0 = only exactly the same text counts. 0.85 = messages that differ in up to 15% of their characters also count\n"
            + "(\"hello!!\" and \"hello!!!\"). Upper and lower case and extra spaces never matter."));
    public static final Setting<Integer> REPEAT_MIN_LENGTH = add(Setting.integer(P + "repeat.min-length", 4).range(1, 256)
        .title("Shortest message checked").description("Shorter messages (\"ok\", \"gg\") are never counted as repeats."));
    public static final Setting<Integer> REPEAT_HISTORY = add(Setting.integer(P + "repeat.history-size", 5).range(1, 50)
        .title("Messages remembered per player").description("How many of a player's last messages are kept for the comparison."));

    // ---- capital letters ----
    public static final Rule CAPS = rule("caps", "Too many capital letters", "A message is written mostly IN CAPITAL LETTERS.",
        "&cPlease do not write in capital letters.", FilterAction.CENSOR, true, List.of("chat"));
    public static final Setting<Integer> CAPS_MAX = add(Setting.integer(P + "caps.max-percent", 70).range(1, 100).title("Capital letters allowed (%)")
        .description("A message is caught when more than this share of its letters are capitals."));
    public static final Setting<Integer> CAPS_MIN_LENGTH = add(Setting.integer(P + "caps.min-length", 8).range(1, 256)
        .title("Shortest message checked").description("Messages with fewer letters than this are never caught (\"LOL\", \"GG\")."));

    // ---- character flood ----
    public static final Rule FLOOD = rule("flood", "Character flood", "The same character many times in a row (\"heeeeeeello\", \"!!!!!!!!\"), "
        + "or a message that is mostly symbols or emoji.", "&cPlease do not flood the chat with repeated characters.", FilterAction.CENSOR, true,
        List.of("chat"));
    public static final Setting<Integer> FLOOD_MAX_RUN = add(Setting.integer(P + "flood.max-repeated-characters", 6).range(2, 256)
        .title("Same character in a row allowed")
        .description("A message is caught when one character (not a digit) is repeated more often than this in a row."));
    public static final Setting<Integer> FLOOD_COLLAPSE = add(Setting.integer(P + "flood.collapse-to", 3).range(1, 256)
        .title("Censor: shorten runs to").description("With action censor, a too long run is shortened to this many characters."));
    public static final Setting<Integer> FLOOD_MAX_SYMBOLS = add(Setting.integer(P + "flood.max-symbol-percent", 70).range(1, 100)
        .title("Symbols and emoji allowed (%)")
        .description("A message is caught when more than this share of its characters (spaces not counted) are symbols, punctuation\n"
            + "or emoji. 100 = never. With action censor such a message is blocked, because there is nothing sensible to keep."));
    public static final Setting<Integer> FLOOD_SYMBOL_MIN_LENGTH = add(Setting.integer(P + "flood.symbol-min-length", 10).range(1, 256)
        .title("Shortest message checked for symbols").description("Shorter messages (\":)\", \"???\") are never caught by the symbol rule."));

    // ---- repeated words ----
    public static final Rule WORDS = rule("words", "Repeated words", "The same word many times in one message (\"buy buy buy buy buy\").",
        "&cPlease do not repeat the same word so often.", FilterAction.BLOCK, true, List.of("chat"));
    public static final Setting<Integer> WORDS_MAX = add(Setting.integer(P + "words.max-identical-words", 4).range(1, 256)
        .title("Same word allowed per message")
        .description("A message is caught when one word appears more often than this. With action censor the extra copies are removed."));
    public static final Setting<Integer> WORDS_MIN_LENGTH = add(Setting.integer(P + "words.min-word-length", 2).range(1, 64)
        .title("Shortest word counted").description("Shorter words (\"a\", \"I\") are not counted."));

    // ---- command spam ----
    public static final Rule COMMANDS = rule("commands", "Too many commands", "A player runs commands faster than allowed. Every command "
        + "counts, also the ones that are not scanned for bad words.", "&cYou are using commands too fast. Please slow down.",
        FilterAction.BLOCK, false, null);
    public static final Setting<Integer> COMMANDS_MAX = add(Setting.integer(P + "commands.max-commands", 8).range(1, 1000)
        .title("Commands allowed per window").description("How many commands a player may run within the time window. The next one is caught."));
    public static final Setting<Duration> COMMANDS_WINDOW = add(Setting.duration(P + "commands.window", Duration.ofSeconds(5)).range(1, 3600)
        .title("Time window").description("Length of the time window for the line above."));
    public static final Setting<List<String>> COMMANDS_EXEMPT = add(Setting.list(P + "commands.exempt-commands", List.of())
        .title("Commands that never count")
        .description("Command names (without /) that are not counted, for example commands a client mod sends automatically."));

    private SpamSettings() {
    }

    private static <T> Setting<T> add(Setting.Builder<T> builder) {
        return add(builder, true);
    }

    private static <T> Setting<T> add(Setting.Builder<T> builder, boolean overridable) {
        builder.file(FILE).category(CATEGORY);
        if (overridable) {
            builder.overridable();
        }
        Setting<T> setting = builder.build();
        ALL.add(setting);
        return setting;
    }

    private static Rule rule(String name, String title, String what, String message, String defaultAction, boolean canCensor,
                             List<String> defaultSources) {
        String p = P + name + ".";
        Setting<Boolean> enabled = add(Setting.bool(p + "enabled", true).title(title)
            .description(title.toUpperCase(java.util.Locale.ROOT) + ": " + what + "\nOnly used while " + P + "enabled is true."));
        Setting<String> action = add((canCensor
            ? Setting.choice(p + "action", defaultAction, FilterAction.BLOCK, FilterAction.CENSOR, FilterAction.LOG_ONLY)
            : Setting.choice(p + "action", defaultAction, FilterAction.BLOCK, FilterAction.LOG_ONLY))
            .title(title + ": action")
            .description("block = the message is not delivered" + (canCensor ? "; censor = a cleaned-up text is delivered instead (chat on "
                + "Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked)" : "") + "; log-only = it goes through and is only noted."));
        Setting<String> text = add(Setting.string(p + "message", message).title(title + ": message")
            .description("Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes."));
        Setting<Integer> weight = add(Setting.integer(p + "strike-weight", 0).range(0, 100).title(title + ": strikes")
            .description("How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this)."));
        Setting<List<String>> sources = defaultSources == null ? null : add(Setting.list(p + "sources", defaultSources)
            .title(title + ": where")
            .description("Text sources this rule looks at: chat, command, sign, book, anvil (or \"*\" for all)."));
        return new Rule(name, enabled, action, text, weight, sources);
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "AntiBadWords - spam and flood control\n"
            + "Everything here is OFF until you set filters.spam.enabled to true. Each rule has its own switch, limits, action,\n"
            + "message and strike weight. Players with the permission antibadwords.bypass.spam are not checked.\n"
            + "Reload with /abw reload.");
        ALL.forEach(r::register);
    }
}
