package com.jason.plugins.antibadwords.mod;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.PunishmentAction;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Everything that needs Minecraft classes, shared by the Fabric and Forge mods (both use Mojang names).
 * This is the mod-side equivalent of the plugin's AntiBadWordsListener (reaction part) and PunishmentExecutor.
 * All public methods must be called on the server thread.
 *
 * <p>NOT compiled by the Maven test module; see mods/README.md.
 */
public final class ModRuntime {
    public static ModCore core;
    private static AttributeLedger ledger;
    private static ModPermissions permissions;
    private static ModCore.Log log;
    private static final Set<UUID> ANVIL_FLAGGED = new HashSet<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AntiBadWords-timer");
        t.setDaemon(true);
        return t;
    });

    private ModRuntime() {
    }

    public static void init(Path configDir, String version, ModPermissions perms, ModCore.Log logger) {
        log = logger;
        permissions = perms;
        core = new ModCore(configDir, version, logger);
        ledger = new AttributeLedger(configDir.resolve("pending-attributes.txt"));
        logger.info("AntiBadWords " + version + " loaded.");
    }

    public static boolean has(ServerPlayer player, String node) {
        return permissions.has(player, node);
    }

    // ------------------------------------------------------------------------------------------
    // Detection entry points (called by the loader events / mixins). Each returns true when the
    // action must be cancelled; the violation is handled before returning.
    // ------------------------------------------------------------------------------------------

    public static boolean blockChat(ServerPlayer player, String text) {
        return scan(player, text, "chat", core.settings().scanChat());
    }

    public static boolean blockCommand(CommandSourceStack source, String commandLine) {
        if (!(source.getEntity() instanceof ServerPlayer player) || has(player, ModCore.PERMISSION_BYPASS)) {
            return false;
        }
        String toScan = core.commandTextToScan(commandLine);
        String word = toScan == null ? null : core.find(toScan);
        if (word == null) {
            return false;
        }
        handleViolation(player, word, "command");
        return true;
    }

    public static boolean blockSign(ServerPlayer player, String[] lines) {
        if (!core.settings().scanSigns() || has(player, ModCore.PERMISSION_BYPASS)) {
            return false;
        }
        for (String line : lines) {
            String word = core.find(line);
            if (word != null) {
                handleViolation(player, word, "sign");
                return true;
            }
        }
        return false;
    }

    public static boolean blockBook(ServerPlayer player, List<String> pages, String title) {
        if (!core.settings().scanBooks() || has(player, ModCore.PERMISSION_BYPASS)) {
            return false;
        }
        List<String> texts = new ArrayList<>(pages);
        if (title != null) {
            texts.add(title);
        }
        for (String text : texts) {
            String word = core.find(text);
            if (word != null) {
                handleViolation(player, word, "book");
                return true;
            }
        }
        return false;
    }

    /**
     * Anvil: the result is recomputed on every keystroke, so only one violation is counted per anvil session while
     * the result slot stays empty as long as the name contains a blocked word.
     *
     * @return true when the result slot must be emptied
     */
    public static boolean blockAnvil(ServerPlayer player, String renameText, boolean resultPresent) {
        if (!core.settings().scanAnvil() || has(player, ModCore.PERMISSION_BYPASS)) {
            return false;
        }
        if (renameText == null || renameText.isBlank() || !resultPresent) {
            return false;
        }
        String word = core.find(renameText);
        if (word == null) {
            return false;
        }
        if (ANVIL_FLAGGED.add(player.getUUID())) {
            handleViolation(player, word, "anvil");
        }
        return true;
    }

    public static void anvilClosed(UUID player) {
        ANVIL_FLAGGED.remove(player);
    }

    private static boolean scan(ServerPlayer player, String text, String source, boolean enabled) {
        if (!enabled || has(player, ModCore.PERMISSION_BYPASS)) {
            return false;
        }
        String word = core.find(text);
        if (word == null) {
            return false;
        }
        handleViolation(player, word, source);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Reaction (port of AntiBadWordsListener.handleViolation / notifyStaff / punish)
    // ------------------------------------------------------------------------------------------

    private static MinecraftServer serverOf(ServerPlayer player) {
        return player.level().getServer();
    }

    private static void handleViolation(ServerPlayer player, String word, String source) {
        MinecraftServer server = serverOf(player);
        String name = player.getName().getString();
        ModCore.Outcome outcome = core.handleViolation(player.getUUID(), name, word, source);

        if (outcome.staffMessage() != null) {
            Component staff = Component.literal(outcome.staffMessage());
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (has(other, ModCore.PERMISSION_NOTIFY) && !other.getName().getString().equals(name)) {
                    other.sendSystemMessage(staff);
                }
            }
        }
        if (outcome.logLine() != null) {
            log.info(outcome.logLine());
        }

        if (!outcome.punish()) {
            if (outcome.broadcast() != null) {
                broadcast(server, outcome.broadcast());
            }
            player.sendSystemMessage(Component.literal(outcome.playerMessage()));
            return;
        }

        List<String> applied = new ArrayList<>();
        for (PunishmentAction action : outcome.actions()) {
            String description = execute(server, player, action);
            if (description != null && !description.isBlank()) {
                applied.add(description);
            }
        }
        ModCore.Punished done = core.finishPunishment(outcome, applied);
        broadcast(server, done.broadcast());
        player.sendSystemMessage(Component.literal(done.playerMessage()));
        if (done.finalCommand() != null) {
            runAsConsole(server, done.finalCommand());
        }
    }

    private static void broadcast(MinecraftServer server, String text) {
        Component component = Component.literal(text);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(component);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Punishment actions (port of PunishmentExecutor)
    // ------------------------------------------------------------------------------------------

    private static String execute(MinecraftServer server, ServerPlayer player, PunishmentAction action) {
        try {
            return switch (action.type()) {
                case "effect" -> applyEffect(server, player, action);
                case "attribute" -> applyAttribute(server, player, action);
                case "sound" -> playSound(server, player, action);
                case "command" -> runCommand(server, player, action);
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
        for (UUID id : ledger.playersWithPending()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                restoreAll(player);
            }
        }
        core.clearAllViolations();
    }

    /** Join: repairs attributes a crash left modified. */
    public static void restoreLeftovers(ServerPlayer player) {
        ledger.takeLeftovers(player.getUUID()).forEach((key, original) -> setBase(player, key, original));
    }

    public static void onQuit(ServerPlayer player) {
        restoreAll(player);
        ANVIL_FLAGGED.remove(player.getUUID());
        if (core.settings().clearViolationsOnQuit()) {
            core.resetViolation(player.getUUID());
        }
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

    private static String runCommand(MinecraftServer server, ServerPlayer player, PunishmentAction action) {
        runAsConsole(server, Placeholders.apply(action.args().get(0), Map.of("player", player.getName().getString())));
        return "server command";
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
