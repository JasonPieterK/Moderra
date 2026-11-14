package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;

/** Settings of the punishment ladder ({@code moderation/ladder.yml}, keys under {@code ladder.}). */
public final class LadderSettings {
    public static final String FILE = CoreSettings.LADDER;
    public static final String ALL = "all";
    public static final String HIGHEST = "highest";
    private static final String CATEGORY = "Ladder";

    public static final Section STEPS = new Section("ladder.steps", FILE, CATEGORY, "Ladder steps",
        "The punishment ladder: what happens at how many strikes. Each step has a name you choose.\n"
            + "While this is empty ({}), the simple setup above is used: at punishment.violations-before-punish strikes every\n"
            + "enabled action under punishment.effects / attributes / sounds / commands runs and the strikes start again at 0.\n"
            + "As soon as there is at least one step, the steps decide instead and strikes are NOT set back to 0 by themselves\n"
            + "(they fade with strikes.decay / punishment.violations-expire-minutes, or use the action reset-strikes).\n"
            + "A step has:\n"
            + "  at:        the number of strikes at which it happens\n"
            + "  repeat:    once (only at that number) or a number N (again every N strikes after it)\n"
            + "  message:   sent to the player (optional). broadcast: sent to everyone (optional). Colours: & codes.\n"
            + "  final-command: a console command run after the messages (optional, placeholders as below)\n"
            + "  actions:   a list, done from top to bottom:\n"
            + "    warn [text]                      nothing but the message (or one more line of text)\n"
            + "    mute <time|perm> [reason]        built-in mute, time like 30s, 10m, 1h30m, 2d\n"
            + "    kick [reason]\n"
            + "    ban [reason]                     runs ladder.ban-command (the normal Minecraft ban, no end date)\n"
            + "    tempban <time> [reason]          timed ban through the ban plugin found on the server (integrations.yml, integrations.bans.*)\n"
            + "    ipban [reason]                   IP ban through the same ban plugin\n"
            + "    command <console command>        for example a ban plugin: command tempban {player} 1d {reason}\n"
            + "    effect <name> <level> <seconds>  attribute <name> <value> <seconds>  sound <name> <volume> <pitch>\n"
            + "    punishments                      every enabled action under punishment.effects / attributes / sounds / commands\n"
            + "    reset-strikes                    the player starts again at 0 strikes\n"
            + "Placeholders in messages and commands: {player} {uuid} {word} {source} {count} {step} {threshold} {reason}\n"
            + "{duration}; in messages also {punishments} {limit} {remaining}.\n"
            + "The simple setup above is the same as this one step (what you get when you leave steps empty):\n"
            + "  steps:\n"
            + "    legacy:\n"
            + "      at: <violations-before-punish>\n"
            + "      repeat: 1\n"
            + "      message: <punishment-player>\n"
            + "      broadcast: <punishment-broadcast-format>\n"
            + "      final-command: <punishment.final-command.command, when enabled>\n"
            + "      actions: [punishments, reset-strikes]\n"
            + "Example:\n"
            + "  steps:\n"
            + "    first-warning:\n"
            + "      at: 2\n"
            + "      repeat: once\n"
            + "      message: \"&eThis is a warning, {player}. Next time you are muted.\"\n"
            + "      actions: [warn]\n"
            + "    short-mute:\n"
            + "      at: 4\n"
            + "      repeat: once\n"
            + "      message: \"&cYou are muted for {duration}.\"\n"
            + "      actions:\n"
            + "        - mute 10m Bad language\n"
            + "        - sound minecraft:entity.villager.no 1 1\n"
            + "    kick:\n"
            + "      at: 6\n"
            + "      repeat: 2\n"
            + "      actions: [\"kick Watch your language\"]\n"
            + "A profile in worlds.yml can have its own ladder (key ladder.steps).\n"
            + "This part cannot be edited in the in-game menu; edit the file.",
        "{}", true);

    public static final Setting<String> SEVERAL_STEPS = Setting.choice("ladder.when-several-steps-are-reached", ALL, ALL, HIGHEST)
        .file(FILE).category(CATEGORY).title("Several steps at once").overridable()
        .description("One violation can be worth several strikes and so reach more than one step at the same moment.\n"
            + "all = every reached step happens, lowest first. highest = only the highest one.").build();
    public static final Setting<String> DEFAULT_REASON = Setting.string("ladder.default-reason", "Bad language")
        .file(FILE).category(CATEGORY).title("Default reason").overridable()
        .description("The reason used by mute, kick and ban actions that do not give one. Colours: & codes.").build();
    public static final Setting<String> BAN_COMMAND = Setting.string("ladder.ban-command", "ban {player} {reason}")
        .file(FILE).category(CATEGORY).title("Ban command").overridable()
        .description("The console command the ban action runs. Minecraft itself cannot ban for a limited time; for that use\n"
            + "the command action with the command of a ban plugin (example: command tempban {player} 1d {reason}).").build();

    private LadderSettings() {
    }

    public static void register(SettingsRegistry r) {
        r.register(STEPS);
        r.register(SEVERAL_STEPS);
        r.register(DEFAULT_REASON);
        r.register(BAN_COMMAND);
    }
}
