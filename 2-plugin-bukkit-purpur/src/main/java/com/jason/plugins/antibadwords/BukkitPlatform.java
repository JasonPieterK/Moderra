package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.Platform;
import com.jason.plugins.antibadwords.engine.PlayerRef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** How the shared engine talks to a Bukkit / Spigot / Paper / Purpur server. Uses only the Bukkit API. */
public final class BukkitPlatform implements Platform {
    private final JavaPlugin plugin;
    private final PunishmentExecutor executor;
    private final EngineLog log;
    private volatile java.util.function.BiFunction<Player, String, String> messageParser;

    public BukkitPlatform(JavaPlugin plugin, PunishmentExecutor executor) {
        this.plugin = plugin;
        this.executor = executor;
        this.log = new EngineLog() {
            @Override
            public void info(String message) {
                plugin.getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                plugin.getLogger().warning(message);
            }
        };
    }

    @Override
    public String name() {
        return "Bukkit";
    }

    @Override
    public String pluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public EngineLog logger() {
        return log;
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        List<PlayerRef> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            out.add(new PlayerRef(player.getUniqueId(), player.getName()));
        }
        return out;
    }

    @Override
    public Optional<PlayerRef> findPlayer(String exactName) {
        Player player = Bukkit.getPlayerExact(exactName);
        return player == null ? Optional.empty() : Optional.of(new PlayerRef(player.getUniqueId(), player.getName()));
    }

    @Override
    public void sendMessage(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            java.util.function.BiFunction<Player, String, String> parser = messageParser;
            player.sendMessage(parser == null ? message : parser.apply(player, message));
        }
    }

    /** Set by the PlaceholderAPI hook (only when integrations.placeholderapi.parse-in-messages is on); {@code null} = off. */
    public void setMessageParser(java.util.function.BiFunction<Player, String, String> parser) {
        this.messageParser = parser;
    }

    @Override
    public void broadcast(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void runConsoleCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
    }

    @Override
    public void kick(UUID playerId, String reason) {
        // ladder kicks can be requested from an async chat thread; Bukkit wants kicks on the main thread
        runSync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.kickPlayer(reason);
            }
        });
    }

    @Override
    public boolean has(UUID playerId, String node) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.hasPermission(node);
    }

    @Override
    public String worldName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? "" : player.getWorld().getName();
    }

    @Override
    public String dimensionName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? "" : dimensionName(player.getWorld());
    }

    /** The vanilla dimension name of a Bukkit world, so worlds.yml can say {@code minecraft:the_nether}. */
    static String dimensionName(World world) {
        World.Environment environment = world.getEnvironment();
        if (environment == World.Environment.NORMAL) {
            return "minecraft:overworld";
        }
        if (environment == World.Environment.NETHER) {
            return "minecraft:the_nether";
        }
        if (environment == World.Environment.THE_END) {
            return "minecraft:the_end";
        }
        return "";
    }

    @Override
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Chat is the one place where Bukkit lets a plugin change the text (AsyncPlayerChatEvent#setMessage). */
    @Override
    public boolean canRewrite(String source) {
        return Message.CHAT.equals(source);
    }

    /*
     * Sign prompts are NOT implemented on Bukkit (supportsSignPrompt() stays false, so text prompts use the chat).
     * The only API that opens a sign editor, Paper's Player#openSign(Sign, Side), needs a real sign block placed in a
     * world (and is not in Bukkit/Spigot), so using it would mean placing a temporary block in the player's world.
     * Not worth the risk (protection plugins, block updates, crashes leaving blocks behind). Chat prompt it is.
     */

    @Override
    public void runSyncLater(Runnable task, long delayMillis) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayMillis / 50L));
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public String applyAction(UUID playerId, PunishmentAction action) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? null : executor.execute(player, action);
    }
}
