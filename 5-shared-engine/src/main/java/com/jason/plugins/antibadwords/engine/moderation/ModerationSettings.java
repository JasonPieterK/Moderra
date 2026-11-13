package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;
import java.util.List;

/** Settings of the strike store and the list of known players ({@code moderation/ladder.yml}). */
public final class ModerationSettings {
    public static final String FILE = CoreSettings.LADDER;
    public static final String STRIKES_FILE = "strikes.json";
    public static final String PLAYERS_FILE = "players.json";
    private static final String CATEGORY = "Strikes";

    public static final Setting<Boolean> SAVE_TO_DISK = Setting.bool("strikes.save-to-disk", true)
        .file(FILE).category(CATEGORY).title("Keep strikes across restarts")
        .description("Save every player's strikes (violations) in data/strikes.json so they are still there after a server restart.\n"
            + "This only has an effect when punishment.clear-violations-on-quit is false: while that is true (the default),\n"
            + "a player's strikes are forgotten as soon as they leave, so there is nothing to keep.\n"
            + "false = strikes live in memory only and are gone after a restart (how old versions worked).").build();
    public static final Setting<Duration> DECAY_EVERY = Setting.duration("strikes.decay.every", Duration.ZERO).range(0, 31_536_000)
        .file(FILE).category(CATEGORY).title("Strikes fade: every")
        .description("Strikes can fade away slowly: every time this much time has passed, the player loses some strikes (see\n"
            + "points below). The time counts from the player's first strike and also while the player or the server is offline.\n"
            + "0s = off. Example: 1h = one strike less every hour.\n"
            + "This works next to punishment.violations-expire-minutes, which forgets ALL strikes at once when the last one\n"
            + "is old enough.").build();
    public static final Setting<Integer> DECAY_POINTS = Setting.integer("strikes.decay.points", 1).range(1, 1000)
        .file(FILE).category(CATEGORY).title("Strikes fade: how many")
        .description("How many strikes are taken away each time.").build();
    public static final Setting<Integer> MAX_POINTS = Setting.integer("strikes.max-points", 0).range(0, 1_000_000)
        .file(FILE).category(CATEGORY).title("Highest number of strikes")
        .description("A player never has more strikes than this. 0 = no limit.").build();
    public static final Setting<Duration> SAVE_DELAY = Setting.duration("strikes.save-delay", Duration.ofSeconds(5)).range(0, 3600)
        .file(FILE).category(CATEGORY).title("Save delay")
        .description("After a change the data files (strikes, known players) are written once this much time has passed, so many\n"
            + "changes in a row cause one write. They are always written when the server stops.").build();
    public static final Setting<Integer> FORGET_PLAYERS_DAYS = Setting.integer("known-players.forget-after-days", 0).range(0, 36_500)
        .file(FILE).category(CATEGORY).title("Forget players after (days)")
        .description("The plugin remembers the name of every player who joined (data/players.json), so commands such as\n"
            + "/abw violations also work for players who are offline. Players who were not seen for this many days\n"
            + "are removed from that list. 0 = never.").build();

    private ModerationSettings() {
    }

    public static void register(SettingsRegistry r) {
        for (Setting<?> setting : List.of(SAVE_TO_DISK, DECAY_EVERY, DECAY_POINTS, MAX_POINTS, SAVE_DELAY, FORGET_PLAYERS_DAYS)) {
            r.register(setting);
        }
    }
}
