package com.jason.plugins.moderra.engine;

/**
 * What one {@link TextCheck} found in a message.
 *
 * @param check        id of the check: {@code badword}, {@code spam}, {@code caps}, {@code link}, {@code evasion} ...
 * @param detail       short detail for messages and logs (for {@code badword}: the blocked word)
 * @param severity     1 (mild) to 3 (severe)
 * @param action       what the check suggests doing with the message
 * @param strikeWeight how many strikes this adds for the player (0 = none; then no warning or punishment follows)
 * @param replacement  for {@link Action#CENSOR}: the cleaned text; otherwise {@code null}
 */
public record Hit(String check, String detail, int severity, Action action, int strikeWeight, String replacement) {
    public enum Action {
        /** Do not deliver the text. */
        BLOCK,
        /** Deliver {@link Hit#replacement()} instead. */
        CENSOR,
        /** Deliver the text unchanged, but record the hit. */
        LOG_ONLY
    }

    public static Hit block(String check, String detail, int severity, int strikeWeight) {
        return new Hit(check, detail, severity, Action.BLOCK, strikeWeight, null);
    }

    public static Hit censor(String check, String detail, int severity, int strikeWeight, String replacement) {
        return new Hit(check, detail, severity, Action.CENSOR, strikeWeight, replacement);
    }

    public static Hit logOnly(String check, String detail, int severity, int strikeWeight) {
        return new Hit(check, detail, severity, Action.LOG_ONLY, strikeWeight, null);
    }
}
