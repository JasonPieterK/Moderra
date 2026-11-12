package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.PunishmentAction;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the engine needs from the server it runs on. The Bukkit plugin, the Fabric mod and the Forge mod each
 * implement this once; the engine itself never touches Bukkit or Minecraft classes.
 *
 * <p>Unless a method says otherwise it is called on the server's main thread. {@link #has}, {@link #worldName},
 * {@link #dimensionName}, {@link #logger}, {@link #runSync} and {@link #runAsync} may be called from any thread.
 */
public interface Platform {
    /** "Bukkit", "Fabric" or "Forge". */
    String name();

    /** Version of the plugin / mod itself (shown by {@code /abw info}). */
    String pluginVersion();

    /** The config folder ({@code plugins/AntiBadWords} or {@code config/antibadwords}). */
    Path dataFolder();

    EngineLog logger();

    Collection<PlayerRef> onlinePlayers();

    /** Exact-name lookup among online players. */
    Optional<PlayerRef> findPlayer(String exactName);

    /** Sends one chat line (already coloured with {@code §} codes) to an online player; does nothing when offline. */
    void sendMessage(UUID player, String message);

    /** Sends one chat line to every online player. */
    void broadcast(String message);

    /** Runs a command as the server console. A leading {@code /} is allowed. */
    void runConsoleCommand(String command);

    void kick(UUID player, String reason);

    /** Permission check for an online player ({@code false} when the player is offline or unknown). */
    boolean has(UUID player, String node);

    /** Name of the world the player is in ("" when unknown). */
    String worldName(UUID player);

    /** Dimension of that world such as {@code minecraft:the_nether} ("" when unknown). */
    default String dimensionName(UUID player) {
        return "";
    }

    /** Runs the task on the server's main thread: at once when already there, otherwise as soon as possible. */
    void runSync(Runnable task);

    /** Runs the task on the main thread after a delay. */
    void runSyncLater(Runnable task, long delayMillis);

    /** Runs the task off the main thread (file and network work). */
    void runAsync(Runnable task);

    /**
     * Applies one punishment action of type {@code effect}, {@code attribute} or {@code sound} to the player
     * (type {@code command} is run by the engine through {@link #runConsoleCommand}).
     *
     * @return a short description for the punishment message ("blindness"), or {@code null} when it could not be applied
     */
    String applyAction(UUID player, PunishmentAction action);
}
