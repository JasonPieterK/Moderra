package com.jason.plugins.antibadwords.engine;

import java.util.Map;
import java.util.UUID;

/**
 * Something that happened, told to everyone who subscribed with {@code engine.events().subscribe(...)} (history,
 * Discord ...). Deliberately generic so new features only add new {@code kind}s and {@code data} keys.
 *
 * <p>Kinds published by the core: {@code violation} (data: check, word, detail, severity, action, weight, count, limit,
 * remaining, profile, text), {@code punishment} (data: word, punishments, count, limit, profile) and
 * {@code admin-command} (data: command, sender). The filters add {@code spam}, {@code link} and {@code evasion}: one event
 * for every hit of such a check (data: check, detail, action = block / censor / log-only, weight, profile, text), published
 * <em>in addition to</em> the one {@code violation} event per message - count one or the other, not both. A bad player name
 * on join is a {@code violation} with source {@code join} and action {@code log}, {@code warn} or {@code kick}.
 * Reserved for later features: {@code mute}, {@code unmute}, {@code mute-expired}, {@code discord-in}.
 *
 * @param kind       what happened
 * @param time       when, in milliseconds since 1970
 * @param playerId   the player it is about; {@code null} for console actions
 * @param playerName the player's name ("" when unknown)
 * @param world      world name ("" when unknown)
 * @param source     where it came from: {@code chat}, {@code command}, {@code sign}, {@code book}, {@code anvil} ...
 * @param data       extra facts as text; never contains secrets
 */
public record ModerationEvent(String kind, long time, UUID playerId, String playerName, String world, String source,
                              Map<String, String> data) {
    public static final String VIOLATION = "violation";
    public static final String PUNISHMENT = "punishment";
    public static final String ADMIN_COMMAND = "admin-command";
    public static final String SPAM = "spam";
    public static final String LINK = "link";
    public static final String EVASION = "evasion";

    public ModerationEvent {
        data = Map.copyOf(data);
    }
}
