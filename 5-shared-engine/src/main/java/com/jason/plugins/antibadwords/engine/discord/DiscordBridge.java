package com.jason.plugins.antibadwords.engine.discord;

/**
 * What the engine needs from a Discord connection that is not a webhook. Implemented only by the Bukkit-family plugin on top
 * of DiscordSRV; Fabric and Forge never have one. All methods may be called from any thread and must not block.
 */
public interface DiscordBridge {
    /** Shown in {@code /abw discord status}, for example {@code DiscordSRV 1.28.0}. */
    String name();

    /**
     * Posts text into the Discord channel that is linked to this channel name on the other side (for DiscordSRV: a name from its
     * channels setting, like {@code global}).
     *
     * @return false when there is no such channel or it is not ready
     */
    boolean sendToChannel(String channelName, String text);

    /** Tells one Discord user something privately (a direct message). A bridge that cannot do this ignores the call. */
    void notifyUser(String discordUserId, String text);

    /** Whether a Discord message can be delivered with changed text (needed for the {@code censor} action). */
    boolean canRewrite();
}
