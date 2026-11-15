package com.jason.plugins.moderra.engine.moderation;

import com.jason.plugins.moderra.engine.config.YamlTree;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The punishment ladder of one profile: the steps from {@code ladder.steps} in {@code moderation/ladder.yml}, sorted by
 * their threshold. Immutable. This class only reads the steps and answers "which steps does this change of strikes
 * reach?"; the engine's violation flow carries the actions out.
 *
 * <p>A step happens when the strikes go <em>up past</em> one of its trigger points: its threshold, and for a repeating
 * step also threshold + N, threshold + 2N ... A step that is passed several times by one change happens once. When
 * strikes fade and rise again, the step is reached again.
 */
public final class Ladder {
    public static final String WARN = "warn";
    public static final String MUTE = "mute";
    public static final String KICK = "kick";
    public static final String BAN = "ban";
    public static final String TEMPBAN = "tempban";
    public static final String IPBAN = "ipban";
    public static final String COMMAND = "command";
    public static final String EFFECT = "effect";
    public static final String ATTRIBUTE = "attribute";
    public static final String SOUND = "sound";
    public static final String PUNISHMENTS = "punishments";
    public static final String RESET = "reset-strikes";

    private static final Ladder EMPTY = new Ladder(List.of());

    /**
     * One action of a step.
     *
     * @param type one of the constants of {@link Ladder}
     * @param args warn: [text]; mute: [duration in ms or -1, reason]; kick, ban: [reason]; command: [command line];
     *             effect, attribute, sound: the three values; punishments, reset-strikes: nothing. A missing text or
     *             reason is ""
     * @param text the action as the admin wrote it
     */
    public record Action(String type, List<String> args, String text) {
    }

    /**
     * @param name        the admin's name for the step
     * @param threshold   strikes at which it happens
     * @param repeatEvery again every this many strikes after the threshold; 0 = once
     * @param message     for the player, not coloured yet; may be empty
     * @param broadcast   for everyone, not coloured yet; may be empty
     * @param finalCommand a console command run after the messages (before kick/ban); "" = none
     */
    public record Step(String name, int threshold, int repeatEvery, String message, String broadcast, List<Action> actions,
                       String finalCommand) {
        public Step(String name, int threshold, int repeatEvery, String message, String broadcast, List<Action> actions) {
            this(name, threshold, repeatEvery, message, broadcast, actions, "");
        }

        public boolean repeats() {
            return repeatEvery > 0;
        }

        /** How many trigger points of this step are at or below {@code points}. */
        private long triggersUpTo(int points) {
            if (points < threshold) {
                return 0;
            }
            return repeats() ? (points - threshold) / repeatEvery + 1L : 1L;
        }

        /** Whether going from {@code before} to {@code after} strikes passes a trigger point of this step. */
        public boolean reachedBy(int before, int after) {
            return after > before && triggersUpTo(after) > triggersUpTo(before);
        }

        /** The first trigger point above {@code points}, or -1 when there is none. */
        public int nextTrigger(int points) {
            if (points < threshold) {
                return threshold;
            }
            if (!repeats()) {
                return -1;
            }
            long next = threshold + ((long) (points - threshold) / repeatEvery + 1L) * repeatEvery;
            return next > Integer.MAX_VALUE ? -1 : (int) next;
        }
    }

    /** The step a player reaches next, and at how many strikes. */
    public record Next(Step step, int at) {
    }

    private final List<Step> steps;

    private Ladder(List<Step> steps) {
        this.steps = steps;
    }

    public static Ladder empty() {
        return EMPTY;
    }

    public static Ladder of(List<Step> steps) {
        List<Step> sorted = new ArrayList<>(steps);
        sorted.sort(Comparator.comparingInt(Step::threshold)); // stable: equal thresholds keep the file order
        return new Ladder(List.copyOf(sorted));
    }

    /**
     * Reads the raw {@code ladder.steps} section. A step or action that is wrong is left out and reported.
     *
     * @param warn receives one line per problem
     */
    public static Ladder parse(Map<String, Object> raw, Consumer<String> warn) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        List<Step> steps = new ArrayList<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String where = LadderSettings.STEPS.key() + "." + e.getKey();
            if (!(e.getValue() instanceof Map<?, ?> map)) {
                warn.accept(where + " must be a section with at: and actions:; the step is ignored.");
                continue;
            }
            YamlTree step = YamlTree.of(YamlTree.stringKeys(map));
            Integer at = wholeNumber(step.get("at"));
            if (at == null || at < 1) {
                warn.accept(where + ".at must be a whole number of 1 or more; the step is ignored.");
                continue;
            }
            int repeat = 0;
            Object rawRepeat = step.get("repeat");
            if (rawRepeat != null) {
                String text = String.valueOf(rawRepeat).trim().toLowerCase(Locale.ROOT);
                if (text.startsWith("every")) {
                    text = text.substring(5).trim();
                }
                Integer n = text.matches("\\d{1,9}") ? Integer.valueOf(text) : null;
                if (n != null) {
                    repeat = n;
                } else if (!text.equals("once") && !text.isEmpty()) {
                    warn.accept(where + ".repeat must be 'once' or a number of strikes; using once.");
                }
            }
            List<Action> actions = new ArrayList<>();
            Object rawActions = step.get("actions");
            List<?> list = rawActions instanceof List<?> l ? l : rawActions == null ? List.of() : List.of(rawActions);
            for (Object item : list) {
                String text = item == null ? "" : String.valueOf(item).trim();
                try {
                    actions.add(action(text));
                } catch (IllegalArgumentException ex) {
                    warn.accept(where + ".actions: '" + text + "' is ignored: " + ex.getMessage());
                }
            }
            String message = step.getString("message", "");
            String broadcast = step.getString("broadcast", "");
            String finalCommand = step.getString("final-command", "").trim();
            if (actions.isEmpty() && message.isBlank() && broadcast.isBlank() && finalCommand.isBlank()) {
                warn.accept(where + " has no action and no message; the step is ignored.");
                continue;
            }
            steps.add(new Step(e.getKey(), at, repeat, message, broadcast, List.copyOf(actions), finalCommand));
        }
        return steps.isEmpty() ? EMPTY : of(steps);
    }

    private static Integer wholeNumber(Object raw) {
        if (raw instanceof Integer i) {
            return i;
        }
        String text = raw == null ? "" : String.valueOf(raw).trim();
        return text.matches("\\d{1,9}") ? Integer.valueOf(text) : null;
    }

    /**
     * Reads one action line such as {@code mute 10m Bad language}.
     *
     * @throws IllegalArgumentException with a plain-language reason when the line is not an action
     */
    public static Action action(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty action");
        }
        String[] head = text.split("\\s+", 2);
        String type = head[0].toLowerCase(Locale.ROOT);
        String rest = head.length > 1 ? head[1].trim() : "";
        switch (type) {
            case WARN, KICK, BAN, IPBAN:
                return new Action(type, List.of(rest), text);
            case TEMPBAN: {
                String[] parts = rest.split("\\s+", 2);
                if (parts[0].isEmpty()) {
                    throw new IllegalArgumentException("tempban needs a time (1d, 2h30m ...)");
                }
                long millis = DurationParser.parse(parts[0]);
                if (millis <= 0) {
                    throw new IllegalArgumentException("the tempban time must be a real time such as 1d (use ban for a permanent ban)");
                }
                return new Action(TEMPBAN, List.of(Long.toString(millis), parts.length > 1 ? parts[1].trim() : ""), text);
            }
            case MUTE: {
                String[] parts = rest.split("\\s+", 2);
                if (parts[0].isEmpty()) {
                    throw new IllegalArgumentException("mute needs a time (10m, 2d ...) or perm");
                }
                long millis = DurationParser.parse(parts[0]);
                if (millis == 0) {
                    throw new IllegalArgumentException("the mute time must be longer than 0");
                }
                return new Action(MUTE, List.of(Long.toString(millis), parts.length > 1 ? parts[1].trim() : ""), text);
            }
            case COMMAND:
                if (rest.isEmpty()) {
                    throw new IllegalArgumentException("command needs the console command to run");
                }
                return new Action(COMMAND, List.of(rest), text);
            case EFFECT, ATTRIBUTE, SOUND: {
                String[] parts = rest.isEmpty() ? new String[0] : rest.split("\\s+");
                if (parts.length != 3) {
                    throw new IllegalArgumentException(type + " needs three values: "
                        + (type.equals(SOUND) ? "name, volume, pitch" : type.equals(EFFECT) ? "name, level, seconds" : "name, value, seconds"));
                }
                for (int i = 1; i < 3; i++) {
                    try {
                        Double.parseDouble(parts[i]);
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("'" + parts[i] + "' is not a number");
                    }
                }
                return new Action(type, List.of(parts), text);
            }
            case PUNISHMENTS:
                return new Action(PUNISHMENTS, List.of(), text);
            case RESET, "reset":
                return new Action(RESET, List.of(), text);
            default:
                throw new IllegalArgumentException("unknown action '" + head[0] + "' (known: warn, mute, kick, ban, tempban, ipban, command, effect, "
                    + "attribute, sound, punishments, reset-strikes)");
        }
    }

    /** Name of the step {@link #classic} builds. */
    public static final String CLASSIC_STEP = "legacy";

    /**
     * The classic flat setup written as a ladder: one step at {@code violations-before-punish} that repeats whenever the
     * strikes go up again ({@code repeat: 1}, so it also fires for a player already above the limit), runs every enabled
     * classic action, says the two punishment messages, runs the final command and sets the strikes back to 0. This is
     * exactly what an admin would write into {@code ladder.steps} to get the old behaviour; the violation flow itself
     * still runs the flat setup directly (see the note in MINI-PROGRESS.md).
     *
     * @param atStrikes   {@code punishment.violations-before-punish}
     * @param message     {@code punishment-player} (already coloured)
     * @param broadcast   {@code punishment-broadcast-format} (already coloured)
     * @param finalCommand the final command when it is enabled, else ""
     */
    public static Ladder classic(int atStrikes, String message, String broadcast, String finalCommand) {
        return of(List.of(new Step(CLASSIC_STEP, Math.max(1, atStrikes), 1, message, broadcast,
            List.of(new Action(PUNISHMENTS, List.of(), PUNISHMENTS), new Action(RESET, List.of(), RESET)),
            finalCommand == null ? "" : finalCommand)));
    }

    /** No steps: the simple setup ({@code punishment.violations-before-punish}) is used instead. */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** The steps, lowest threshold first. */
    public List<Step> steps() {
        return steps;
    }

    /** The steps reached when the strikes go from {@code before} up to {@code after}, lowest threshold first. */
    public List<Step> reached(int before, int after) {
        List<Step> out = new ArrayList<>();
        for (Step step : steps) {
            if (step.reachedBy(before, after)) {
                out.add(step);
            }
        }
        return out;
    }

    /** The step that happens next for a player with {@code points} strikes (the nearest trigger point above them). */
    public Optional<Next> next(int points) {
        Next best = null;
        for (Step step : steps) {
            int at = step.nextTrigger(points);
            if (at >= 0 && (best == null || at < best.at())) {
                best = new Next(step, at);
            }
        }
        return Optional.ofNullable(best);
    }

    /** The lowest threshold; 0 for an empty ladder. */
    public int firstThreshold() {
        return steps.isEmpty() ? 0 : steps.get(0).threshold();
    }

    /**
     * The number shown as "limit" for a player with {@code points} strikes: the nearest trigger point at or above
     * them, or the highest threshold when nothing more can happen.
     */
    public int limit(int points) {
        if (steps.isEmpty()) {
            return 0;
        }
        return next(Math.max(0, points) - 1).map(Next::at).orElse(steps.get(steps.size() - 1).threshold());
    }
}
