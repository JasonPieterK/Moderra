package com.jason.plugins.antibadwords.engine;

import java.util.UUID;

/**
 * Counts strikes (violations) per player. The engine uses
 * {@link com.jason.plugins.antibadwords.engine.moderation.PersistentStrikeService} (points per UUID, expiry, decay, cap,
 * saved in {@code data/strikes.json}); with the default settings it behaves exactly like the old plugin.
 * {@link InMemoryStrikeService} is the plain old counter. Swap in another implementation with
 * {@link Engine#setStrikeService(StrikeService)}. Implementations must be thread-safe.
 */
public interface StrikeService {
    /** Adds {@code weight} strikes and returns the player's new total. */
    int add(UUID player, int weight);

    /** The player's current total (0 when none or expired). */
    int get(UUID player);

    /** Forgets one player's strikes. */
    void reset(UUID player);

    /** Forgets everything (server stop). */
    void clear();
}
