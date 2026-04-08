package com.jason.plugins.antibadwords;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiBadWordsPlugin extends JavaPlugin {
    private final ConcurrentMap<UUID, Integer> violations = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> languageWordLists = new LinkedHashMap<>();
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
        languageWordLists.clear();
        customWords.clear();

        int protectionLevel = Math.max(1, Math.min(3, getConfig().getInt("filters.protection-level", 3)));

        ConfigurationSection wordsSection = getConfig().getConfigurationSection("word-lists");
        if (wordsSection != null) {
            for (String key : wordsSection.getKeys(false)) {
                if (key.equalsIgnoreCase("custom")) {
                    continue;
                }
                Set<String> words = new LinkedHashSet<>();
                readTieredWordList("word-lists." + key, words, protectionLevel);
                languageWordLists.put(key.toLowerCase(Locale.ROOT), words);
            }
        }
        readWordList("word-lists.custom", customWords);

        Set<String> enabledLists = getConfig().getStringList("filters.enabled-word-lists").stream()
            .map(s -> s.toLowerCase(Locale.ROOT).trim())
            .filter(s -> !s.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (enabledLists.isEmpty()) {
            enabledLists.addAll(languageWordLists.keySet());
        }
        enabledLists.add("custom");
        activeWords = buildActiveWords(enabledLists);

        List<PunishmentAction> actions = new ArrayList<>();
        readTypedActions("punishment.effects", "effect", actions);
        readTypedActions("punishment.attributes", "attribute", actions);
        readTypedActions("punishment.sounds", "sound", actions);
        readTypedActions("punishment.commands", "command", actions);

        settings = new AntiBadWordsSettings(
            getConfig().getBoolean("punishment.enabled", true),
            getConfig().getInt("punishment.violations-before-punish", 2),
            List.copyOf(actions),
            color(getConfig().getString("messages.warning-player",
                "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.")),
            color(getConfig().getString("messages.warning-broadcast-format",
                "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.")),
            color(getConfig().getString("messages.punishment-broadcast-format",
                "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C.")),
            color(getConfig().getString("messages.punishment-player",
                "&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.")),
            color(getConfig().getString("messages.punishment-disabled-player",
                "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.")),
            getConfig().getBoolean("messages.notify-staff", true),
            color(getConfig().getString("messages.staff-notify-format",
                "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7.")),
            getConfig().getBoolean("punishment.final-command.enabled", false),
            getConfig().getString("punishment.final-command.command", ""),
            getConfig().getBoolean("filters.matching.collapse-repeated-letters", true),
            Math.max(2, getConfig().getInt("filters.matching.repeated-letter-threshold", 3))
        );

        long enabledCount = actions.stream().filter(PunishmentAction::enabled).count();
        getLogger().info("LOADED PUNISHMENT ACTIONS: " + actions.size() + " TOTAL, " + enabledCount + " ENABLED.");
        getLogger().info("PROTECTION LEVEL: " + protectionLevel + " (1=LOW, 2=MEDIUM, 3=HIGH)");
        if (enabledCount == 0) {
            getLogger().warning("NO PUNISHMENT ACTIONS ARE ENABLED IN CONFIG.");
        }
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
        String key = listName.toLowerCase(Locale.ROOT);
        if (key.equals("custom")) {
            return new ArrayList<>(customWords);
        }
        if (key.equals("active")) {
            return new ArrayList<>(activeWords);
        }
        Set<String> words = languageWordLists.get(key);
        return words == null ? List.of() : new ArrayList<>(words);
    }

    public List<String> getAvailableLists() {
        List<String> lists = new ArrayList<>(languageWordLists.keySet());
        lists.add("custom");
        lists.add("active");
        return lists;
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

    private void readTieredWordList(String basePath, Collection<String> destination, int protectionLevel) {
        // Backward compatibility: old flat list format.
        readWordList(basePath, destination);
        for (int level = 1; level <= protectionLevel; level++) {
            readWordList(basePath + ".level-" + level, destination);
        }
    }

    private Set<String> buildActiveWords(Set<String> enabledLists) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : languageWordLists.entrySet()) {
            if (enabledLists.contains(entry.getKey())) {
                result.addAll(entry.getValue());
            }
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

            String format;
            if (type.equals("effect")) {
                String effectName = getConfig().getString(basePath + ".effect-name", "").trim();
                int amplifier = getConfig().getInt(basePath + ".amplifier", 0);
                int duration = getConfig().getInt(basePath + ".duration", 0);
                if (!effectName.isBlank() && duration > 0) {
                    format = effectName + "-" + amplifier + "-" + duration;
                } else {
                    format = getConfig().getString(basePath + ".format", "").trim();
                }
            } else if (type.equals("attribute")) {
                String attributeName = getConfig().getString(basePath + ".attribute-name", "").trim();
                Object valueObj = getConfig().get(basePath + ".value");
                String valueRaw = valueObj == null ? "" : String.valueOf(valueObj).trim();
                int duration = getConfig().getInt(basePath + ".duration", 0);
                if (!attributeName.isBlank() && !valueRaw.isBlank() && duration > 0) {
                    format = attributeName + "-" + valueRaw + "-" + duration;
                } else {
                    format = getConfig().getString(basePath + ".format", "").trim();
                }
            } else {
                format = getConfig().getString(basePath + ".format", "").trim();
            }

            if (!format.isBlank()) {
                destination.add(new PunishmentAction(enabled, type, format));
            }
        }
    }

    private void logStartupBanner() {
        String version = getPluginMeta().getVersion();
        String line = "==============================================";
        String gameVersion = getServer().getMinecraftVersion();

        ConsoleCommandSender console = getServer().getConsoleSender();
        console.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + line);
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "    AAA   N   N  TTTTT  III ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   A   A  NN  N    T     I  ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   AAAAA  N N N    T     I  ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   A   A  N  NN    T     I  ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   A   A  N   N    T    III ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + " ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   BBBB    AAA   DDDD ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   B   B  A   A  D   D");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   BBBB   AAAAA  D   D");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   B   B  A   A  D   D");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   BBBB   A   A  DDDD ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + " ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   W   W  OOO  RRRR   DDDD   SSSS ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   W   W O   O R   R  D   D S     ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   W W W O   O RRRR   D   D  SSS  ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   WW WW O   O R  R   D   D     S ");
        console.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "   W   W  OOO  R   R  DDDD  SSSS  ");
        console.sendMessage(ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD + "-- GAME VERSION: " + ChatColor.AQUA + gameVersion.toUpperCase(Locale.ROOT) + ChatColor.LIGHT_PURPLE + " --");
        console.sendMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + "-- PLUGIN VERSION: " + ChatColor.GREEN + version.toUpperCase(Locale.ROOT) + ChatColor.YELLOW + " --");
        console.sendMessage(ChatColor.AQUA.toString() + ChatColor.BOLD + "-- MADE BY " + ChatColor.GOLD + "JASON P" + ChatColor.AQUA + " --");
        console.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + line);
    }
}
