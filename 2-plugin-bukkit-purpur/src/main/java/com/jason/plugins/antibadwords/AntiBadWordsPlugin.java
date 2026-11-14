package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.config.WorldProfiles;
import com.jason.plugins.antibadwords.gui.GuiListener;
import com.jason.plugins.antibadwords.gui.GuiManager;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AntiBadWords for Bukkit / Spigot / Paper / Purpur. All rules live in the shared engine (folder
 * {@code 5-shared-engine}); this plugin only connects Bukkit to it: {@link BukkitPlatform} (what the engine needs from
 * the server), {@link AntiBadWordsListener} (events to engine), {@link AntiBadWordsCommand} ({@code /abw}) and
 * {@link PunishmentExecutor} (effects, attributes, sounds).
 */
public final class AntiBadWordsPlugin extends JavaPlugin {
    private Engine engine;
    private PunishmentExecutor punishmentExecutor;
    private GuiManager gui;

    @Override
    public void onEnable() {
        punishmentExecutor = new PunishmentExecutor(this);
        // Creates the config folder on the first start, or converts an old single config.yml (a backup is kept).
        engine = Engine.load(new BukkitPlatform(this, punishmentExecutor), getDataFolder().toPath());
        engine.addReloadListener(this::registerProfilePermissions);
        registerProfilePermissions();

        gui = new GuiManager(this);
        getServer().getPluginManager().registerEvents(new AntiBadWordsListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);

        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("antibadwords"),
            "antibadwords command not defined in plugin.yml");
        AntiBadWordsCommand command = new AntiBadWordsCommand(this);
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        // Players who were already online during a /reload: repair leftovers from earlier sessions.
        getServer().getOnlinePlayers().forEach(punishmentExecutor::restoreLeftovers);
        logStartupBanner();
    }

    @Override
    public void onDisable() {
        if (gui != null) {
            gui.closeAll();
        }
        // Never leave a player with a half-applied attribute punishment.
        if (punishmentExecutor != null) {
            punishmentExecutor.restoreEverything();
        }
        if (engine != null) {
            engine.shutdown();
        }
    }

    public Engine getEngine() {
        return engine;
    }

    public GuiManager getGui() {
        return gui;
    }

    public PunishmentExecutor getPunishmentExecutor() {
        return punishmentExecutor;
    }

    /**
     * Bukkit gives permissions it does not know to every operator. The profile permissions
     * ({@code antibadwords.profile.<name>}) must belong to nobody by default, so they are registered that way for every
     * profile in worlds.yml (again after each reload, because profiles can be added).
     */
    private void registerProfilePermissions() {
        PluginManager manager = getServer().getPluginManager();
        for (String profile : engine.profileNames()) {
            String node = WorldProfiles.PERMISSION_PREFIX + profile;
            if (manager.getPermission(node) == null) {
                manager.addPermission(new Permission(node, "Puts the player on the AntiBadWords profile '" + profile + "'.",
                    PermissionDefault.FALSE));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Startup banner
    // ------------------------------------------------------------------------------------------

    private void logStartupBanner() {
        String version = getDescription().getVersion();
        String line = "==============================================";
        // "26.3-R0.1-SNAPSHOT" -> "26.3" (getMinecraftVersion() is Paper-only)
        String gameVersion = getServer().getBukkitVersion().split("-")[0];
        String red = ChatColor.RED.toString() + ChatColor.BOLD;

        ConsoleCommandSender console = getServer().getConsoleSender();
        console.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + line);
        String[] art = {
            "   AAA   N   N  TTTTT  III ",
            "   A   A  NN  N    T     I  ",
            "   AAAAA  N N N    T     I  ",
            "   A   A  N  NN    T     I  ",
            "   A   A  N   N    T    III ",
            " ",
            "   BBBB    AAA   DDDD ",
            "   B   B  A   A  D   D",
            "   BBBB   AAAAA  D   D",
            "   B   B  A   A  D   D",
            "   BBBB   A   A  DDDD ",
            " ",
            "   W   W  OOO  RRRR   DDDD   SSSS ",
            "   W   W O   O R   R  D   D S     ",
            "   W W W O   O RRRR   D   D  SSS  ",
            "   WW WW O   O R  R   D   D     S ",
            "   W   W  OOO  R   R  DDDD  SSSS  "
        };
        for (String row : art) {
            console.sendMessage(red + row);
        }
        console.sendMessage(ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD + "-- GAME VERSION: " + ChatColor.AQUA + gameVersion.toUpperCase(Locale.ROOT) + ChatColor.LIGHT_PURPLE + " --");
        console.sendMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + "-- PLUGIN VERSION: " + ChatColor.GREEN + version.toUpperCase(Locale.ROOT) + ChatColor.YELLOW + " --");
        console.sendMessage(ChatColor.AQUA.toString() + ChatColor.BOLD + "-- MADE BY " + ChatColor.GOLD + "JASON P" + ChatColor.AQUA + " --");
        console.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + line);
    }
}
