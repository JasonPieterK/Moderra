package com.jason.plugins.antibadwords;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiBadWordsPlugin extends JavaPlugin {
    private final ConcurrentMap<UUID, Integer> violations = new ConcurrentHashMap<>();
    private final Set<String> englishWords = new LinkedHashSet<>();
    private final Set<String> indonesianWords = new LinkedHashSet<>();
    private final Set<String> customWords = new LinkedHashSet<>();
    private Set<String> activeWords = Set.of();
    private AntiBadWordsSettings settings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginConfig();

        AntiBadWordsListener listener = new AntiBadWordsListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        AntiBadWordsCommand command = new AntiBadWordsCommand(this);
        Objects.requireNonNull(getCommand("antibadwords"), "antibadwords command not defined")
            .setExecutor(command);
        Objects.requireNonNull(getCommand("antibadwords"), "antibadwords command not defined")
            .setTabCompleter(command);
        logStartupBanner();
    }

    public void loadPluginConfig() {
        reloadConfig();
        englishWords.clear();
        indonesianWords.clear();
        customWords.clear();

        readWordList("word-lists.english", englishWords);
        readWordList("word-lists.indonesian", indonesianWords);
        readWordList("word-lists.custom", customWords);

        Set<String> enabledLists = getConfig().getStringList("filters.enabled-word-lists").stream()
            .map(s -> s.toLowerCase(Locale.ROOT).trim())
            .filter(s -> !s.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (enabledLists.isEmpty()) {
            enabledLists.add("english");
            enabledLists.add("indonesian");
        }
        enabledLists.add("custom");
        activeWords = buildActiveWords(enabledLists);

        List<PunishmentAction> actions = new ArrayList<>();
        readTypedActions("punishment.effects", "effect", actions);
        readTypedActions("punishment.attributes", "attribute", actions);
        readTypedActions("punishment.sounds", "sound", actions);
        readTypedActions("punishment.commands", "command", actions);

        settings = new AntiBadWordsSettings(
            getConfig().getInt("punishment.violations-before-punish", 2),
            List.copyOf(actions),
            color(getConfig().getString("messages.warning-player",
                "&eNot allowed: '&f{word}&e' in &f{source}&e. Warning &f{count}/{limit}&e.")),
            color(getConfig().getString("messages.warning-broadcast-format",
                "&8[&cAntiBadWords&8] &f{player}&7: {warning}")),
            getConfig().getBoolean("messages.broadcast-warning-to-server", true),
            color(getConfig().getString("messages.punishment-player", "&cWatch your language.")),
            getConfig().getBoolean("messages.notify-staff", true),
            color(getConfig().getString("messages.staff-notify-format",
                "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7.")),
            getConfig().getBoolean("filters.matching.collapse-repeated-letters", true),
            Math.max(2, getConfig().getInt("filters.matching.repeated-letter-threshold", 3))
        );
    }

    public AntiBadWordsSettings getSettings() {
        return settings;
    }

    public Set<String> getActiveWords() {
        return activeWords;
    }

    public int addViolation(UUID playerId) {
        return violations.merge(playerId, 1, Integer::sum);
    }

    public int getViolation(UUID playerId) {
        return violations.getOrDefault(playerId, 0);
    }

    public void resetViolation(UUID playerId) {
        violations.put(playerId, 0);
    }

    public void clearViolation(UUID playerId) {
        violations.remove(playerId);
    }

    public boolean addCustomWord(String rawWord) {
        String normalized = normalizeWord(rawWord);
        if (normalized.isBlank()) {
            return false;
        }
        if (!customWords.add(normalized)) {
            return false;
        }
        getConfig().set("word-lists.custom", new ArrayList<>(customWords));
        saveConfig();
        loadPluginConfig();
        return true;
    }

    public boolean removeCustomWord(String rawWord) {
        String normalized = normalizeWord(rawWord);
        if (!customWords.remove(normalized)) {
            return false;
        }
        getConfig().set("word-lists.custom", new ArrayList<>(customWords));
        saveConfig();
        loadPluginConfig();
        return true;
    }

    public List<String> listWords(String listName) {
        return switch (listName.toLowerCase(Locale.ROOT)) {
            case "english" -> new ArrayList<>(englishWords);
            case "indonesian" -> new ArrayList<>(indonesianWords);
            case "custom" -> new ArrayList<>(customWords);
            case "active" -> new ArrayList<>(activeWords);
            default -> List.of();
        };
    }

    public Player findOnlinePlayer(String name) {
        return getServer().getOnlinePlayers().stream()
            .filter(p -> p.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private void readWordList(String path, Collection<String> destination) {
        for (String word : getConfig().getStringList(path)) {
            String normalized = normalizeWord(word);
            if (!normalized.isBlank()) {
                destination.add(normalized);
            }
        }
    }

    private Set<String> buildActiveWords(Set<String> enabledLists) {
        Set<String> result = new LinkedHashSet<>();
        if (enabledLists.contains("english")) {
            result.addAll(englishWords);
        }
        if (enabledLists.contains("indonesian")) {
            result.addAll(indonesianWords);
        }
        if (enabledLists.contains("custom")) {
            result.addAll(customWords);
        }
        return Set.copyOf(result);
    }

    private String normalizeWord(String input) {
        return input.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]", "");
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private void readTypedActions(String sectionPath, String type, List<PunishmentAction> destination) {
        ConfigurationSection section = getConfig().getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String basePath = sectionPath + "." + key;
            boolean enabled = getConfig().getBoolean(basePath + ".enabled", false);
            String format = getConfig().getString(basePath + ".format", "").trim();
            if (!format.isBlank()) {
                destination.add(new PunishmentAction(enabled, type, format));
            }
        }
    }

    private void logStartupBanner() {
        String line = "==============================================";
        getLogger().info(line);
        getLogger().info("  ANTI BAD WORDS");
        getLogger().info("  Version: " + getPluginMeta().getVersion());
        getLogger().info("  Chat and text filter loaded successfully.");
        getLogger().info(line);
    }
}
