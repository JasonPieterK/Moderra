package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.Verdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Hands every place a player can type text to the shared engine.
 *
 * <p>Every handler does the same three things: build a {@link Message}, ask {@code engine.inspect}, apply the
 * {@link Verdict} (cancel the event, or replace the chat text when a check censored it). The follow-up (strikes,
 * warnings, punishments) is {@code engine.onViolation}, which moves itself to the main thread.
 *
 * <p>Uses only the Bukkit/Spigot API, so the same jar runs on Spigot, Paper and Purpur.
 */
public final class AntiBadWordsListener implements Listener {
    private final AntiBadWordsPlugin plugin;

    public AntiBadWordsListener(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    private Engine engine() {
        return plugin.getEngine();
    }

    private static Message message(Player player, String source, String text) {
        return Message.of(player.getUniqueId(), player.getName(), player.getWorld().getName(), source, text, player::hasPermission)
            .withDimension(BukkitPlatform.dimensionName(player.getWorld()));
    }

    // ------------------------------------------------------------------------------------------
    // One handler per text source
    // ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        // Runs on an async thread: inspect() is thread-safe, onViolation() switches to the main thread itself.
        Message message = message(event.getPlayer(), Message.CHAT, event.getMessage());
        Verdict verdict = engine().inspect(message);
        if (verdict.blocked()) {
            event.setCancelled(true);
        } else if (verdict.censored()) {
            event.setMessage(verdict.text());
        }
        engine().onViolation(message, verdict);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // The engine decides which commands are scanned (scanned-commands / ignored-commands) and looks at the arguments.
        Message message = message(event.getPlayer(), Message.COMMAND, event.getMessage());
        Verdict verdict = engine().inspect(message);
        if (verdict.blocked() || verdict.censored()) {
            event.setCancelled(true); // a command cannot be rewritten safely, so a censoring check blocks it
        }
        engine().onViolation(message, verdict);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignWrite(SignChangeEvent event) {
        Message template = message(event.getPlayer(), Message.SIGN, "");
        Verdict verdict = engine().inspectFirst(template, Arrays.asList(event.getLines()));
        if (verdict.blocked() || verdict.censored()) {
            event.setCancelled(true);
        }
        engine().onViolation(template, verdict);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        BookMeta meta = event.getNewBookMeta();
        List<String> texts = new ArrayList<>(meta.getPages());
        if (meta.hasTitle() && meta.getTitle() != null) {
            texts.add(meta.getTitle());
        }
        Message template = message(event.getPlayer(), Message.BOOK, "");
        Verdict verdict = engine().inspectFirst(template, texts);
        if (verdict.blocked() || verdict.censored()) {
            event.setCancelled(true);
        }
        engine().onViolation(template, verdict);
    }

    /**
     * PrepareAnvilEvent fires on <em>every keystroke</em>, so only one violation is counted per anvil
     * session; the result slot stays empty for as long as the name contains a blocked word.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        AnvilInventory anvil = event.getInventory();
        String renameText = anvil.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        Message message = message(player, Message.ANVIL, renameText);
        Verdict verdict = engine().inspect(message);
        if (!verdict.blocked() && !verdict.censored()) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        event.setResult(null);
        if (engine().firstAnvilViolation(player.getUniqueId())) {
            engine().onViolation(message, verdict);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof AnvilInventory) {
            engine().anvilClosed(event.getPlayer().getUniqueId());
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
        plugin.getPunishmentExecutor().restoreAll(event.getPlayer());
        engine().onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        // A punishment command (or anything else) may kick the player; restore attributes before data is saved.
        plugin.getPunishmentExecutor().restoreAll(event.getPlayer());
    }
}
