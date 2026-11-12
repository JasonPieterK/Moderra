package com.jason.plugins.antibadwords.engine;

import java.util.UUID;

/**
 * Counts strikes (violations) per player. The core ships {@link InMemoryStrikeService}, which behaves exactly like the
 * old plugin (counts in memory, optional expiry, forgotten on restart). Swap in another implementation with
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
