package com.jason.plugins.moderra.engine.moderation;

import com.jason.plugins.moderra.engine.config.DurationText;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Durations the way staff type them in commands and ladder actions: {@code 30s}, {@code 10m}, {@code 1h30m}, {@code 2d},
 * {@code 1w} or {@code perm}. A thin, stricter wrapper around {@link DurationText} (which reads durations in the config
 * files): a unit is required (a bare {@code 90} is not a duration here, so it cannot be mixed up with the first word of
 * a reason), and "forever" is a value of its own.
 */
public final class DurationParser {
    /** The result for {@code perm}: no end. */
    public static final long PERMANENT = -1L;
    private static final Set<String> PERMANENT_WORDS = Set.of("perm", "permanent", "forever");
    private static final Pattern SHAPE = Pattern.compile("(?:\\d{1,9}\\s*(?:ms|[smhdw])\\s*)+");

    private DurationParser() {
    }

    /**
     * @return the length in milliseconds, or {@link #PERMANENT}
     * @throws IllegalArgumentException when the text is not a duration
     */
    public static long parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (PERMANENT_WORDS.contains(text)) {
            return PERMANENT;
        }
        if (!SHAPE.matcher(text).matches()) {
            throw new IllegalArgumentException("not a duration: '" + (raw == null ? "" : raw.trim())
                + "' (examples: 30s, 10m, 1h30m, 2d, perm)");
        }
        return DurationText.parse(text).toMillis();
    }

    /** As {@link #parse(String)}, but empty instead of an exception. */
    public static OptionalLong tryParse(String raw) {
        try {
            return OptionalLong.of(parse(raw));
        } catch (IllegalArgumentException ex) {
            return OptionalLong.empty();
        }
    }

    public static boolean isPermanent(long millis) {
        return millis < 0;
    }

    /** {@code 5400000} -> {@code 1h30m}; {@link #PERMANENT} -> {@code permanent}. */
    public static String format(long millis) {
        return isPermanent(millis) ? "permanent" : DurationText.format(Duration.ofMillis(millis));
    }
}
