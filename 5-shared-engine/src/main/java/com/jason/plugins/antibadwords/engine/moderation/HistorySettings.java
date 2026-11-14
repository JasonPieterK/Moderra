package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;

/** Settings of the moderation history ({@code moderation/history.yml}, files {@code data/history/YYYY-MM.jsonl}). */
public final class HistorySettings {
    public static final String FILE = "moderation/history.yml";
    private static final String CATEGORY = "History";

    public static final Setting<Boolean> ENABLED = Setting.bool("history.enabled", true)
        .file(FILE).category(CATEGORY).title("Keep a history")
        .description("Write a line for every moderation event (violation, punishment, mute ...) to data/history/YYYY-MM.jsonl,\n"
            + "one file per month. Staff with the permission antibadwords.history read it with /abw history.\n"
            + "The history never contains the chat text unless you switch store-message-text on.").build();
    public static final Setting<List<String>> KINDS = Setting.list("history.kinds", List.of("*"))
        .file(FILE).category(CATEGORY).title("Which events")
        .description("The kinds of event that are written. * = all of them. Kinds: violation, punishment, spam, link, evasion,\n"
            + "mute, unmute, mute-expired, admin-command. Example: [violation, punishment, mute]").build();
    public static final Setting<Boolean> STORE_TEXT = Setting.bool("history.store-message-text", false)
        .file(FILE).category(CATEGORY).title("Store the blocked text")
        .description("Also write the text the player typed. OFF by default for privacy: chat can contain personal things.\n"
            + "Only turn this on when your rules and your players' privacy expectations allow it.").build();
    public static final Setting<Boolean> REDACT_WORDS = Setting.bool("history.redact-words", true)
        .file(FILE).category(CATEGORY).title("Mask the bad word in the stored text")
        .description("Only used when store-message-text is on: the bad word is replaced by stars (same length) in the stored text.\n"
            + "When the word cannot be found in the text (disguised spelling), no text is stored at all.").build();
    public static final Setting<Integer> RETENTION_DAYS = Setting.integer("history.retention-days", 90).range(0, 36_500)
        .file(FILE).category(CATEGORY).title("Keep history for (days)")
        .description("Monthly files that are completely older than this many days are deleted. 0 = keep forever.\n"
            + "The unit is a whole month: a file goes when its last day is older than this.").build();
    public static final Setting<Integer> MAX_TOTAL_MB = Setting.integer("history.max-total-size-mb", 50).range(0, 100_000)
        .file(FILE).category(CATEGORY).title("Size limit (MB)")
        .description("When all history files together are bigger than this, the oldest months are deleted. If only the current\n"
            + "month is left and it is still too big, new events are not written until it is below the limit. 0 = no limit.").build();
    public static final Setting<Integer> PAGE_SIZE = Setting.integer("history.page-size", 8).range(1, 50)
        .file(FILE).category(CATEGORY).title("Lines per page")
        .description("How many lines /abw history shows per page.").build();

    private HistorySettings() {
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "AntiBadWords - history\n"
            + "A log of what happened (who, when, what, how many strikes). Read it with /abw history (permission antibadwords.history).\n"
            + "Reload with /abw reload.");
        for (Setting<?> setting : List.of(ENABLED, KINDS, STORE_TEXT, REDACT_WORDS, RETENTION_DAYS, MAX_TOTAL_MB, PAGE_SIZE)) {
            r.register(setting);
        }
    }
}
