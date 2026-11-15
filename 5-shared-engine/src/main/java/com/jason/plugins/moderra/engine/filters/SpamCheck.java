package com.jason.plugins.moderra.engine.filters;

import com.jason.plugins.moderra.engine.EffectiveSettings;
import com.jason.plugins.moderra.engine.Hit;
import com.jason.plugins.moderra.engine.Message;
import com.jason.plugins.moderra.engine.TextCheck;
import com.jason.plugins.moderra.mod.Names;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The spam and flood rules. One instance per rule, so each has its own id ({@code spam-rate}, {@code spam-repeat},
 * {@code spam-caps}, {@code spam-flood}, {@code spam-words}, {@code spam-commands}), position in the check order and
 * settings. The rules that count over time share one {@link SpamState}.
 */
public final class SpamCheck implements TextCheck {
    public enum Kind {
        RATE(SpamSettings.RATE), REPEAT(SpamSettings.REPEAT), CAPS(SpamSettings.CAPS), FLOOD(SpamSettings.FLOOD),
        WORDS(SpamSettings.WORDS), COMMANDS(SpamSettings.COMMANDS);

        private final SpamSettings.Rule rule;

        Kind(SpamSettings.Rule rule) {
            this.rule = rule;
        }
    }

    /** Prefix of all spam check ids; {@code spam} in {@code filters.check-order} means all of them. */
    public static final String GROUP = "spam";

    private final Kind kind;
    private final SpamState state;
    private final LongSupplier clock;
    private final String id;

    public SpamCheck(Kind kind, SpamState state, LongSupplier clock) {
        this.kind = kind;
        this.state = state;
        this.clock = clock;
        this.id = GROUP + "-" + kind.rule.name();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean enabled(EffectiveSettings settings) {
        return settings.get(SpamSettings.ENABLED) && settings.get(kind.rule.enabled());
    }

    @Override
    public boolean seesEveryCommand() {
        return kind == Kind.COMMANDS;
    }

    @Override
    public String feedback(Hit hit, EffectiveSettings settings) {
        return Names.colour(settings.get(kind.rule.message()));
    }

    @Override
    public Optional<Hit> check(Message message, EffectiveSettings s) {
        if (message.has(SpamSettings.PERMISSION_BYPASS)) {
            return Optional.empty();
        }
        if (kind == Kind.COMMANDS) {
            if (!Message.COMMAND.equals(message.source())) {
                return Optional.empty();
            }
        } else if (!FilterAction.covers(s.get(kind.rule.sources()), message)) {
            return Optional.empty();
        }
        String text = message.text();
        return switch (kind) {
            case RATE -> rate(message, s);
            case REPEAT -> repeat(message, s, text);
            case COMMANDS -> commands(message, s, text);
            case CAPS -> caps(s, text);
            case FLOOD -> flood(s, text);
            case WORDS -> words(s, text);
        };
    }

    private Optional<Hit> hit(EffectiveSettings s, String detail, String cleaned) {
        return Optional.of(FilterAction.hit(id, detail, s.get(kind.rule.action()), s.get(kind.rule.weight()), cleaned));
    }

    private boolean counts(Message message, EffectiveSettings s) {
        if (message.probe() || message.playerId() == null) {
            return false; // a test, or no player to count for
        }
        state.limits(s.get(SpamSettings.MAX_TRACKED), s.get(SpamSettings.FORGET_AFTER).toMillis());
        return true;
    }

    private Optional<Hit> rate(Message message, EffectiveSettings s) {
        if (!counts(message, s)) {
            return Optional.empty();
        }
        int max = s.get(SpamSettings.RATE_MAX);
        int count = state.hit(message.playerId(), SpamState.Counter.MESSAGES, clock.getAsLong(), s.get(SpamSettings.RATE_WINDOW).toMillis(), max + 1);
        return count > max ? hit(s, "too many messages", null) : Optional.empty();
    }

    private Optional<Hit> commands(Message message, EffectiveSettings s, String commandLine) {
        if (!counts(message, s)) {
            return Optional.empty();
        }
        String first = commandLine.trim();
        int space = first.indexOf(' ');
        String label = Names.commandLabel(space < 0 ? first : first.substring(0, space));
        for (String exempt : s.get(SpamSettings.COMMANDS_EXEMPT)) {
            if (Names.commandLabel(exempt.trim()).equals(label)) {
                return Optional.empty();
            }
        }
        int max = s.get(SpamSettings.COMMANDS_MAX);
        int count = state.hit(message.playerId(), SpamState.Counter.COMMANDS, clock.getAsLong(), s.get(SpamSettings.COMMANDS_WINDOW).toMillis(), max + 1);
        return count > max ? hit(s, "too many commands", null) : Optional.empty();
    }

    private Optional<Hit> repeat(Message message, EffectiveSettings s, String text) {
        if (!counts(message, s)) {
            return Optional.empty();
        }
        String canonical = Similarity.canonical(text);
        if (canonical.length() < s.get(SpamSettings.REPEAT_MIN_LENGTH)) {
            return Optional.empty();
        }
        int same = state.repeats(message.playerId(), canonical, clock.getAsLong(), s.get(SpamSettings.REPEAT_WINDOW).toMillis(),
            s.get(SpamSettings.REPEAT_SIMILARITY), s.get(SpamSettings.REPEAT_HISTORY));
        return same >= s.get(SpamSettings.REPEAT_MAX) ? hit(s, "repeated message", null) : Optional.empty();
    }

    private Optional<Hit> caps(EffectiveSettings s, String text) {
        int upper = 0;
        int cased = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isUpperCase(cp)) {
                upper++;
                cased++;
            } else if (Character.isLowerCase(cp)) {
                cased++;
            }
        }
        if (cased < s.get(SpamSettings.CAPS_MIN_LENGTH) || upper * 100L <= (long) s.get(SpamSettings.CAPS_MAX) * cased) {
            return Optional.empty();
        }
        return hit(s, "too many capital letters", text.toLowerCase(Locale.ROOT));
    }

    private Optional<Hit> flood(EffectiveSettings s, String text) {
        int maxRun = s.get(SpamSettings.FLOOD_MAX_RUN);
        int keep = Math.min(s.get(SpamSettings.FLOOD_COLLAPSE), maxRun);
        int[] cps = text.codePoints().toArray();
        StringBuilder collapsed = new StringBuilder(text.length());
        boolean longRun = false;
        for (int i = 0; i < cps.length; ) {
            int j = i;
            while (j < cps.length && cps[j] == cps[i]) {
                j++;
            }
            int run = j - i;
            boolean counted = !Character.isDigit(cps[i]) && !Character.isWhitespace(cps[i]);
            int emit = run;
            if (counted && run > maxRun) {
                longRun = true;
                emit = keep;
            }
            for (int n = 0; n < emit; n++) {
                collapsed.appendCodePoint(cps[i]);
            }
            i = j;
        }
        String cleaned = collapsed.toString();
        boolean symbols = tooManySymbols(s, text);
        if (!longRun && !symbols) {
            return Optional.empty();
        }
        // a cleaned text that is still mostly symbols is not worth delivering: null makes "censor" block
        return hit(s, longRun ? "character flood" : "symbol flood", tooManySymbols(s, cleaned) ? null : cleaned);
    }

    private static boolean tooManySymbols(EffectiveSettings s, String text) {
        int max = s.get(SpamSettings.FLOOD_MAX_SYMBOLS);
        int total = 0;
        int symbols = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || EvasionNormalizer.isInvisible(cp) || EvasionNormalizer.isMark(cp)) {
                continue;
            }
            total++;
            if (!Character.isLetterOrDigit(cp)) {
                symbols++;
            }
        }
        return max < 100 && total >= s.get(SpamSettings.FLOOD_SYMBOL_MIN_LENGTH) && symbols * 100L > (long) max * total;
    }

    private Optional<Hit> words(EffectiveSettings s, String text) {
        int max = s.get(SpamSettings.WORDS_MAX);
        int minLength = s.get(SpamSettings.WORDS_MIN_LENGTH);
        String[] parts = text.trim().split("\\s+");
        Map<String, Integer> seen = new HashMap<>();
        List<String> kept = new ArrayList<>(parts.length);
        String worst = null;
        for (String part : parts) {
            String word = bare(part);
            if (word.codePointCount(0, word.length()) < minLength) {
                kept.add(part);
                continue;
            }
            int count = seen.merge(word, 1, Integer::sum);
            if (count > max) {
                if (worst == null) {
                    worst = word;
                }
            } else {
                kept.add(part);
            }
        }
        return worst == null ? Optional.empty() : hit(s, "repeated word '" + worst + "'", String.join(" ", kept));
    }

    /** The word without punctuation around it, lower-case ("Buy!!" -> "buy"). */
    private static String bare(String part) {
        int start = 0;
        int end = part.length();
        while (start < end && !Character.isLetterOrDigit(part.charAt(start)) && !Character.isSurrogate(part.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(part.charAt(end - 1)) && !Character.isSurrogate(part.charAt(end - 1))) {
            end--;
        }
        return part.substring(start, end).toLowerCase(Locale.ROOT);
    }
}
