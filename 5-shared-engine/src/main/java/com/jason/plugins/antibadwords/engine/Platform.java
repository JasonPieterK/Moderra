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

    /**
     * Whether a server plugin (Bukkit family) or mod with this name or id is installed and enabled. Used to detect ban
     * plugins and Floodgate. Default: false (Fabric and Forge have no way for it yet, so integrations treat that as "not there").
     */
    default boolean hasPlugin(String name) {
        return false;
    }

    /**
     * The IP address (text, no port) an online player connected from; {@code null} when unknown or the player is offline.
     * Used for {@code {ip}} in ban templates and stored in players.json (see {@code known-players.store-ip}).
     * Default: unknown (the mods do not expose the connection address in this repository).
     */
    default String playerIp(UUID player) {
        return null;
    }

    /** Name of the world the player is in ("" when unknown). */
    String worldName(UUID player);

    /** Dimension of that world such as {@code minecraft:the_nether} ("" when unknown). */
    default String dimensionName(UUID player) {
        return "";
    }

    /**
     * Whether the loader can deliver a changed text for this source (a "censor" verdict). Where it cannot, the engine
     * turns a censoring verdict into a blocking one. Default: nowhere.
     *
     * @param source {@link Message#CHAT}, {@link Message#SIGN} ...
     */
    default boolean canRewrite(String source) {
        return false;
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

    /** Whether {@link #openSignPrompt} works here (a sign editor can be opened for a text input). Default: no, the chat is used. */
    default boolean supportsSignPrompt() {
        return false;
    }

    /**
     * Opens a sign editor for the player; when the player finishes it the loader calls
     * {@code engine.prompts().onSignInput(uuid, lines)} and cancels the edit when that returns true. Called again after
     * an invalid answer. Only called when {@link #supportsSignPrompt()} is true.
     */
    default void openSignPrompt(UUID player, String title, String hint) {
    }

    /** Whether {@link #openMenu} can show the settings menu here. Default: no ({@code /abw} then prints its usage line). */
    default boolean supportsMenu() {
        return false;
    }

    /**
     * Opens the settings menu ({@code engine.gui.Menu}) for the player. Only called on the main thread, for a player
     * that has {@code antibadwords.gui}, and only when {@link #supportsMenu()} is true.
     *
     * @return false when the menu could not be opened
     */
    default boolean openMenu(UUID player) {
        return false;
    }

    /**
     * Opens the menu at a page: {@code page} is what the player typed after {@code /abw gui} (a category name, a special
     * page such as {@code ladder} or {@code profiles}, a setting key, or a page number; see
     * {@code engine.gui.Menu#open(Engine, String)}). The platform builds the state with that method. Default: ignore the page.
     */
    default boolean openMenu(UUID player, String page) {
        return openMenu(player);
    }
}
