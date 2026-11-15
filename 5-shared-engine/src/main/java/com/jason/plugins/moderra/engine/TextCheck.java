package com.jason.plugins.moderra.engine;

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

    /**
     * Whether this check must see every command a player runs, with the whole command line as text (command-spam
     * counting). Such a check is also asked for commands that are not in {@code scanned-commands} and when
     * {@code filters.scan.commands} is off; it has its own on/off setting. Default: false (the check only sees the
     * arguments of scanned commands, like the bad-word check).
     */
    default boolean seesEveryCommand() {
        return false;
    }

    /**
     * The line to send to the player when a hit of this check stopped the message (already coloured with {@code §}
     * codes), or {@code null} / blank for none. Not used for log-only hits.
     */
    default String feedback(Hit hit, EffectiveSettings settings) {
        return null;
    }

    /**
     * Looks at {@code message.text()}. Empty = nothing found. A check that keeps counters (spam) must not count a
     * message for which {@link Message#probe()} is true.
     */
    Optional<Hit> check(Message message, EffectiveSettings settings);
}
