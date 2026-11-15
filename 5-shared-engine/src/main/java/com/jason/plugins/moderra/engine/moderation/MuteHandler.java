package com.jason.plugins.moderra.engine.moderation;

import java.util.UUID;

/**
 * The one thing the punishment ladder needs from the mute feature: "mute this player". The mute service implements it
 * and is installed with {@code engine.setMuteHandler(...)}; until then the engine uses a stand-in that only writes a
 * line to the log.
 */
public interface MuteHandler {
    /** Who did it, when the ladder (not a staff member) mutes somebody. */
    String SYSTEM = "system";

    /**
     * @param player         who is muted
     * @param playerName     their name ("" when unknown)
     * @param durationMillis how long, or {@link DurationParser#PERMANENT}
     * @param reason         shown to the player and stored; may be empty
     * @param actor          the staff member's name, or {@link #SYSTEM}
     * @return {@code true} when the player is muted now
     */
    boolean mute(UUID player, String playerName, long durationMillis, String reason, String actor);
}
