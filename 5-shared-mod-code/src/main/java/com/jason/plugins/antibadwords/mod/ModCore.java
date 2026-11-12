package com.jason.plugins.antibadwords.mod;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.PunishmentAction;
import com.jason.plugins.antibadwords.WordMatcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loader-independent brain of the Fabric and Forge mods. A line-by-line port of what the Bukkit plugin does in
 * {@code AntiBadWordsPlugin}, {@code PunishmentParser} and the decision parts of {@code AntiBadWordsListener},
 * reading the very same {@code config.yml}. It only needs the JDK and SnakeYAML, so it is unit tested without
 * Minecraft; the loader specific classes feed it text and carry out what it decides.
 */
public final class ModCore {
    public static final String PERMISSION_BYPASS = "antibadwords.bypass";
    public static final String PERMISSION_ADMIN = "antibadwords.admin";
    public static final String PERMISSION_NOTIFY = "antibadwords.notify";

    /** name-value-value, where the value may be negative: {@code minecraft:max_health--2.5-10}. */
    private static final Pattern LEGACY = Pattern.compile("(.+?)\\s*-\\s*(-?\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)");

    public interface Log {
        void info(String message);

        void warn(String message);
    }

    private record Violation(int count, long lastMillis) {
    }

    /** What to do after a blocked word. Messages are fully formatted; {@code null} means "don't send". */
    public record Outcome(boolean punish, Map<String, String> values, String staffMessage, String logLine,
                          String broadcast, String playerMessage, List<PunishmentAction> actions) {
    }

    /** Messages that follow once the punishment actions ran. */
    public record Punished(String broadcast, String playerMessage, String finalCommand) {
    }

    private final Path configFile;
    private final Log log;
    private final String version;
    private final LongSupplier clock;
    private final ConcurrentMap<UUID, Violation> violations = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> languageWordLists = new LinkedHashMap<>();
    private final Set<String> customWords = new LinkedHashSet<>();
    private volatile WordMatcher matcher = new WordMatcher(List.of(), WordMatcher.Options.defaults());
    private volatile Set<String> activeWords = Set.of();
    private volatile AntiBadWordsSettings settings;
    private volatile int protectionLevel = 3;

    public ModCore(Path configDir, String version, Log log) {
        this(configDir, version, log, System::currentTimeMillis);
    }

    ModCore(Path configDir, String version, Log log, LongSupplier clock) {
        this.configFile = configDir.resolve("config.yml");
        this.version = version;
        this.log = log;
        this.clock = clock;
        reload();
    }

    public String version() {
        return version;
    }

    public AntiBadWordsSettings settings() {
        return settings;
    }

    public WordMatcher matcher() {
        return matcher;
    }

    // ------------------------------------------------------------------------------------------
    // Configuration (same rules as AntiBadWordsPlugin.loadPluginConfig)
    // ------------------------------------------------------------------------------------------

    /** (Re)loads config.yml, creating it from the bundled default on first start. */
    public synchronized void reload() {
        try {
            if (Files.notExists(configFile)) {
                Files.createDirectories(configFile.getParent());
                try (InputStream in = ModCore.class.getResourceAsStream("/config.yml")) {
                    if (in == null) {
                        throw new IOException("bundled config.yml is missing");
                    }
                    Files.write(configFile, in.readAllBytes());
                }
            }
            YamlConfig config;
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                config = new YamlConfig(reader);
            }
            apply(config);
        } catch (Exception ex) {
            log.warn("Could not read config.yml (" + ex + "); " + (settings == null ? "using built-in defaults." : "keeping the previous settings."));
            if (settings == null) {
                apply(new YamlConfig(new java.io.StringReader("")));
            }
        }
    }

    private void apply(YamlConfig config) {
        languageWordLists.clear();
        customWords.clear();

        protectionLevel = Math.max(1, Math.min(3, config.getInt("filters.protection-level", 3)));

        for (String key : config.keys("word-lists")) {
            if (key.equalsIgnoreCase("custom")) {
                continue;
            }
            Set<String> words = new LinkedHashSet<>();
            readTieredWordList(config, "word-lists." + key, words, protectionLevel);
            languageWordLists.put(key.toLowerCase(Locale.ROOT), words);
        }
        readWordList(config, "word-lists.custom", customWords);

        Set<String> enabledLists = config.getStringList("filters.enabled-word-lists").stream()
            .map(s -> s.toLowerCase(Locale.ROOT).trim())
            .filter(s -> !s.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (enabledLists.isEmpty()) {
            enabledLists.addAll(languageWordLists.keySet());
        }
        enabledLists.add("custom");
        for (String name : enabledLists) {
            if (!name.equals("custom") && !languageWordLists.containsKey(name)) {
                log.warn("filters.enabled-word-lists contains '" + name + "' but there is no word-lists." + name + " section.");
            }
        }
        Set<String> active = buildActiveWords(enabledLists);
        activeWords = Set.copyOf(active);

        WordMatcher.Options options = new WordMatcher.Options(
            config.getBoolean("filters.matching.collapse-repeated-letters", true),
            Math.max(2, config.getInt("filters.matching.repeated-letter-threshold", 3)),
            config.getBoolean("filters.matching.detect-spaced-letters", true),
            config.getBoolean("filters.matching.join-adjacent-words", false),
            config.getBoolean("filters.matching.partial-match", false),
            Math.max(3, config.getInt("filters.matching.partial-min-length", 4)),
            config.getStringList("filters.whitelist"));
        matcher = new WordMatcher(active, options);

        List<PunishmentAction> actions = parsePunishments(config);

        settings = new AntiBadWordsSettings(
            config.getBoolean("punishment.enabled", true),
            Math.max(1, config.getInt("punishment.violations-before-punish", 2)),
            Math.max(0, config.getInt("punishment.violations-expire-minutes", 0)),
            config.getBoolean("punishment.clear-violations-on-quit", true),
            List.copyOf(actions),
            config.getBoolean("punishment.final-command.enabled", false),
            config.getString("punishment.final-command.command", ""),
            config.getBoolean("messages.broadcast-warnings", true),
            Names.colour(config.getString("messages.warning-broadcast-format",
                "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.")),
            Names.colour(config.getString("messages.warning-player",
                "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.")),
            Names.colour(config.getString("messages.punishment-broadcast-format",
                "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C.")),
            Names.colour(config.getString("messages.punishment-player",
                "&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.")),
            Names.colour(config.getString("messages.punishment-disabled-player",
                "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.")),
            config.getBoolean("messages.notify-staff", true),
            Names.colour(config.getString("messages.staff-notify-format",
                "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7 (&f{count}&7/&f{limit}&7).")),
            config.getBoolean("logging.console", true),
            config.getBoolean("filters.scan.chat", true),
            config.getBoolean("filters.scan.commands", true),
            config.getBoolean("filters.scan.signs", true),
            config.getBoolean("filters.scan.books", true),
            config.getBoolean("filters.scan.anvil", true),
            lowerCaseSet(config.getStringList("filters.scanned-commands")),
            lowerCaseSet(config.getStringList("filters.ignored-commands")));

        long enabledCount = actions.stream().filter(PunishmentAction::enabled).count();
        log.info("Loaded " + matcher.size() + " blocked words from " + enabledLists.size() + " lists.");
        log.info("Loaded punishment actions: " + actions.size() + " total, " + enabledCount + " enabled.");
        log.info("Protection level: " + protectionLevel + " (1=LOW, 2=MEDIUM, 3=HIGH)");
        if (settings.punishmentEnabled() && enabledCount == 0) {
            log.warn("Punishment is enabled but no punishment action is enabled in config.yml.");
        }
    }

    private static void readWordList(YamlConfig config, String path, Collection<String> destination) {
        for (String word : config.getStringList(path)) {
            String cleaned = WordMatcher.cleanEntry(word);
            if (WordMatcher.isValidEntry(cleaned)) {
                destination.add(cleaned);
            }
        }
    }

    /** Level 3 = most severe: LOW (1) blocks only level 3, MEDIUM (2) levels 2-3, HIGH (3) levels 1-3. */
    private static void readTieredWordList(YamlConfig config, String basePath, Collection<String> destination, int level) {
        readWordList(config, basePath, destination); // old flat list format
        for (int tier = 4 - level; tier <= 3; tier++) {
            readWordList(config, basePath + ".level-" + tier, destination);
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
        return values.stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------------------------------------
    // Punishment config (port of PunishmentParser)
    // ------------------------------------------------------------------------------------------

    private List<PunishmentAction> parsePunishments(YamlConfig config) {
        List<PunishmentAction> actions = new ArrayList<>();
        readTypedActions(config, "punishment.effects", "effect", actions);
        readTypedActions(config, "punishment.attributes", "attribute", actions);
        readTypedActions(config, "punishment.sounds", "sound", actions);
        readTypedActions(config, "punishment.commands", "command", actions);
        return actions;
    }

    private void readTypedActions(YamlConfig config, String sectionPath, String type, List<PunishmentAction> destination) {
        for (String key : config.keys(sectionPath)) {
            String basePath = sectionPath + "." + key;
            boolean enabled = config.getBoolean(basePath + ".enabled", false);
            List<String> args = switch (type) {
                case "effect" -> structuredArgs(config, basePath, "effect-name", "amplifier", "duration");
                case "attribute" -> structuredArgs(config, basePath, "attribute-name", "value", "duration");
                case "sound" -> structuredArgs(config, basePath, "sound-name", "volume", "pitch");
                default -> {
                    String command = config.getString(basePath + ".command", "").trim();
                    yield command.isEmpty() ? legacyArgs(config, basePath, 1) : List.of(command);
                }
            };
            if (args.isEmpty()) {
                if (enabled) {
                    log.warn(sectionPath + "." + key + " is enabled but incomplete; it will be ignored.");
                }
                continue;
            }
            destination.add(new PunishmentAction(enabled, type, key, List.copyOf(args)));
        }
    }

    private static List<String> structuredArgs(YamlConfig config, String basePath, String first, String second, String third) {
        String a = config.getString(basePath + "." + first, "").trim();
        Object bRaw = config.get(basePath + "." + second);
        Object cRaw = config.get(basePath + "." + third);
        if (!a.isEmpty() && bRaw != null && cRaw != null) {
            return List.of(a, String.valueOf(bRaw).trim(), String.valueOf(cRaw).trim());
        }
        return legacyArgs(config, basePath, 3);
    }

    private static List<String> legacyArgs(YamlConfig config, String basePath, int expectedParts) {
        String format = config.getString(basePath + ".format", "").trim();
        if (format.isEmpty()) {
            return List.of();
        }
        if (expectedParts == 1) {
            return List.of(format);
        }
        Matcher m = LEGACY.matcher(format);
        if (!m.matches()) {
            return List.of();
        }
        return List.of(m.group(1).trim(), m.group(2), m.group(3));
    }

    // ------------------------------------------------------------------------------------------
    // Decisions
    // ------------------------------------------------------------------------------------------

    /**
     * For a command line such as {@code /msg Bob hello}: the text after the label that must be scanned, or
     * {@code null} when the command is not scanned (same rules as the plugin's PlayerCommandPreprocessEvent handler).
     */
    public String commandTextToScan(String message) {
        AntiBadWordsSettings s = settings;
        if (!s.scanCommands()) {
            return null;
        }
        int space = message.indexOf(' ');
        if (space < 0) {
            return null; // a bare command has no arguments that could contain a bad word
        }
        String label = Names.commandLabel(message.substring(0, space));
        if (s.ignoredCommands().contains(label)) {
            return null;
        }
        if (!s.scannedCommands().contains("*") && !s.scannedCommands().contains(label)) {
            return null;
        }
        return message.substring(space + 1);
    }

    /** First blocked word in {@code text}, or {@code null}. */
    public String find(String text) {
        return matcher.find(text);
    }

    /** Records the violation and decides what to say/do (port of AntiBadWordsListener.handleViolation). */
    public Outcome handleViolation(UUID playerId, String playerName, String word, String source) {
        AntiBadWordsSettings s = settings;
        int count = addViolation(playerId);
        int limit = s.violationsBeforePunish();
        int remaining = Math.max(0, limit - count);
        boolean punish = count >= limit && s.punishmentEnabled();

        Map<String, String> values = new HashMap<>();
        values.put("player", playerName);
        values.put("word", word);
        values.put("source", source);
        values.put("count", Integer.toString(count));
        values.put("limit", Integer.toString(limit));
        values.put("remaining", Integer.toString(remaining));

        String staff = s.notifyStaff() ? Placeholders.apply(s.staffNotifyFormat(), values) : null;
        String logLine = s.logToConsole()
            ? playerName + " said blocked word '" + word + "' in " + source + " (violation " + count + "/" + limit + ")"
            : null;

        if (punish) {
            resetViolation(playerId);
            List<PunishmentAction> enabled = s.punishmentActions().stream().filter(PunishmentAction::enabled).toList();
            return new Outcome(true, values, staff, logLine, null, null, enabled);
        }
        String broadcast = s.broadcastWarnings() ? Placeholders.apply(s.warningBroadcastMessage(), values) : null;
        String player = count >= limit
            ? Placeholders.apply(s.punishmentDisabledMessage(), values)
            : Placeholders.apply(s.warningPlayerMessage(), values);
        return new Outcome(false, values, staff, logLine, broadcast, player, List.of());
    }

    /** Builds the messages that follow the punishment, given what the actions reported as applied. */
    public Punished finishPunishment(Outcome outcome, List<String> applied) {
        AntiBadWordsSettings s = settings;
        Map<String, String> values = new HashMap<>(outcome.values());
        values.put("remaining", "0");
        values.put("punishments", Names.formatPunishmentList(applied));
        values.put("punishment", values.get("punishments"));
        String command = null;
        if (s.finalCommandEnabled() && !s.finalCommand().isBlank()) {
            command = Placeholders.apply(s.finalCommand(), values);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
        }
        return new Punished(Placeholders.apply(s.punishmentBroadcastMessage(), values),
            Placeholders.apply(s.punishmentPlayerMessage(), values), command);
    }

    // ------------------------------------------------------------------------------------------
    // Violations
    // ------------------------------------------------------------------------------------------

    public int addViolation(UUID playerId) {
        long now = clock.getAsLong();
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
        if (expireMillis > 0 && clock.getAsLong() - violation.lastMillis() > expireMillis) {
            return 0;
        }
        return violation.count();
    }

    public void resetViolation(UUID playerId) {
        violations.remove(playerId);
    }

    public void clearAllViolations() {
        violations.clear();
    }

    // ------------------------------------------------------------------------------------------
    // Word lists (custom words are written back into config.yml, keeping its comments)
    // ------------------------------------------------------------------------------------------

    public synchronized boolean addCustomWord(String rawWord) {
        if (!WordMatcher.isValidEntry(rawWord)) {
            return false;
        }
        if (!customWords.add(WordMatcher.cleanEntry(rawWord))) {
            return false;
        }
        saveCustomWords();
        return true;
    }

    public synchronized boolean removeCustomWord(String rawWord) {
        if (!customWords.remove(WordMatcher.cleanEntry(rawWord))) {
            return false;
        }
        saveCustomWords();
        return true;
    }

    private void saveCustomWords() {
        try {
            String text = Files.readString(configFile, StandardCharsets.UTF_8);
            StringBuilder block = new StringBuilder("  custom:");
            if (customWords.isEmpty()) {
                block.append(" []\n");
            } else {
                block.append('\n');
                for (String word : customWords) {
                    block.append("    - \"").append(word.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
                }
            }
            // "  custom: []" or "  custom:" followed by "    - item" lines, directly below word-lists.
            Pattern section = Pattern.compile("(?m)^  custom:[^\\n]*\\n(?:    - [^\\n]*\\n)*");
            Matcher m = section.matcher(text.endsWith("\n") ? text : text + "\n");
            String updated;
            if (m.find()) {
                String normalized = text.endsWith("\n") ? text : text + "\n";
                updated = normalized.substring(0, m.start()) + block + normalized.substring(m.end());
            } else {
                updated = (text.endsWith("\n") ? text : text + "\n") + block;
            }
            Files.writeString(configFile, updated, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Could not save the custom word list: " + ex);
        }
        reload();
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
}
