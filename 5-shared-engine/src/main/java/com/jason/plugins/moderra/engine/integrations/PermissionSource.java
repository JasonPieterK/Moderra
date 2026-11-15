package com.jason.plugins.moderra.engine.integrations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What a permission plugin (LuckPerms on Bukkit) can tell the engine beyond {@code Platform.has}: groups, meta values and
 * permission checks for players who are offline. Optional: {@code engine.setPermissionSource(...)}; without one nothing changes.
 *
 * <p>{@link #primaryGroup}, {@link #inGroup} and {@link #meta} are called for every message of an online player, from any
 * thread (also the chat thread), so they must answer from memory and never block. Only {@link #hasPermission} may take time.
 */
public interface PermissionSource {
    /** Short name for logs and {@code /moderra info} ("LuckPerms"). */
    String name();

    /** The player's primary group, or empty when unknown (offline, not loaded). Answers from memory. */
    Optional<String> primaryGroup(UUID player);

    /** Whether the player is in the group, directly or by inheritance (false when unknown). Answers from memory. */
    boolean inGroup(UUID player, String group);

    /** A meta value of an online player (for example {@code moderra-profile}), or empty. Answers from memory. */
    Optional<String> meta(UUID player, String key);

    /** The chat prefix of an online player (Vault Chat, LuckPerms meta), colour codes as stored, or empty. Answers from memory. */
    default Optional<String> prefix(UUID player) {
        return Optional.empty();
    }

    /**
     * Permission check for any player, also one who is offline (the data is loaded in the background). The future never
     * fails: an unknown player or an error completes with {@code false}.
     */
    CompletableFuture<Boolean> hasPermission(UUID player, String node);
}
