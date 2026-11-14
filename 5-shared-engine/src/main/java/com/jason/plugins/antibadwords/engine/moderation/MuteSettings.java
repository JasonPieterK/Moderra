package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;
import java.util.List;

/** Settings of the built-in mute ({@code moderation/mute.yml}). */
public final class MuteSettings {
    public static final String FILE = "moderation/mute.yml";
    public static final String DATA_FILE = "mutes.json";
    private static final String CATEGORY = "Mute";
    private static final String PLACEHOLDERS = "Placeholders: {player} {reason} {remaining} {duration} {by}. Colours: & codes.";

    public static final Setting<Boolean> ENABLED = Setting.bool("mute.enabled", true).file(FILE).category(CATEGORY)
        .title("Built-in mute on")
        .description("Switches the built-in mute on. When off, /abw mute and the ladder action mute do nothing and nobody is held back.\n"
            + "Players who are already muted stay in data/mutes.json and are muted again when you switch this back on.").build();
    public static final Setting<Duration> DEFAULT_DURATION = Setting.duration("mute.default-duration", Duration.ofHours(1)).range(1, 31_536_000)
        .file(FILE).category(CATEGORY).title("Default mute time")
        .description("How long /abw mute <player> lasts when no time is given. (Use the word perm for a permanent mute.)").build();
    public static final Setting<Boolean> BLOCK_CHAT = Setting.bool("mute.block.chat", true).file(FILE).category(CATEGORY)
        .title("Block chat")
        .description("A muted player's chat messages are stopped.").build();
    public static final Setting<List<String>> BLOCKED_COMMANDS = Setting.list("mute.block.commands",
            List.of("msg", "tell", "w", "whisper", "r", "reply", "t", "pm", "m", "say", "me", "teammsg", "tm"))
        .file(FILE).category(CATEGORY).title("Blocked commands")
        .description("Commands a muted player may not use (without the slash, upper/lower case does not matter, a prefix such as minecraft: is ignored).\n"
            + "A * is a wildcard: \"*\" = every command, \"msg*\" = everything that starts with msg. [] = none.").build();
    public static final Setting<Boolean> BLOCK_SIGNS = Setting.bool("mute.block.signs", false).file(FILE).category(CATEGORY)
        .title("Block signs")
        .description("A muted player cannot write text on signs.").build();
    public static final Setting<Boolean> BLOCK_BOOKS = Setting.bool("mute.block.books", false).file(FILE).category(CATEGORY)
        .title("Block books")
        .description("A muted player cannot sign or edit books.").build();
    public static final Setting<Duration> CHECK_EVERY = Setting.duration("mute.check-every", Duration.ofSeconds(30)).range(1, 3600)
        .file(FILE).category(CATEGORY).title("Expiry check interval")
        .description("How often the server looks for mutes that have run out (to tell the player and publish the mute-expired event).\n"
            + "A muted player is never held back longer than the mute lasts, whatever this is set to.").build();
    public static final Setting<Boolean> NOTIFY_ON_JOIN = Setting.bool("mute.notify-on-join", true).file(FILE).category(CATEGORY)
        .title("Tell muted players when they join")
        .description("A player who is still muted gets the message mute.messages.join when they join.").build();
    public static final Setting<String> NO_REASON = Setting.string("mute.no-reason-text", "no reason given").file(FILE).category(CATEGORY)
        .title("Text for a missing reason")
        .description("Used for {reason} when a mute has no reason.").build();

    public static final Setting<String> MSG_BLOCKED = Setting.string("mute.messages.blocked",
            "&cYou are muted. Time left: {remaining}. Reason: {reason}").file(FILE).category(CATEGORY).title("Message: blocked")
        .description("Sent when a muted player tries to chat or use a blocked command. " + PLACEHOLDERS).build();
    public static final Setting<String> MSG_JOIN = Setting.string("mute.messages.join",
            "&cYou are still muted. Time left: {remaining}. Reason: {reason}").file(FILE).category(CATEGORY).title("Message: on join")
        .description("Sent when a muted player joins. " + PLACEHOLDERS).build();
    public static final Setting<String> MSG_MUTED = Setting.string("mute.messages.muted",
            "&cYou have been muted for {remaining}. Reason: {reason}").file(FILE).category(CATEGORY).title("Message: muted")
        .description("Sent to the player (when online) at the moment they are muted. " + PLACEHOLDERS).build();
    public static final Setting<String> MSG_UNMUTED = Setting.string("mute.messages.unmuted",
            "&aYou are no longer muted.").file(FILE).category(CATEGORY).title("Message: unmuted")
        .description("Sent to the player (when online) when a staff member lifts the mute. " + PLACEHOLDERS).build();
    public static final Setting<String> MSG_EXPIRED = Setting.string("mute.messages.expired",
            "&aYour mute has run out. You can chat again.").file(FILE).category(CATEGORY).title("Message: expired")
        .description("Sent to the player (when online) when the mute runs out. " + PLACEHOLDERS).build();

    private MuteSettings() {
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "AntiBadWords - built-in mute\n"
            + "Muted players cannot chat or use the listed commands. Commands: /abw mute, /abw unmute, /abw mutes.\n"
            + "Who is muted is stored in data/mutes.json. Reload with /abw reload.");
        for (Setting<?> setting : List.of(ENABLED, DEFAULT_DURATION, BLOCK_CHAT, BLOCKED_COMMANDS, BLOCK_SIGNS, BLOCK_BOOKS, CHECK_EVERY,
            NOTIFY_ON_JOIN, NO_REASON, MSG_BLOCKED, MSG_JOIN, MSG_MUTED, MSG_UNMUTED, MSG_EXPIRED)) {
            r.register(setting);
        }
    }
}
