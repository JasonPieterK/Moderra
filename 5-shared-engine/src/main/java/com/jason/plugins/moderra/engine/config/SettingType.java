package com.jason.plugins.moderra.engine.config;

/** The kinds of values a {@link Setting} can hold. The in-game GUI picks its editor from this. */
public enum SettingType {
    /** true / false. */
    BOOLEAN,
    /** Whole number, optionally limited by min/max. */
    INT,
    /** Decimal number, optionally limited by min/max. */
    DOUBLE,
    /** Free text. */
    STRING,
    /** One value out of a fixed list ({@link Setting#allowedValues()}). */
    ENUM,
    /** A length of time, written like {@code 30s}, {@code 10m}, {@code 1h30m} or a plain number of seconds. */
    DURATION,
    /** A list of texts. */
    STRING_LIST,
    /** Text that must never be shown or logged (webhook URLs, tokens). Use {@link Setting#display(Object)}. */
    SECRET
}
