package com.jason.plugins.antibadwords;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffectType;

public final class AntiBadWordsListener implements Listener {
    private final AntiBadWordsPlugin plugin;

    public AntiBadWordsListener(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String matchedWord = findBlockedWord(plain);
        if (matchedWord == null) {
            return;
        }

        event.setCancelled(true);
        int newCount = plugin.addViolation(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> warnOrPunish(player, newCount, matchedWord, "chat"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String matchedWord = findBlockedWord(event.getMessage());
        if (matchedWord == null) {
            return;
        }

        event.setCancelled(true);
        warnOrPunish(player, plugin.addViolation(player.getUniqueId()), matchedWord, "command");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignWrite(SignChangeEvent event) {
        Player player = event.getPlayer();
        for (String line : event.getLines()) {
            String matchedWord = findBlockedWord(line);
            if (matchedWord != null) {
                event.setCancelled(true);
                warnOrPunish(player, plugin.addViolation(player.getUniqueId()), matchedWord, "sign");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        BookMeta bookMeta = event.getNewBookMeta();
        for (String page : bookMeta.getPages()) {
            String matchedWord = findBlockedWord(page);
            if (matchedWord != null) {
                event.setCancelled(true);
                warnOrPunish(player, plugin.addViolation(player.getUniqueId()), matchedWord, "book");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        String renameText = anvil.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }

        String matchedWord = findBlockedWord(renameText);
        if (matchedWord == null || !(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        event.setResult(null);
        warnOrPunish(player, plugin.addViolation(player.getUniqueId()), matchedWord, "anvil");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.clearViolation(event.getPlayer().getUniqueId());
    }

    private void warnOrPunish(Player player, int count, String matchedWord, String source) {
        AntiBadWordsSettings settings = plugin.getSettings();
        int limit = Math.max(1, settings.violationsBeforePunish());
        int remaining = Math.max(0, limit - count);
        String warning = fillTemplate(settings.warningMessage(), player.getName(), matchedWord, source, count, limit, remaining);

        player.sendMessage(warning);
        if (settings.broadcastWarningToServer()) {
            String broadcast = fillTemplate(settings.warningBroadcastFormat(), player.getName(), matchedWord, source, count, limit, remaining)
                .replace("{warning}", warning);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        }

        if (count < limit) {
            return;
        }

        punish(player, matchedWord, source, count, limit);
        plugin.resetViolation(player.getUniqueId());
    }

    private void punish(Player player, String matchedWord, String source, int count, int limit) {
        AntiBadWordsSettings settings = plugin.getSettings();
        for (PunishmentAction action : settings.punishmentActions()) {
            if (!action.enabled()) {
                continue;
            }
            executeAction(player, action);
        }
        player.sendMessage(settings.punishmentMessage());

        if (settings.notifyStaff()) {
            String staff = fillTemplate(settings.staffNotifyFormat(), player.getName(), matchedWord, source,
                count, limit, 0);
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("antibadwords.admin"))
                .forEach(p -> p.sendMessage(staff));
        }
    }

    private void executeAction(Player player, PunishmentAction action) {
        String type = action.type();
        String format = action.format();
        try {
            switch (type) {
                case "effect" -> applyEffect(player, format);
                case "attribute" -> applyAttribute(player, format);
                case "command" -> runConsoleCommand(player, format);
                case "sound" -> playSound(player, format);
                default -> plugin.getLogger().warning("Unknown punishment type: " + type);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Invalid punishment format (" + type + "): " + format);
        }
    }

    private void applyEffect(Player player, String format) {
        String[] parts = format.split("-");
        if (parts.length < 3) {
            return;
        }
        String effectName = parts[0].trim().toLowerCase(Locale.ROOT);
        int amplifier = Math.max(0, Integer.parseInt(parts[1].trim()));
        int seconds = Math.max(1, Integer.parseInt(parts[2].trim()));
        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(effectName));
        if (type == null) {
            return;
        }
        player.addPotionEffect(type.createEffect(seconds * 20, amplifier));
    }

    private void applyAttribute(Player player, String format) {
        String[] parts = format.split("-");
        if (parts.length < 3) {
            return;
        }
        String attributeName = parts[0].trim().toLowerCase(Locale.ROOT);
        double value = Double.parseDouble(parts[1].trim());
        int seconds = Math.max(1, Integer.parseInt(parts[2].trim()));
        Attribute attribute = resolveAttribute(attributeName);
        if (attribute == null) {
            return;
        }
        if (player.getAttribute(attribute) == null) {
            return;
        }
        double oldBase = player.getAttribute(attribute).getBaseValue();
        player.getAttribute(attribute).setBaseValue(value);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getAttribute(attribute) != null) {
                player.getAttribute(attribute).setBaseValue(oldBase);
            }
        }, seconds * 20L);
    }

    private void runConsoleCommand(Player player, String format) {
        String command = format.replace("{player}", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void playSound(Player player, String format) {
        String[] parts = format.split("-");
        if (parts.length < 3) {
            return;
        }
        Sound sound = Sound.valueOf(parts[0].trim().toUpperCase(Locale.ROOT).replace('.', '_'));
        float volume = Float.parseFloat(parts[1].trim());
        float pitch = Float.parseFloat(parts[2].trim());
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private String findBlockedWord(String rawInput) {
        String normalizedInput = normalizeText(rawInput);
        String[] parts = normalizedInput.split("\\s+");
        StringBuilder compactBuilder = new StringBuilder();

        for (String part : parts) {
            String normalizedPart = canonicalizeForMatch(part);
            if (normalizedPart.isBlank() || normalizedPart.length() < 2) {
                continue;
            }
            compactBuilder.append(normalizedPart);
            for (String blockedWord : plugin.getActiveWords()) {
                if (normalizedPart.equals(canonicalizeForMatch(blockedWord))) {
                    return blockedWord;
                }
            }
        }

        // Catch bypasses like "f u c k" or "n g e n t o t" by checking compacted text.
        String compact = compactBuilder.toString();
        if (compact.length() >= 4) {
            for (String blockedWord : plugin.getActiveWords()) {
                String blockedCanonical = canonicalizeForMatch(blockedWord);
                if (blockedCanonical.length() >= 4 && compact.contains(blockedCanonical)) {
                    return blockedWord;
                }
            }
        }
        return null;
    }

    private String normalizeText(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
    }

    private String canonicalizeForMatch(String token) {
        String mapped = token.toLowerCase(Locale.ROOT)
            .replace('@', 'a')
            .replace('$', 's')
            .replace('!', 'i')
            .replace('|', 'i')
            .replace('0', 'o')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
            .replace('8', 'b')
            .replace('9', 'g');

        String cleaned = mapped.replaceAll("[^a-z0-9]", "");
        if (cleaned.isBlank()) {
            return "";
        }

        AntiBadWordsSettings settings = plugin.getSettings();
        if (!settings.collapseRepeatedLetters()) {
            return cleaned;
        }
        int threshold = settings.repeatedLettersThreshold();
        return cleaned.replaceAll("(.)\\1{" + (threshold - 1) + ",}", "$1");
    }

    private String fillTemplate(String template, String player, String word, String source, int count, int limit, int remaining) {
        return template
            .replace("{player}", player)
            .replace("{word}", word)
            .replace("{source}", source)
            .replace("{count}", Integer.toString(count))
            .replace("{limit}", Integer.toString(limit))
            .replace("{remaining}", Integer.toString(remaining));
    }

    private Attribute resolveAttribute(String raw) {
        for (Attribute attribute : Attribute.values()) {
            String key = attribute.getKey().getKey().toLowerCase(Locale.ROOT);
            String namespaced = attribute.getKey().toString().toLowerCase(Locale.ROOT);
            if (raw.equals(key) || raw.equals(namespaced)) {
                return attribute;
            }
        }
        return null;
    }
}
