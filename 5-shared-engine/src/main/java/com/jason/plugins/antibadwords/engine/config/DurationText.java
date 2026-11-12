package com.jason.plugins.antibadwords.engine.config;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes durations the way admins type them: {@code 45s}, {@code 10m}, {@code 2h}, {@code 1d12h}, {@code 90} (seconds). */
public final class DurationText {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(ms|[smhdw])");

    private DurationText() {
    }

    /** @throws IllegalArgumentException when the text is not a duration */
    public static Duration parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty duration");
        }
        if (text.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(text));
        }
        Matcher m = PART.matcher(text);
        long millis = 0;
        int end = 0;
        while (m.find()) {
            if (!text.substring(end, m.start()).isBlank()) {
                throw new IllegalArgumentException("not a duration: " + raw);
            }
            long n = Long.parseLong(m.group(1));
            millis += switch (m.group(2)) {
                case "ms" -> n;
                case "s" -> n * 1000L;
                case "m" -> n * 60_000L;
                case "h" -> n * 3_600_000L;
                case "d" -> n * 86_400_000L;
                default -> n * 604_800_000L;
            };
            end = m.end();
        }
        if (end == 0 || !text.substring(end).isBlank()) {
            throw new IllegalArgumentException("not a duration: " + raw);
        }
        return Duration.ofMillis(millis);
    }

    /** {@code PT1H30M} -> {@code 1h30m}; zero -> {@code 0s}. */
    public static String format(Duration d) {
        long millis = d.toMillis();
        if (millis <= 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        long[] sizes = {86_400_000L, 3_600_000L, 60_000L, 1000L, 1L};
        String[] units = {"d", "h", "m", "s", "ms"};
        for (int i = 0; i < sizes.length; i++) {
            long n = millis / sizes[i];
            if (n > 0) {
                out.append(n).append(units[i]);
                millis -= n * sizes[i];
            }
        }
        return out.toString();
    }
}
