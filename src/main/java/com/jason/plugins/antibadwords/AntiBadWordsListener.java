package com.jason.plugins.antibadwords;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Wires every place a player can type text into the {@link WordMatcher}.
 *
 * <p>Uses only the Bukkit/Spigot API, so the same jar runs on Spigot and Paper.
 *
 * <p>Every handler follows the same rule: <em>detect</em> (possibly off the main thread), <em>cancel</em>
 * the event, then hand over to {@link #handleViolation} which always runs on the main thread.
 */
public final class AntiBadWordsListener implements Listener {
    private final AntiBadWordsPlugin plugin;
    /** Players that were already punished for the anvil text they are currently typing (see onPrepareAnvil). */
    private final Set<UUID> anvilFlagged = new HashSet<>();

    public AntiBadWordsListener(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------------
    // Detection: one handler per text source
    // ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        if (!plugin.getSettings().scanChat()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isBypassed(player)) {
            return;
        }
        String matched = plugin.getMatcher().find(event.getMessage());
        if (matched == null) {
            return;
        }
        event.setCancelled(true);
        // Chat runs on an async thread; Bukkit API (messages, effects, commands) must run on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> handleViolation(player, matched, "chat"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        AntiBadWordsSettings settings = plugin.getSettings();
        Player player = event.getPlayer();
        if (!settings.scanCommands() || plugin.isBypassed(player)) {
            return;
        }

        String message = event.getMessage();
        int space = message.indexOf(' ');
        if (space < 0) {
            return; // a bare command has no arguments that could contain a bad word
        }
        String label = commandLabel(message.substring(0, space));
        if (settings.ignoredCommands().contains(label)) {
            return; // e.g. /antibadwords addword <word> must keep working for admins
        }
        if (!settings.scannedCommands().contains("*") && !settings.scannedCommands().contains(label)) {
            return;
        }

        String matched = plugin.getMatcher().find(message.substring(space + 1));
        if (matched != null) {
            event.setCancelled(true);
            handleViolation(player, matched, "command");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignWrite(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSettings().scanSigns() || plugin.isBypassed(player)) {
            return;
        }
        for (String line : event.getLines()) {
            String matched = plugin.getMatcher().find(line);
            if (matched != null) {
                event.setCancelled(true);
                handleViolation(player, matched, "sign");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSettings().scanBooks() || plugin.isBypassed(player)) {
            return;
        }
        BookMeta meta = event.getNewBookMeta();
        List<String> texts = new ArrayList<>();
        texts.addAll(meta.getPages());
        if (meta.hasTitle() && meta.getTitle() != null) {
            texts.add(meta.getTitle());
        }
        for (String text : texts) {
            String matched = plugin.getMatcher().find(text);
            if (matched != null) {
                event.setCancelled(true);
                handleViolation(player, matched, "book");
                return;
            }
        }
    }

    /**
     * PrepareAnvilEvent fires on <em>every keystroke</em>, so we only count one violation per anvil
     * session; the result slot stays empty for as long as the name contains a blocked word.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getSettings().scanAnvil() || !(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        if (plugin.isBypassed(player)) {
            return;
        }
        AnvilInventory anvil = event.getInventory();
        String renameText = anvil.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        String matched = plugin.getMatcher().find(renameText);
        if (matched == null) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        event.setResult(null);
        if (anvilFlagged.add(player.getUniqueId())) {
            handleViolation(player, matched, "anvil");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof AnvilInventory) {
            anvilFlagged.remove(event.getPlayer().getUniqueId());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Repairs attributes left modified by a crash during an attribute punishment.
        plugin.getPunishmentExecutor().restoreLeftovers(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        // A punishment command (or anything else) may kick the player; restore attributes before data is saved.
        plugin.getPunishmentExecutor().restoreAll(event.getPlayer());
    }

    private void release(Player player) {
        plugin.getPunishmentExecutor().restoreAll(player);
        anvilFlagged.remove(player.getUniqueId());
        if (plugin.getSettings().clearViolationsOnQuit()) {
            plugin.resetViolation(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reaction (main thread)
    // ------------------------------------------------------------------------------------------

    private void handleViolation(Player player, String matchedWord, String source) {
        AntiBadWordsSettings settings = plugin.getSettings();
        int count = plugin.addViolation(player.getUniqueId());
        int limit = settings.violationsBeforePunish();
        int remaining = Math.max(0, limit - count);
        boolean punish = count >= limit && settings.punishmentEnabled();

        Map<String, String> values = Map.of(
            "player", player.getName(),
            "word", matchedWord,
            "source", source,
            "count", Integer.toString(count),
            "limit", Integer.toString(limit),
            "remaining", Integer.toString(remaining));

        notifyStaff(values);

        if (punish) {
            punish(player, values);
            plugin.resetViolation(player.getUniqueId());
            return;
        }

        // Plain warning (or "blocked, but punishment is disabled").
        if (settings.broadcastWarnings()) {
            String broadcast = Placeholders.apply(settings.warningBroadcastMessage(), values);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        }
        if (count >= limit) {
            player.sendMessage(Placeholders.apply(settings.punishmentDisabledMessage(), values));
        } else {
            player.sendMessage(Placeholders.apply(settings.warningPlayerMessage(), values));
        }
    }

    private void punish(Player player, Map<String, String> baseValues) {
        AntiBadWordsSettings settings = plugin.getSettings();
        List<String> applied = new ArrayList<>();
        for (PunishmentAction action : settings.punishmentActions()) {
            if (!action.enabled()) {
                continue;
            }
            String description = plugin.getPunishmentExecutor().execute(player, action);
            if (description != null && !description.isBlank()) {
                applied.add(description);
            }
        }

        Map<String, String> values = new java.util.HashMap<>(baseValues);
        values.put("remaining", "0");
        values.put("punishments", formatPunishmentList(applied));
        values.put("punishment", values.get("punishments"));

        String broadcast = Placeholders.apply(settings.punishmentBroadcastMessage(), values);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        player.sendMessage(Placeholders.apply(settings.punishmentPlayerMessage(), values));

        if (settings.finalCommandEnabled() && !settings.finalCommand().isBlank()) {
            String command = Placeholders.apply(settings.finalCommand(), values);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
        }
    }

    private void notifyStaff(Map<String, String> values) {
        AntiBadWordsSettings settings = plugin.getSettings();
        String staff = Placeholders.apply(settings.staffNotifyFormat(), values);
        if (settings.notifyStaff()) {
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(AntiBadWordsPlugin.PERMISSION_NOTIFY))
                .filter(p -> !p.getName().equals(values.get("player")))
                .forEach(p -> p.sendMessage(staff));
        }
        if (settings.logToConsole()) {
            plugin.getLogger().info(values.get("player") + " said blocked word '" + values.get("word")
                + "' in " + values.get("source") + " (violation " + values.get("count") + "/" + values.get("limit") + ")");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** {@code "/Minecraft:Msg"} -> {@code "msg"}. */
    private static String commandLabel(String firstWord) {
        String label = firstWord.startsWith("/") ? firstWord.substring(1) : firstWord;
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        return label.toLowerCase(Locale.ROOT);
    }

    private static String formatPunishmentList(List<String> items) {
        if (items.isEmpty()) {
            return "punishment";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
    }
}
