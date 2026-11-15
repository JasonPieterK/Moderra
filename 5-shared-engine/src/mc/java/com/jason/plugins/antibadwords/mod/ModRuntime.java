package com.jason.plugins.antibadwords.mod;

import com.jason.plugins.antibadwords.PunishmentAction;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.Platform;
import com.jason.plugins.antibadwords.engine.PlayerRef;
import com.jason.plugins.antibadwords.engine.Verdict;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Everything that needs Minecraft classes, shared by the Fabric and Forge mods (both use Mojang names): the
 * {@link Platform} the shared engine talks to, the entry points the loader events and mixins call, and the punishment
 * actions that need the game (effects, attributes, sounds). All rules live in the engine; each entry point only builds a
 * {@link Message}, asks {@code engine.inspect} and reports whether the action must be cancelled.
 * All public methods must be called on the server thread.
 *
 * <p>NOT compiled by the Maven test module; see 6-guides/FABRIC-AND-FORGE-STATUS.md.
 */
public final class ModRuntime {
    /** The shared engine; {@code null} until {@link #init} ran (the mixins check this). */
    public static Engine engine;
    private static AttributeLedger ledger;
    private static ModPermissions permissions;
    private static EngineLog log;
    /** The running server and its main thread, remembered the first time a player or command reaches us. */
    private static volatile MinecraftServer server;
    private static volatile Thread serverThread;
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AntiBadWords-timer");
        t.setDaemon(true);
        return t;
    });

    /** Asks the loader whether a mod id is installed (Fabric: isModLoaded, Forge: ModList). Set by the loader before init. */
    private static volatile java.util.function.Predicate<String> modLookup;

    private ModRuntime() {
    }

    public static void setModLookup(java.util.function.Predicate<String> lookup) {
        modLookup = lookup;
    }

    /**
     * @param loaderName "Fabric" or "Forge"
     */
    public static void init(Path configDir, String version, String loaderName, ModPermissions perms, EngineLog logger) {
        log = logger;
        permissions = perms;
        ledger = new AttributeLedger(configDir.resolve("pending-attributes.txt"));
        // Creates the config folder on the first start, or converts an old single config.yml (a backup is kept).
        engine = Engine.load(new ModPlatform(configDir, version, loaderName), configDir);
        logger.info("AntiBadWords " + version + " loaded.");
    }

    /** Remembers the server (and that the calling thread is its main thread). Called from every entry point. */
    public static void remember(MinecraftServer running) {
        if (running != null && server != running) {
            server = running;
            serverThread = Thread.currentThread();
        }
    }

    /**
     * Diagnostic used by the repository's test scripts ({@code test-on-real-server.sh}): when the JVM is started with
     * {@code -Dantibadwords.selftest=1}, force-loads every class we inject into, so a Mixin that does not fit this
     * Minecraft version fails loudly at startup instead of the first time a player joins. Does nothing otherwise.
     */
    public static void selfTestIfRequested() {
        if (System.getProperty("antibadwords.selftest") == null) {
            return;
        }
        for (String name : new String[] {"net.minecraft.server.network.ServerGamePacketListenerImpl",
            "net.minecraft.world.inventory.AnvilMenu", "net.minecraft.world.inventory.ItemCombinerMenu",
            "net.minecraft.world.level.BaseCommandBlock",
            "net.minecraft.commands.Commands"}) {
            try {
                Class.forName(name);
                log.info("SELFTEST loaded+mixed " + name);
            } catch (Throwable t) {
                log.warn("SELFTEST FAILED " + name + ": " + t);
            }
        }
    }

    /** The online player with this id, or {@code null}. */
    public static ServerPlayer onlinePlayer(UUID id) {
        MinecraftServer running = server;
        return running == null ? null : running.getPlayerList().getPlayer(id);
    }

    public static boolean has(ServerPlayer player, String node) {
        return permissions.has(player, node);
    }

    // ------------------------------------------------------------------------------------------
    // Detection entry points (called by the loader events / mixins). Each returns true when the
    // action must be cancelled; the follow-up (strikes, messages, punishments) is started before returning.
    // ------------------------------------------------------------------------------------------

    /** The dimension id of the player's level, such as {@code minecraft:the_nether} (what worlds.yml rules match on mods). */
    static String dimensionOf(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private static Message message(ServerPlayer player, String source, String text) {
        remember(serverOf(player));
        return Message.of(player.getUUID(), player.getName().getString(), dimensionOf(player), source, text, node -> has(player, node));
    }

    /**
     * A mod cannot rewrite the text anywhere yet, so the engine already turns "censor" into "block" for us
     * ({@code Platform.canRewrite} is false everywhere); the second test is only a safety net.
     */
    private static boolean apply(Message message, Verdict verdict) {
        engine.onViolation(message, verdict);
        return verdict.blocked() || verdict.censored();
    }

    public static boolean blockChat(ServerPlayer player, String text) {
        Message message = message(player, Message.CHAT, text);
        Verdict verdict = engine.inspect(message);
        if (verdict.captured()) {
            // The text was the answer to a GUI text prompt: cancel it silently (not broadcast, not scanned, no strike).
            return true;
        }
        return apply(message, verdict);
    }

    public static boolean blockCommand(CommandSourceStack source, String commandLine) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        // The engine decides which commands are scanned (scanned-commands / ignored-commands) and looks at the arguments.
        Message message = message(player, Message.COMMAND, commandLine);
        return apply(message, engine.inspect(message));
    }

    /** Signs: the packet carries the lines of the side that was edited; the back side uses source sign-back (filters.scan.sign-back). */
    public static boolean blockSign(ServerPlayer player, String[] lines, boolean back) {
        Message template = message(player, back ? Message.SIGN_BACK : Message.SIGN, "");
        return apply(template, engine.inspectFirst(template, Arrays.asList(lines)));
    }

    /** A command block's command was set by a player (packet) or is about to run; {@code where} is for the log. */
    public static boolean blockCommandBlock(String world, String where, String command) {
        return engine.blockCommandBlock(world, where, command);
    }

    public static boolean blockBook(ServerPlayer player, List<String> pages, String title) {
        List<String> texts = new ArrayList<>(pages);
        if (title != null) {
            texts.add(title);
        }
        Message template = message(player, Message.BOOK, "");
        return apply(template, engine.inspectFirst(template, texts));
    }

    /**
     * Anvil: the result is recomputed on every keystroke, so only one violation is counted per anvil session while
     * the result slot stays empty as long as the name contains a blocked word.
     *
     * @return true when the result slot must be emptied
     */
    public static boolean blockAnvil(ServerPlayer player, String renameText, boolean resultPresent) {
        if (renameText == null || renameText.isBlank() || !resultPresent) {
            return false;
        }
        Message message = message(player, Message.ANVIL, renameText);
        Verdict verdict = engine.inspect(message);
        if (!verdict.blocked() && !verdict.censored()) {
            return false;
        }
        if (engine.firstAnvilViolation(player.getUUID())) {
            engine.onViolation(message, verdict);
        }
        return true;
    }

    public static void anvilClosed(UUID player) {
        engine.anvilClosed(player);
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        return player.level().getServer();
    }

    // ------------------------------------------------------------------------------------------
    // What the engine needs from the server
    // ------------------------------------------------------------------------------------------

    private static final class ModPlatform implements Platform {
        private final Path configDir;
        private final String version;
        private final String loaderName;

        ModPlatform(Path configDir, String version, String loaderName) {
            this.configDir = configDir;
            this.version = version;
            this.loaderName = loaderName;
        }

        private static ServerPlayer player(UUID id) {
            MinecraftServer running = server;
            return running == null ? null : running.getPlayerList().getPlayer(id);
        }

        @Override
        public String name() {
            return loaderName;
        }

        @Override
        public String pluginVersion() {
            return version;
        }

        @Override
        public Path dataFolder() {
            return configDir;
        }

        @Override
        public EngineLog logger() {
            return log;
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            List<PlayerRef> out = new ArrayList<>();
            MinecraftServer running = server;
            if (running != null) {
                for (ServerPlayer p : running.getPlayerList().getPlayers()) {
                    out.add(new PlayerRef(p.getUUID(), p.getName().getString()));
                }
            }
            return out;
        }

        @Override
        public Optional<PlayerRef> findPlayer(String exactName) {
            MinecraftServer running = server;
            ServerPlayer p = running == null ? null : running.getPlayerList().getPlayerByName(exactName);
            return p == null ? Optional.empty() : Optional.of(new PlayerRef(p.getUUID(), p.getName().getString()));
        }

        @Override
        public void sendMessage(UUID playerId, String message) {
            ServerPlayer p = player(playerId);
            if (p != null) {
                p.sendSystemMessage(Component.literal(message));
            }
        }

        @Override
        public void broadcast(String message) {
            MinecraftServer running = server;
            if (running != null) {
                ModRuntime.broadcast(running, message);
            }
        }

        @Override
        public void runConsoleCommand(String command) {
            MinecraftServer running = server;
            if (running != null) {
                runAsConsole(running, command);
            }
        }

        @Override
        public void kick(UUID playerId, String reason) {
            ServerPlayer p = player(playerId);
            if (p != null) {
                // through the vanilla /kick command, like effects and sounds: no version-specific networking code
                runAsConsole(server, "kick " + p.getName().getString() + " " + reason);
            }
        }

        @Override
        public boolean has(UUID playerId, String node) {
            ServerPlayer p = player(playerId);
            return p != null && ModRuntime.has(p, node);
        }

        @Override
        public boolean hasPlugin(String name) {
            java.util.function.Predicate<String> lookup = modLookup;
            if (lookup == null || name == null) {
                return false;
            }
            for (String id : ModPluginIds.idsFor(name)) {
                if (lookup.test(id)) {
                    return true;
                }
            }
            return false;
        }

        /** The text prompt is an anvil rename screen (a fake sign needs API that is not verified, see AnvilPrompt). */
        @Override
        public boolean supportsSignPrompt() {
            return true;
        }

        @Override
        public void openSignPrompt(UUID playerId, String title, String hint) {
            ServerPlayer p = player(playerId);
            if (p != null) {
                com.jason.plugins.antibadwords.mod.gui.AnvilPrompt.open(p, title, hint);
            }
        }

        @Override
        public boolean supportsMenu() {
            return com.jason.plugins.antibadwords.mod.gui.ModGui.available();
        }

        @Override
        public boolean openMenu(UUID playerId) {
            return openMenu(playerId, "");
        }

        @Override
        public boolean openMenu(UUID playerId, String page) {
            ServerPlayer p = player(playerId);
            return p != null && com.jason.plugins.antibadwords.mod.gui.ModGui.open(p, page);
        }

        @Override
        public String worldName(UUID playerId) {
            ServerPlayer p = player(playerId);
            return p == null ? "" : dimensionOf(p);
        }

        @Override
        public String dimensionName(UUID playerId) {
            return worldName(playerId);
        }

        @Override
        public String playerIp(UUID playerId) {
            ServerPlayer p = player(playerId);
            if (p == null || p.connection == null) {
                return null;
            }
            java.net.SocketAddress address = p.connection.getRemoteAddress();
            return address instanceof java.net.InetSocketAddress inet && inet.getAddress() != null
                ? inet.getAddress().getHostAddress() : null;
        }

        @Override
        public void runSync(Runnable task) {
            MinecraftServer running = server;
            if (running == null || Thread.currentThread() == serverThread) {
                task.run();
            } else {
                running.execute(task);
            }
        }

        @Override
        public void runSyncLater(Runnable task, long delayMillis) {
            TIMER.schedule(() -> runSync(task), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
        }

        @Override
        public void runAsync(Runnable task) {
            Thread thread = new Thread(task, "AntiBadWords-async");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public String applyAction(UUID playerId, PunishmentAction action) {
            MinecraftServer running = server;
            ServerPlayer p = player(playerId);
            return p == null ? null : execute(running, p, action);
        }
    }

    private static void broadcast(MinecraftServer server, String text) {
        Component component = Component.literal(text);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(component);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Punishment actions that need the game (the engine runs "command" actions itself)
    // ------------------------------------------------------------------------------------------

    private static String execute(MinecraftServer server, ServerPlayer player, PunishmentAction action) {
        try {
            return switch (action.type()) {
                case "effect" -> applyEffect(server, player, action);
                case "attribute" -> applyAttribute(server, player, action);
                case "sound" -> playSound(server, player, action);
                default -> {
                    log.warn("Unknown punishment type '" + action.type() + "' (" + action.id() + ")");
                    yield null;
                }
            };
        } catch (RuntimeException ex) {
            log.warn("Could not apply " + action.type() + " punishment '" + action.id() + "' ("
                + String.join(", ", action.args()) + "): " + ex.getMessage());
            return null;
        }
    }

    /** Effects run through the vanilla /effect command: same result as adding the effect, no registry code. */
    private static String applyEffect(MinecraftServer server, ServerPlayer player, PunishmentAction action) {
        List<String> args = action.args();
        String name = args.get(0);
        int amplifier = Math.max(0, Names.parseInt(args.get(1)));
        int seconds = Math.max(1, Names.parseInt(args.get(2)));
        String key = Names.effectKey(name);
        if (!runChecked(server, "effect give " + player.getUUID() + " minecraft:" + key + " " + seconds + " " + amplifier + " false")) {
            log.warn("Unknown potion effect '" + name + "' (" + action.id() + ")");
            return null;
        }
        return Names.prettyName(name);
    }

    private static String applyAttribute(MinecraftServer server, ServerPlayer player, PunishmentAction action) {
        List<String> args = action.args();
        String name = args.get(0);
        double value = Double.parseDouble(args.get(1).trim());
        int seconds = Math.max(1, Names.parseInt(args.get(2)));
        String key = null;
        Attribute attribute = null;
        for (String candidate : Names.attributeKeys(name)) {
            attribute = findAttribute(candidate);
            if (attribute != null) {
                key = candidate;
                break;
            }
        }
        if (attribute == null) {
            log.warn("Unknown attribute '" + name + "' (" + action.id() + ")");
            return null;
        }
        AttributeInstance instance = player.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
        if (instance == null) {
            return null;
        }
        UUID id = player.getUUID();
        double original = ledger.original(id, key, instance.getBaseValue());
        instance.setBaseValue(value);
        int token = ledger.register(id, key, original);
        String finalKey = key;
        TIMER.schedule(() -> server.execute(() -> {
            // Only the most recent punishment of this attribute may restore it.
            if (!ledger.isLatest(id, finalKey, token)) {
                return;
            }
            // Look the player up again: after dying/respawning the old object is stale. Offline players are
            // restored when they leave.
            ServerPlayer live = server.getPlayerList().getPlayer(id);
            if (live != null) {
                restoreOne(live, finalKey);
            }
        }), seconds, TimeUnit.SECONDS);
        return Names.prettyName(name);
    }

    private static Attribute findAttribute(String path) {
        for (Attribute candidate : BuiltInRegistries.ATTRIBUTE) {
            if (BuiltInRegistries.ATTRIBUTE.getKey(candidate).getPath().equals(path)) {
                return candidate;
            }
        }
        return null;
    }

    private static void setBase(ServerPlayer player, String key, double value) {
        Attribute attribute = findAttribute(key);
        AttributeInstance instance = attribute == null ? null : player.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void restoreOne(ServerPlayer player, String key) {
        Double original = ledger.take(player.getUUID(), key);
        if (original != null) {
            setBase(player, key, original);
        }
    }

    /** Quit/kick: put every pending attribute of this player back. */
    public static void restoreAll(ServerPlayer player) {
        ledger.takeAll(player.getUUID()).forEach((key, original) -> setBase(player, key, original));
    }

    /** Server stop (and mod reload): never leave a player with a half-applied attribute punishment. */
    public static void restoreEverything(MinecraftServer server) {
        remember(server);
        for (UUID id : ledger.playersWithPending()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                restoreAll(player);
            }
        }
        engine.shutdown();
    }

    /** Join: repairs attributes a crash left modified. */
    public static void restoreLeftovers(ServerPlayer player) {
        remember(serverOf(player));
        ledger.takeLeftovers(player.getUUID()).forEach((key, original) -> setBase(player, key, original));
    }

    /**
     * Join: repairs leftovers, then lets the engine check the player's name (filters.scan.join-names: log, warn or
     * kick; a kick goes through the vanilla /kick command, see {@code ModPlatform.kick}).
     */
    public static void onJoin(ServerPlayer player) {
        restoreLeftovers(player);
        engine.onJoin(player.getUUID(), player.getName().getString());
    }

    public static void onQuit(ServerPlayer player) {
        com.jason.plugins.antibadwords.mod.gui.ModGui.onQuit(player.getUUID());
        restoreAll(player);
        engine.onQuit(player.getUUID());
    }

    private static String playSound(MinecraftServer server, ServerPlayer player, PunishmentAction action) {
        List<String> args = action.args();
        String name = Names.stripNamespace(args.get(0));
        String volume = String.valueOf(Float.parseFloat(args.get(1).trim()));
        String pitch = String.valueOf(Float.parseFloat(args.get(2).trim()));
        String soundKey = resolveSoundKey(name);
        // "execute at" so the sound plays where the player is, not at the console's position.
        runChecked(server, "execute at " + player.getUUID() + " run playsound " + soundKey + " master "
            + player.getUUID() + " ~ ~ ~ " + volume + " " + pitch);
        return Names.soundDescription(name);
    }

    /** Accepts {@code entity.villager.no} as well as the old enum style {@code ENTITY_VILLAGER_NO}. */
    private static String resolveSoundKey(String lowerName) {
        if (lowerName.indexOf('.') >= 0) {
            return lowerName;
        }
        for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            String path = id.getPath();
            if (path.replace('.', '_').equals(lowerName)) {
                return path;
            }
        }
        return lowerName; // maybe a custom (resource pack) sound without dots
    }

    // ------------------------------------------------------------------------------------------
    // Command helpers
    // ------------------------------------------------------------------------------------------

    /** Like the plugin's Bukkit.dispatchCommand(console, ...): errors are printed to the server log. */
    public static void runAsConsole(MinecraftServer server, String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), line);
    }

    /** Runs a vanilla command quietly; false when it is unknown or failed. */
    private static boolean runChecked(MinecraftServer server, String command) {
        try {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            return server.getCommands().getDispatcher().execute(command, source) > 0;
        } catch (CommandSyntaxException ex) {
            return false;
        }
    }
}
