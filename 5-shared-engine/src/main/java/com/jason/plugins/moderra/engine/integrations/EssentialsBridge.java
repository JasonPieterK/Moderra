package com.jason.plugins.moderra.engine.integrations;

import java.util.Optional;
import java.util.UUID;

/**
 * What the engine needs from EssentialsX. The Bukkit plugin implements it by reflection (EssentialsX is never a compile
 * dependency); tests use a fake. Every method may throw a {@link RuntimeException} when Essentials misbehaves, the
 * {@link EssentialsLink} catches it. Called on the main thread, except {@link #mute(UUID)} which chat checks call from the
 * async chat thread.
 */
public interface EssentialsBridge {
    /** Essentials' own mute of a player. {@code until} = end in milliseconds since 1970, or -1 for permanent. */
    record ExternalMute(long until, String reason) {
    }

    /** The Essentials mute of the player, empty when not muted (or unknown). */
    Optional<ExternalMute> mute(UUID player);

    /** Mutes in Essentials. {@code until} = end in milliseconds since 1970, or -1 for permanent. @return false when it failed */
    boolean setMute(UUID player, String name, long until, String reason);

    /** Lifts the Essentials mute. @return false when the player was not muted there or it failed */
    boolean clearMute(UUID player);

    /** The player's /nick nickname, or "" when there is none (or it cannot be read). */
    String nickname(UUID player);

    /** Removes the /nick nickname. @return false when it failed */
    boolean resetNickname(UUID player);

    /** Whether the player is vanished (Essentials /vanish, or any plugin that sets the Bukkit metadata "vanished"). */
    boolean vanished(UUID player);
}
