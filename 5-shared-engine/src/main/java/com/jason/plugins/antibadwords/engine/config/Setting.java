package com.jason.plugins.antibadwords.engine.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One configurable value: where it lives, what type it has, its default and limits, and the texts that describe it.
 * Settings are immutable definitions; register them with {@link SettingsRegistry#register(Setting)} (normally through
 * {@code Setting.bool("a.b", true).file("config.yml")...register(registry)}).
 *
 * <p>Java type per {@link SettingType}: BOOLEAN = Boolean, INT = Integer, DOUBLE = Double, STRING / ENUM / SECRET = String,
 * DURATION = {@link Duration}, STRING_LIST = {@code List<String>}.
 */
public final class Setting<T> implements ConfigEntry {
    /** A value read from a file, plus what was wrong with the raw value ({@code null} = nothing). */
    public record Parsed<T>(T value, String problem) {
    }

    private final String key;
    private final SettingType type;
    private final T defaultValue;
    private final Double min;
    private final Double max;
    private final List<String> allowedValues;
    private final String file;
    private final String category;
    private final String title;
    private final String description;
    private final boolean overridable;

    private Setting(Builder<T> b) {
        this.key = b.key;
        this.type = b.type;
        this.defaultValue = b.defaultValue;
        this.min = b.min;
        this.max = b.max;
        this.allowedValues = List.copyOf(b.allowed);
        this.file = b.file;
        this.category = b.category;
        this.title = b.title == null ? b.key : b.title;
        this.description = b.description == null ? "" : b.description;
        this.overridable = b.overridable;
    }

    // ------------------------------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------------------------------

    public static Builder<Boolean> bool(String key, boolean def) {
        return new Builder<>(key, SettingType.BOOLEAN, def);
    }

    public static Builder<Integer> integer(String key, int def) {
        return new Builder<>(key, SettingType.INT, def);
    }

    public static Builder<Double> decimal(String key, double def) {
        return new Builder<>(key, SettingType.DOUBLE, def);
    }

    public static Builder<String> string(String key, String def) {
        return new Builder<>(key, SettingType.STRING, def);
    }

    /** ENUM: one of {@code allowed} (compared without case). */
    public static Builder<String> choice(String key, String def, String... allowed) {
        Builder<String> b = new Builder<>(key, SettingType.ENUM, def);
        b.allowed.addAll(List.of(allowed));
        return b;
    }

    public static Builder<Duration> duration(String key, Duration def) {
        return new Builder<>(key, SettingType.DURATION, def);
    }

    public static Builder<List<String>> list(String key, List<String> def) {
        return new Builder<>(key, SettingType.STRING_LIST, List.copyOf(def));
    }

    /** SECRET: default is empty; never print the value, use {@link #display(Object)}. */
    public static Builder<String> secret(String key) {
        return new Builder<>(key, SettingType.SECRET, "");
    }

    public static final class Builder<T> {
        private final String key;
        private final SettingType type;
        private final T defaultValue;
        private final List<String> allowed = new ArrayList<>();
        private Double min;
        private Double max;
        private String file = "config.yml";
        private String category = "General";
        private String title;
        private String description;
        private boolean overridable;

        private Builder(String key, SettingType type, T defaultValue) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        /** File relative to the config folder (default {@code config.yml}). */
        public Builder<T> file(String file) {
            this.file = file;
            return this;
        }

        public Builder<T> category(String category) {
            this.category = category;
            return this;
        }

        public Builder<T> title(String title) {
            this.title = title;
            return this;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        /** Lowest allowed value (INT, DOUBLE; DURATION in seconds). */
        public Builder<T> min(double min) {
            this.min = min;
            return this;
        }

        /** Highest allowed value (INT, DOUBLE; DURATION in seconds). */
        public Builder<T> max(double max) {
            this.max = max;
            return this;
        }

        public Builder<T> range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        /** A profile in worlds.yml may override this setting. */
        public Builder<T> overridable() {
            this.overridable = true;
            return this;
        }

        public Setting<T> build() {
            return new Setting<>(this);
        }

        public Setting<T> register(SettingsRegistry registry) {
            return registry.register(build());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Description
    // ------------------------------------------------------------------------------------------

    @Override
    public String key() {
        return key;
    }

    public SettingType type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** Lowest allowed value or {@code null}. */
    public Double min() {
        return min;
    }

    /** Highest allowed value or {@code null}. */
    public Double max() {
        return max;
    }

    /** Allowed values of an ENUM (empty for other types). */
    public List<String> allowedValues() {
        return allowedValues;
    }

    @Override
    public String file() {
        return file;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean overridable() {
        return overridable;
    }

    /** One short line such as "whole number from 1 to 3" for comments, docs and error messages. */
    public String constraintText() {
        String range = min != null && max != null ? " from " + number(min) + " to " + number(max)
            : min != null ? ", at least " + number(min) : max != null ? ", at most " + number(max) : "";
        return switch (type) {
            case BOOLEAN -> "true or false";
            case INT -> "whole number" + range;
            case DOUBLE -> "number" + range;
            case STRING -> "text";
            case ENUM -> "one of: " + String.join(", ", allowedValues);
            case DURATION -> "time such as 30s, 10m, 2h or 1d"
                + (min != null ? ", at least " + DurationText.format(seconds(min)) : "")
                + (max != null ? ", at most " + DurationText.format(seconds(max)) : "");
            case STRING_LIST -> "list of texts";
            case SECRET -> "secret text (never shown)";
        };
    }

    private static Duration seconds(double s) {
        return Duration.ofMillis(Math.round(s * 1000));
    }

    private static String number(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    /** Text that is safe to show to people and to write to logs: secrets are masked. */
    public String display(Object value) {
        if (type == SettingType.SECRET) {
            return value == null || String.valueOf(value).isEmpty() ? "(not set)" : "********";
        }
        if (value instanceof Duration d) {
            return DurationText.format(d);
        }
        return String.valueOf(value);
    }

    /** The value in the shape it is written to YAML (Duration becomes text, everything else stays). */
    public Object toYaml(T value) {
        return value instanceof Duration d ? DurationText.format(d) : value;
    }

    // ------------------------------------------------------------------------------------------
    // Reading and checking values
    // ------------------------------------------------------------------------------------------

    /**
     * Turns a raw YAML value into a checked value. Never throws: a missing value gives the default, a wrong value gives
     * the default (or the nearest allowed number) together with a problem text.
     */
    @SuppressWarnings("unchecked")
    public Parsed<T> parse(Object raw) {
        if (raw == null) {
            return new Parsed<>(defaultValue, null);
        }
        try {
            return (Parsed<T>) parseTyped(raw);
        } catch (IllegalArgumentException ex) {
            String shown = type == SettingType.SECRET ? "the value" : "'" + raw + "'";
            return new Parsed<>(defaultValue, shown + " is not valid (" + constraintText() + "); using the default "
                + display(defaultValue) + ".");
        }
    }

    /** Like {@link #parse(Object)} but refuses wrong values instead of repairing them (used for commands and the GUI). */
    public T require(Object raw) {
        Parsed<T> parsed = parse(raw);
        if (raw == null || parsed.problem() != null) {
            throw new IllegalArgumentException(key + ": " + (raw == null ? "no value given" : parsed.problem()));
        }
        return parsed.value();
    }

    private Parsed<?> parseTyped(Object raw) {
        switch (type) {
            case BOOLEAN -> {
                if (raw instanceof Boolean b) {
                    return new Parsed<>(b, null);
                }
                String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
                if (raw instanceof String && (s.equals("true") || s.equals("false"))) {
                    return new Parsed<>(s.equals("true"), null);
                }
                throw new IllegalArgumentException();
            }
            case INT -> {
                long value;
                if (raw instanceof Number n) {
                    value = n.intValue();
                } else if (raw instanceof String s) {
                    value = parseLong(s);
                } else {
                    throw new IllegalArgumentException();
                }
                double clamped = clamp(value);
                return new Parsed<>((int) clamped, clamped == value ? null : outOfRange(Long.toString(value), number(clamped)));
            }
            case DOUBLE -> {
                double value;
                if (raw instanceof Number n) {
                    value = n.doubleValue();
                } else if (raw instanceof String s) {
                    value = parseDouble(s);
                } else {
                    throw new IllegalArgumentException();
                }
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException();
                }
                double clamped = clamp(value);
                return new Parsed<>(clamped, clamped == value ? null : outOfRange(Double.toString(value), number(clamped)));
            }
            case STRING, SECRET -> {
                if (raw instanceof Map<?, ?> || raw instanceof List<?>) {
                    throw new IllegalArgumentException();
                }
                return new Parsed<>(String.valueOf(raw), null);
            }
            case ENUM -> {
                String s = String.valueOf(raw).trim();
                for (String allowed : allowedValues) {
                    if (allowed.equalsIgnoreCase(s)) {
                        return new Parsed<>(allowed, null);
                    }
                }
                throw new IllegalArgumentException();
            }
            case DURATION -> {
                Duration value;
                if (raw instanceof Duration d) {
                    value = d;
                } else if (raw instanceof Number n) {
                    value = Duration.ofMillis(Math.round(n.doubleValue() * 1000));
                } else if (raw instanceof String s) {
                    value = DurationText.parse(s);
                } else {
                    throw new IllegalArgumentException();
                }
                double secs = value.toMillis() / 1000.0;
                double clamped = clamp(secs);
                return clamped == secs ? new Parsed<>(value, null)
                    : new Parsed<>(seconds(clamped), outOfRange(DurationText.format(value), DurationText.format(seconds(clamped))));
            }
            case STRING_LIST -> {
                if (!(raw instanceof List<?> list)) {
                    throw new IllegalArgumentException();
                }
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> || o instanceof List<?>) {
                        throw new IllegalArgumentException();
                    }
                    if (o != null) {
                        out.add(String.valueOf(o));
                    }
                }
                return new Parsed<>(List.copyOf(out), null);
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private String outOfRange(String given, String used) {
        return given + " is outside the allowed range (" + constraintText() + "); using " + used + ".";
    }

    private double clamp(double value) {
        if (min != null && value < min) {
            return min;
        }
        if (max != null && value > max) {
            return max;
        }
        return value;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException();
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return file + ":" + key;
    }
}
