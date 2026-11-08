package com.jason.plugins.antibadwords.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.WordMatcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Loader-independent brain of the Fabric and Forge mods: configuration, word matching, strike counting
 * and message formatting. It only uses the JDK and Gson, so it is unit tested without Minecraft.
 * The loader specific classes just feed it text and carry out what it decides.
 */
public final class ModCore {
    public static final String FILE_NAME = "antibadwords.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** What to do after a bad word was found. */
    public record Verdict(String word, int count, int limit, boolean punish, List<String> commands, String finalCommand) {
    }

    private final Path configFile;
    private final Consumer<String> log;
    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();
    private volatile ModConfig config;
    private volatile WordMatcher matcher;

    public ModCore(Path configDir, Consumer<String> log) {
        this.configFile = configDir.resolve(FILE_NAME);
        this.log = log;
        reload();
    }

    /** (Re)reads the config file, creating it from the bundled defaults on first start. */
    public synchronized void reload() {
        try {
            if (Files.notExists(configFile)) {
                Files.createDirectories(configFile.getParent());
                try (InputStream in = ModCore.class.getResourceAsStream("/antibadwords-default.json")) {
                    if (in == null) {
                        throw new IOException("bundled antibadwords-default.json is missing");
                    }
                    Files.write(configFile, in.readAllBytes());
                }
            }
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                apply(loaded == null ? new ModConfig() : loaded);
            }
        } catch (Exception ex) {
            log.accept("Could not read " + FILE_NAME + " (" + ex + "); keeping the previous settings.");
            if (config == null) {
                apply(new ModConfig());
            }
        }
    }

    private void apply(ModConfig loaded) {
        int level = Math.max(1, Math.min(3, loaded.protectionLevel));
        Set<String> enabled = new LinkedHashSet<>();
        for (String name : loaded.enabledWordLists) {
            enabled.add(name.toLowerCase(Locale.ROOT).trim());
        }
        if (enabled.isEmpty()) {
            for (String name : loaded.wordLists.keySet()) {
                enabled.add(name.toLowerCase(Locale.ROOT));
            }
        }
        List<String> words = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> list : loaded.wordLists.entrySet()) {
            if (!enabled.contains(list.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            // Same rule as the plugin: level 3 = most severe, so LOW (1) keeps only tier 3, HIGH (3) keeps 1-3.
            for (int i = 4 - level; i <= 3; i++) {
                List<String> tier = list.getValue().get("level-" + i);
                if (tier != null) {
                    words.addAll(tier);
                }
            }
        }
        words.addAll(loaded.customWords);

        ModConfig.Matching m = loaded.matching;
        WordMatcher.Options options = new WordMatcher.Options(m.collapseRepeatedLetters,
            Math.max(2, m.repeatedLetterThreshold), m.detectSpacedLetters, m.joinAdjacentWords,
            m.partialMatch, Math.max(2, m.partialMinLength), loaded.whitelist);
        this.matcher = new WordMatcher(words, options);
        this.config = loaded;
        log.accept("Loaded " + matcher.size() + " blocked words (protection level " + level + ").");
    }

    public ModConfig config() {
        return config;
    }

    public int wordCount() {
        return matcher.size();
    }

    /** Returns the blocked word found in {@code text}, or {@code null}. */
    public String find(String text) {
        return matcher.find(text);
    }

    /**
     * Records a violation for {@code player} if {@code text} contains a blocked word.
     *
     * @return {@code null} when the text is clean
     */
    public Verdict check(UUID player, String text) {
        String word = matcher.find(text);
        if (word == null) {
            return null;
        }
        ModConfig.Punishment p = config.punishment;
        int limit = Math.max(1, p.violationsBeforePunish);
        int count = violations.merge(player, 1, Integer::sum);
        boolean punish = p.enabled && count >= limit;
        List<String> commands = new ArrayList<>();
        if (punish) {
            violations.put(player, 0);
            for (ModConfig.Action action : p.commands) {
                if (action.enabled && !action.command.isBlank()) {
                    commands.add(action.command);
                }
            }
        }
        String fin = punish && p.finalCommandEnabled && !p.finalCommand.isBlank() ? p.finalCommand : "";
        return new Verdict(word, count, limit, punish, commands, fin);
    }

    public int violations(UUID player) {
        return violations.getOrDefault(player, 0);
    }

    public void resetViolations(UUID player) {
        violations.remove(player);
    }

    public void onQuit(UUID player) {
        if (config.punishment.clearViolationsOnQuit) {
            violations.remove(player);
        }
    }

    public boolean addCustomWord(String raw) {
        String word = WordMatcher.cleanEntry(raw);
        if (!WordMatcher.isValidEntry(word) || config.customWords.contains(word)) {
            return false;
        }
        config.customWords.add(word);
        save();
        return true;
    }

    public boolean removeCustomWord(String raw) {
        if (!config.customWords.remove(WordMatcher.cleanEntry(raw))) {
            return false;
        }
        save();
        return true;
    }

    private synchronized void save() {
        try (var writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException ex) {
            log.accept("Could not save " + FILE_NAME + ": " + ex);
        }
        apply(config);
    }

    // ---- message helpers ---------------------------------------------------------------------

    /** Fills {@code {placeholders}} and turns {@code &c} colour codes into {@code §c}. */
    public static String format(String template, Map<String, String> values) {
        return colour(Placeholders.apply(template, values));
    }

    public static String colour(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(i + 1)) >= 0) {
                out.append('§');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static Map<String, String> values(String player, Verdict verdict, String source) {
        return Map.of(
            "player", player,
            "word", verdict.word(),
            "source", source,
            "count", Integer.toString(verdict.count()),
            "limit", Integer.toString(verdict.limit()),
            "remaining", Integer.toString(Math.max(0, verdict.limit() - verdict.count())));
    }
}
