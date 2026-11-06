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
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AntiBadWords - blocks profanity in chat, commands, signs, books and anvil renames and punishes
 * repeat offenders. This class owns the loaded configuration, the word lists and the violation counters.
 */
public final class AntiBadWordsPlugin extends JavaPlugin {
    /** Permission that exempts a player from every check. */
    public static final String PERMISSION_BYPASS = "antibadwords.bypass";
    /** Permission for the admin sub-commands. */
    public static final String PERMISSION_ADMIN = "antibadwords.admin";
    /** Permission to receive staff notifications about blocked words. */
    public static final String PERMISSION_NOTIFY = "antibadwords.notify";

    private record Violation(int count, long lastMillis) {
    }

    private final ConcurrentMap<UUID, Violation> violations = new ConcurrentHashMap<>();
    // Only touched from the main thread (config load / admin commands).
    private final Map<String, Set<String>> languageWordLists = new LinkedHashMap<>();
    private final Set<String> customWords = new LinkedHashSet<>();

    // Replaced as a whole on every reload; volatile so async chat threads always see a consistent value.
    private volatile WordMatcher matcher = new WordMatcher(List.of(), WordMatcher.Options.defaults());
    private volatile Set<String> activeWords = Set.of();
    private volatile AntiBadWordsSettings settings;
    private PunishmentExecutor punishmentExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        punishmentExecutor = new PunishmentExecutor(this);
        loadPluginConfig();

        getServer().getPluginManager().registerEvents(new AntiBadWordsListener(this), this);

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
        // Never leave a player with a half-applied attribute punishment.
        if (punishmentExecutor != null) {
            punishmentExecutor.restoreEverything();
        }
        violations.clear();
    }

    // ------------------------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------------------------

    /** (Re)loads config.yml and rebuilds the word matcher and settings snapshot. */
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
        for (String name : enabledLists) {
            if (!name.equals("custom") && !languageWordLists.containsKey(name)) {
                getLogger().warning("filters.enabled-word-lists contains '" + name + "' but there is no word-lists." + name + " section.");
            }
        }
        Set<String> active = buildActiveWords(enabledLists);
        activeWords = Set.copyOf(active);

        WordMatcher.Options options = new WordMatcher.Options(
            getConfig().getBoolean("filters.matching.collapse-repeated-letters", true),
            Math.max(2, getConfig().getInt("filters.matching.repeated-letter-threshold", 3)),
            getConfig().getBoolean("filters.matching.detect-spaced-letters", true),
            getConfig().getBoolean("filters.matching.join-adjacent-words", false),
            getConfig().getBoolean("filters.matching.partial-match", false),
            Math.max(3, getConfig().getInt("filters.matching.partial-min-length", 4)),
            getConfig().getStringList("filters.whitelist"));
        matcher = new WordMatcher(active, options);

        List<PunishmentAction> actions = new ArrayList<>();
        readTypedActions("punishment.effects", "effect", actions);
        readTypedActions("punishment.attributes", "attribute", actions);
        readTypedActions("punishment.sounds", "sound", actions);
        readTypedActions("punishment.commands", "command", actions);

        settings = new AntiBadWordsSettings(
            getConfig().getBoolean("punishment.enabled", true),
            Math.max(1, getConfig().getInt("punishment.violations-before-punish", 2)),
            Math.max(0, getConfig().getInt("punishment.violations-expire-minutes", 0)),
            getConfig().getBoolean("punishment.clear-violations-on-quit", true),
            List.copyOf(actions),
            getConfig().getBoolean("punishment.final-command.enabled", false),
            getConfig().getString("punishment.final-command.command", ""),
            getConfig().getBoolean("messages.broadcast-warnings", true),
            color(getConfig().getString("messages.warning-broadcast-format",
                "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.")),
            color(getConfig().getString("messages.warning-player",
                "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.")),
            color(getConfig().getString("messages.punishment-broadcast-format",
                "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C.")),
            color(getConfig().getString("messages.punishment-player",
                "&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.")),
            color(getConfig().getString("messages.punishment-disabled-player",
                "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.")),
            getConfig().getBoolean("messages.notify-staff", true),
            color(getConfig().getString("messages.staff-notify-format",
                "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7 (&f{count}&7/&f{limit}&7).")),
            getConfig().getBoolean("logging.console", true),
            getConfig().getBoolean("filters.scan.chat", true),
            getConfig().getBoolean("filters.scan.commands", true),
            getConfig().getBoolean("filters.scan.signs", true),
            getConfig().getBoolean("filters.scan.books", true),
            getConfig().getBoolean("filters.scan.anvil", true),
            lowerCaseSet(getConfig().getStringList("filters.scanned-commands")),
            lowerCaseSet(getConfig().getStringList("filters.ignored-commands")));

        long enabledCount = actions.stream().filter(PunishmentAction::enabled).count();
        getLogger().info("Loaded " + matcher.size() + " blocked words from " + enabledLists.size() + " lists.");
        getLogger().info("Loaded punishment actions: " + actions.size() + " total, " + enabledCount + " enabled.");
        getLogger().info("Protection level: " + protectionLevel + " (1=LOW, 2=MEDIUM, 3=HIGH)");
        if (settings.punishmentEnabled() && enabledCount == 0) {
            getLogger().warning("Punishment is enabled but no punishment action is enabled in config.yml.");
        }
    }

    public AntiBadWordsSettings getSettings() {
        return settings;
    }

    public WordMatcher getMatcher() {
        return matcher;
    }

    public PunishmentExecutor getPunishmentExecutor() {
        return punishmentExecutor;
    }

    public Set<String> getActiveWords() {
        return activeWords;
    }

    /** {@code true} when the player may say anything (permission {@value #PERMISSION_BYPASS}). */
    public boolean isBypassed(Player player) {
        return player.hasPermission(PERMISSION_BYPASS);
    }

    // ------------------------------------------------------------------------------------------
    // Violations
    // ------------------------------------------------------------------------------------------

    /** Adds one violation and returns the new total (old violations may have expired first). */
    public int addViolation(UUID playerId) {
        long now = System.currentTimeMillis();
        long expireMillis = settings.violationExpireMinutes() * 60_000L;
        return violations.merge(playerId, new Violation(1, now), (old, ignored) -> {
            boolean expired = expireMillis > 0 && now - old.lastMillis() > expireMillis;
            return new Violation(expired ? 1 : old.count() + 1, now);
        }).count();
    }

    public int getViolation(UUID playerId) {
        Violation violation = violations.get(playerId);
        if (violation == null) {
            return 0;
        }
        long expireMillis = settings.violationExpireMinutes() * 60_000L;
        if (expireMillis > 0 && System.currentTimeMillis() - violation.lastMillis() > expireMillis) {
            return 0;
        }
        return violation.count();
    }

    public void resetViolation(UUID playerId) {
        violations.remove(playerId);
    }

    // ------------------------------------------------------------------------------------------
    // Word lists
    // ------------------------------------------------------------------------------------------

    public boolean addCustomWord(String rawWord) {
        if (!WordMatcher.isValidEntry(rawWord)) {
            return false;
        }
        if (!customWords.add(WordMatcher.cleanEntry(rawWord))) {
            return false;
        }
        saveCustomWords();
        return true;
    }

    public boolean removeCustomWord(String rawWord) {
        if (!customWords.remove(WordMatcher.cleanEntry(rawWord))) {
            return false;
        }
        saveCustomWords();
        return true;
    }

    private void saveCustomWords() {
        List<String> snapshot = new ArrayList<>(customWords);
        getConfig().set("word-lists.custom", snapshot);
        saveConfig();
        loadPluginConfig();
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
        return getServer().getPlayerExact(name);
    }

    private void readWordList(String path, Collection<String> destination) {
        for (String word : getConfig().getStringList(path)) {
            String cleaned = WordMatcher.cleanEntry(word);
            if (WordMatcher.isValidEntry(cleaned)) {
                destination.add(cleaned);
            }
        }
    }

    /**
     * Reads {@code level-1}..{@code level-3} below {@code basePath}.
     *
     * <p>Level 3 holds the most severe words and level 1 the mildest, so a higher protection level blocks
     * <em>more</em>: LOW (1) blocks only level 3, MEDIUM (2) blocks levels 2-3, HIGH (3) blocks 1-3.
     */
    private void readTieredWordList(String basePath, Collection<String> destination, int protectionLevel) {
        // Backward compatibility: old flat list format (a plain list under the language name).
        readWordList(basePath, destination);
        int lowestIncludedTier = 4 - protectionLevel;
        for (int tier = lowestIncludedTier; tier <= 3; tier++) {
            readWordList(basePath + ".level-" + tier, destination);
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
        return result;
    }

    private static Set<String> lowerCaseSet(List<String> values) {
        return values.stream()
            .map(s -> s.toLowerCase(Locale.ROOT).trim())
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    // ------------------------------------------------------------------------------------------
    // Punishment actions
    // ------------------------------------------------------------------------------------------

    private void readTypedActions(String sectionPath, String type, List<PunishmentAction> destination) {
        ConfigurationSection section = getConfig().getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String basePath = sectionPath + "." + key;
            boolean enabled = getConfig().getBoolean(basePath + ".enabled", false);
            List<String> args = switch (type) {
                case "effect" -> structuredArgs(basePath, "effect-name", "amplifier", "duration");
                case "attribute" -> structuredArgs(basePath, "attribute-name", "value", "duration");
                case "sound" -> structuredArgs(basePath, "sound-name", "volume", "pitch");
                default -> {
                    String command = getConfig().getString(basePath + ".command", "").trim();
                    yield command.isEmpty() ? legacyArgs(basePath, 1) : List.of(command);
                }
            };
            if (args.isEmpty()) {
                if (enabled) {
                    getLogger().warning(sectionPath + "." + key + " is enabled but incomplete; it will be ignored.");
                }
                continue;
            }
            destination.add(new PunishmentAction(enabled, type, key, List.copyOf(args)));
        }
    }

    /** Reads the structured keys, or falls back to the legacy single-line {@code format:} key. */
    private List<String> structuredArgs(String basePath, String first, String second, String third) {
        String a = getConfig().getString(basePath + "." + first, "").trim();
        Object bRaw = getConfig().get(basePath + "." + second);
        Object cRaw = getConfig().get(basePath + "." + third);
        if (!a.isEmpty() && bRaw != null && cRaw != null) {
            return List.of(a, String.valueOf(bRaw).trim(), String.valueOf(cRaw).trim());
        }
        return legacyArgs(basePath, 3);
    }

    /**
     * Legacy format {@code name-value-value}. The last two {@code -} separated parts are taken from the
     * right, so names and values may themselves contain dashes.
     */
    private List<String> legacyArgs(String basePath, int expectedParts) {
        String format = getConfig().getString(basePath + ".format", "").trim();
        if (format.isEmpty()) {
            return List.of();
        }
        if (expectedParts == 1) {
            return List.of(format);
        }
        int last = format.lastIndexOf('-');
        int secondLast = last <= 0 ? -1 : format.lastIndexOf('-', last - 1);
        if (last < 0 || secondLast < 0) {
            return List.of();
        }
        return List.of(format.substring(0, secondLast).trim(),
            format.substring(secondLast + 1, last).trim(),
            format.substring(last + 1).trim());
    }

    // ------------------------------------------------------------------------------------------
    // Startup banner
    // ------------------------------------------------------------------------------------------

    private void logStartupBanner() {
        String version = getPluginMeta().getVersion();
        String line = "==============================================";
        String gameVersion = getServer().getMinecraftVersion();
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
