package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.config.WorldProfiles;
import com.jason.plugins.antibadwords.engine.integrations.PapiLuckPermsSettings;
import com.jason.plugins.antibadwords.gui.GuiListener;
import com.jason.plugins.antibadwords.gui.GuiManager;
import com.jason.plugins.antibadwords.integrations.VaultHook;
import com.jason.plugins.antibadwords.integrations.essentials.EssentialsHook;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private DiscordSrvHook discordSrv;
    private EssentialsHook essentials;
    private PlaceholderApiHook placeholderApi;
    private BukkitPlatform platform;
    private NetworkMessaging networkMessaging;

    @Override
    public void onEnable() {
        punishmentExecutor = new PunishmentExecutor(this);
        // Creates the config folder on the first start, or converts an old single config.yml (a backup is kept).
        platform = new BukkitPlatform(this, punishmentExecutor);
        engine = Engine.load(platform, getDataFolder().toPath());
        engine.addReloadListener(this::registerProfilePermissions);
        registerProfilePermissions();

        gui = new GuiManager(this);
        startEssentials(); // before the listeners: it decides the chat priority
        AntiBadWordsListener listener = new AntiBadWordsListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        registerChatListener(listener);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);

        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("antibadwords"),
            "antibadwords command not defined in plugin.yml");
        AntiBadWordsCommand command = new AntiBadWordsCommand(this);
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        // Players who were already online during a /reload: repair leftovers from earlier sessions.
        getServer().getOnlinePlayers().forEach(punishmentExecutor::restoreLeftovers);
        startDiscordSrv();
        startLuckPerms();
        startVault();
        startPlaceholderApi();
        networkMessaging = new NetworkMessaging(this, engine);
        networkMessaging.start();
        try {
            com.jason.plugins.antibadwords.integrations.ExtraHooks.start(engine);
        } catch (Throwable ex) {
            getLogger().warning("CoreProtect and packet hooks: error. They did not start (" + ex.getClass().getSimpleName() + ").");
        }
        logStartupBanner();
    }

    @Override
    public void onDisable() {
        if (networkMessaging != null) {
            networkMessaging.stop();
            networkMessaging = null;
        }
        if (essentials != null) {
            essentials.stop(engine);
            essentials = null;
        }
        if (placeholderApi != null) {
            placeholderApi.stop();
            placeholderApi = null;
        }
        if (platform != null) {
            platform.setMessageParser(null);
        }
        if (discordSrv != null) {
            discordSrv.stop();
            discordSrv = null;
        }
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

    /** EssentialsX is an optional soft dependency (reflection only): any problem with it must never stop this plugin. */
    private void startEssentials() {
        try {
            essentials = EssentialsHook.start(this, engine);
        } catch (Throwable ex) { // NoClassDefFoundError and friends included on purpose
            getLogger().warning("The EssentialsX hook could not start (" + ex.getClass().getSimpleName() + "); continuing without it.");
        }
    }

    /**
     * Chat is registered by hand to pick its priority from the settings. Spelled out because order matters:
     * <ul>
     *   <li>EssentialsX Chat (plugin {@code EssentialsChat}) has handlers at LOWEST, NORMAL and HIGHEST. With it installed, ours runs
     *       at LOWEST ({@code integrations.essentials.chat-priority: auto}), before its formatting and recipient handling.</li>
     *   <li>{@code plugin.yml} has {@code loadbefore: [EssentialsChat]}, so this plugin is enabled (and its LOWEST handler registered)
     *       before EssentialsChat's own: Bukkit runs equal priorities in registration order.</li>
     *   <li>{@code ignoreCancelled = true}: a message another plugin already cancelled is not scanned or counted.</li>
     *   <li>A blocked message is cancelled and its recipients are cleared, a censored one gets the cleaned text via
     *       {@code setMessage}, so EssentialsX Chat formats the cleaned text.</li>
     * </ul>
     */
    private void registerChatListener(AntiBadWordsListener listener) {
        EventPriority priority = EssentialsHook.chatPriority(engine);
        java.util.List<String> channelPlugins = ChatChannelPlugins.detect(engine);
        EventPriority channelPriority = ChatChannelPlugins.priority(engine, channelPlugins);
        if (!channelPlugins.isEmpty()) {
            getLogger().info("Chat-channel plugins found: " + String.join(", ", channelPlugins)
                + ". Their channel names are not read; use integrations.chatchannels.channel-commands for channel profiles.");
        }
        if (channelPriority != null) {
            priority = channelPriority;
        }
        getServer().getPluginManager().registerEvent(AsyncPlayerChatEvent.class, listener, priority, (l, event) -> {
            if (event instanceof AsyncPlayerChatEvent chat) {
                listener.onAsyncChat(chat);
            }
        }, this, true);
        ChannelChatEvents.register(this, engine);
        getLogger().info("Chat check priority: " + priority + (essentials != null ? " (EssentialsX found)" : "") + ".");
    }

    /** DiscordSRV is an optional soft dependency: any problem with it (missing, other version) must never stop this plugin. */
    private void startDiscordSrv() {
        try {
            discordSrv = DiscordSrvHook.start(this, engine);
        } catch (Throwable ex) { // NoClassDefFoundError and friends included on purpose
            getLogger().warning("The DiscordSRV hook could not start (" + ex.getClass().getSimpleName() + "); webhooks still work.");
        }
    }

    /** LuckPerms is optional: its classes are only touched after the plugin was found, and any problem is one log line. */
    private void startLuckPerms() {
        try {
            if (!engine.value(PapiLuckPermsSettings.LP_ENABLED)) {
                getLogger().info("LuckPerms hook: switched off (integrations.luckperms.enabled).");
                return;
            }
            org.bukkit.plugin.Plugin lp = getServer().getPluginManager().getPlugin(LuckPermsHook.PLUGIN_NAME);
            if (lp == null || !lp.isEnabled()) {
                return; // not installed: nothing to report
            }
            engine.setPermissionSource(LuckPermsHook.start(platform.logger()));
        } catch (Throwable ex) { // NoClassDefFoundError and friends included on purpose
            getLogger().warning("The LuckPerms hook could not start (" + ex.getClass().getSimpleName() + "); profiles by group and offline checks are off.");
        }
    }

    /** Vault is optional: used for groups and prefixes only when LuckPerms is not connected. Reflection only, any problem is one log line. */
    private void startVault() {
        try {
            for (String name : new String[] {"TAB", "NametagEdit"}) {
                if (platform.hasPlugin(name) && engine.value(name.equals("TAB")
                    ? com.jason.plugins.antibadwords.engine.integrations.NameplateSettings.TAB
                    : com.jason.plugins.antibadwords.engine.integrations.NameplateSettings.NAMETAGEDIT)) {
                    getLogger().info(name + ": found. The plugin scans its commands (integrations." + name.toLowerCase(Locale.ROOT) + ".scan-commands).");
                }
            }
            if (!platform.hasPlugin(VaultHook.PLUGIN_NAME)) {
                return;
            }
            if (!engine.value(com.jason.plugins.antibadwords.engine.integrations.NameplateSettings.VAULT)) {
                getLogger().info("Vault hook: switched off (integrations.vault.enabled).");
            } else if (engine.permissionSource() != null) {
                getLogger().info("Vault found, but " + engine.permissionSource().name() + " is already connected; Vault is not used.");
            } else {
                engine.setPermissionSource(VaultHook.start(platform.logger()));
            }
        } catch (Throwable ex) {
            getLogger().warning("The Vault hook could not start (" + ex.getClass().getSimpleName() + "); Vault groups and prefixes are off.");
        }
    }

    /** PlaceholderAPI is optional too: the expansion class is loaded only when the plugin is there. */
    private void startPlaceholderApi() {
        try {
            placeholderApi = PlaceholderApiHook.start(this, engine, platform.logger());
            if (placeholderApi != null) {
                platform.setMessageParser(placeholderApi::parse);
            }
        } catch (Throwable ex) {
            getLogger().warning("The PlaceholderAPI hook could not start (" + ex.getClass().getSimpleName() + "); placeholders are off.");
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
        // menu edit rights: antibadwords.gui.edit.<category>, default TRUE (holders of antibadwords.gui may edit everything
        // until an admin negates a node). Without this Bukkit would give an unknown node to operators only.
        for (String node : com.jason.plugins.antibadwords.engine.gui.Menu.editNodes(
            new com.jason.plugins.antibadwords.engine.gui.EngineConfigAccess(engine))) {
            if (manager.getPermission(node) == null) {
                manager.addPermission(new Permission(node, "Change the settings of this category in the /abw menu (needs antibadwords.gui too).",
                    PermissionDefault.TRUE));
            }
        }
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
