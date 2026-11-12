package com.jason.plugins.antibadwords.engine;

import java.util.Optional;

/**
 * One stage of the checking pipeline (bad words, spam, links ...). Register it with
 * {@link Engine#registerCheck(int, TextCheck)}. Checks must be thread-safe: chat is checked off the main thread on
 * Bukkit. A check reads its own settings from the {@link EffectiveSettings} it is given (they already contain the
 * world/profile overrides).
 */
public interface TextCheck {
    /** Stable id, also used in {@code filters.check-order} and in events ({@code badword}, {@code spam} ...). */
    String id();

    /** Whether the check is switched on for this profile (read your own on/off setting here). */
    default boolean enabled(EffectiveSettings settings) {
        return true;
    }

    /** Looks at {@code message.text()}. Empty = nothing found. */
    Optional<Hit> check(Message message, EffectiveSettings settings);
}
