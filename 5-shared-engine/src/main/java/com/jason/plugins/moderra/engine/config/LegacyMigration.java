package com.jason.plugins.moderra.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns an old single-file {@code config.yml} (everything in one file, no {@code config-version: 2}) into the new
 * config folder, without changing how the server behaves:
 * <ul>
 *   <li>the old file is copied to {@code config.yml.bak-<date>} first and is never deleted,</li>
 *   <li>every old value is written into the new commented files (only values that differ from the default are touched),</li>
 *   <li>the old {@code word-lists:} become {@code wordlists/<code>.yml} files with exactly the old words, and exactly
 *       the same languages stay switched on,</li>
 *   <li>custom words go to {@code wordlists/custom.yml}; the whitelist stays a list in {@code config.yml}.</li>
 * </ul>
 * The new {@code config.yml} is written last, so a crash in the middle simply runs the migration again next time.
 */
public final class LegacyMigration {
    /**
     * @param migrated true when a migration was done now
     * @param backup   the backup file, or {@code null}
     * @param log      what was done, line by line (already sent to the logger)
     */
    public record Result(boolean migrated, Path backup, List<String> log) {
    }

    /**
     * Settings whose default is different now than in version 1. A migrated install that did not set them gets the old
     * default written into its new file ("behaves the same after the update").
     */
    private static final Map<String, Object> LEGACY_DEFAULTS = Map.of(
        CoreSettings.SCANNED_COMMANDS.key(), CoreSettings.LEGACY_SCANNED_COMMANDS,
        CoreSettings.WHITELIST.key(), CoreSettings.LEGACY_WHITELIST,
        com.jason.plugins.moderra.engine.filters.LinkSettings.SOURCES.key(),
        com.jason.plugins.moderra.engine.filters.LinkSettings.LEGACY_SOURCES,
        com.jason.plugins.moderra.engine.filters.EvasionSettings.DETECT_SOURCES.key(),
        com.jason.plugins.moderra.engine.filters.EvasionSettings.LEGACY_DETECT_SOURCES);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private LegacyMigration() {
    }

    /** True when the text is a config.yml of the new folder layout. */
    public static boolean isNewLayout(YamlTree config) {
        return config.get(List.of(CoreSettings.CONFIG_VERSION.key())) instanceof Number n && n.intValue() >= 2;
    }

    /**
     * Migrates when an old config.yml is found. Never throws; problems are reported through {@code warn}.
     *
     * @param nowMillis used for the name of the backup file
     */
    public static Result migrateIfNeeded(Path folder, SettingsRegistry registry, long nowMillis,
                                         java.util.function.Consumer<String> info, java.util.function.Consumer<String> warn) {
        Path configFile = folder.resolve(CoreSettings.CONFIG);
        if (Files.notExists(configFile)) {
            return new Result(false, null, List.of());
        }
        YamlTree old;
        String oldText;
        try {
            oldText = Files.readString(configFile, StandardCharsets.UTF_8);
            old = YamlTree.parse(oldText);
        } catch (IOException | RuntimeException ex) {
            // The normal loader reports the unreadable file; an old file is migrated once it can be read again.
            return new Result(false, null, List.of());
        }
        if (isNewLayout(old) || old.root().isEmpty()) {
            // new layout, or an empty file: nothing to carry over (missing keys simply use their defaults)
            return new Result(false, null, List.of());
        }
        List<String> log = new ArrayList<>();
        try {
            Path backup = migrate(folder, registry, old, oldText, nowMillis, log, warn);
            log.forEach(info);
            return new Result(true, backup, log);
        } catch (IOException | RuntimeException ex) {
            warn.accept("Could not migrate the old config.yml (" + ex + "). The old file was not changed; built-in defaults are used "
                + "until this is fixed.");
            return new Result(false, null, log);
        }
    }

    private static Path migrate(Path folder, SettingsRegistry registry, YamlTree old, String oldText, long nowMillis, List<String> log,
                                java.util.function.Consumer<String> warn) throws IOException {
        log.add("Old single-file config.yml found: moving its settings into the new config folder.");

        // ---- 1. settings: same key paths as before, so values are copied -------------------------------
        Map<String, YamlEditor> editors = new LinkedHashMap<>();
        YamlEditor config = editor(editors, registry, CoreSettings.CONFIG);
        int changed = 0;
        int same = 0;
        for (Setting<?> setting : registry.settings()) {
            if (setting == CoreSettings.CONFIG_VERSION || setting == CoreSettings.ENABLED_LANGUAGES) {
                continue;
            }
            Object raw = old.get(setting.key());
            if (raw == null) {
                // A default that changed since version 1: write the old default, so the server behaves as before.
                Object legacy = LEGACY_DEFAULTS.get(setting.key());
                if (legacy != null && !legacy.equals(setting.defaultValue())) {
                    editor(editors, registry, setting.file()).set(setting.key(), legacy);
                    changed++;
                }
                continue;
            }
            Setting.Parsed<?> parsed = setting.parse(raw);
            if (parsed.problem() != null) {
                warn.accept("Old config.yml -> " + setting.key() + ": " + parsed.problem());
            }
            if (Objects.equals(parsed.value(), setting.defaultValue())) {
                same++;
                continue;
            }
            editor(editors, registry, setting.file()).set(setting.key(), yamlValue(setting, parsed.value()));
            changed++;
        }
        for (Section section : List.of(CoreSettings.EFFECTS, CoreSettings.ATTRIBUTES, CoreSettings.SOUNDS, CoreSettings.COMMANDS)) {
            Map<String, Object> oldSection = old.getMap(section.key());
            Map<String, Object> def = ConfigStore.defaultSection(section);
            if (oldSection.equals(def) && new ArrayList<>(oldSection.keySet()).equals(new ArrayList<>(def.keySet()))) {
                same++;
            } else {
                // also when the old file had no such section: the old plugin then ran no action of this kind
                editor(editors, registry, section.file()).set(section.key(), oldSection);
                changed++;
            }
        }
        log.add("Settings: " + changed + " differed from the defaults and were copied, " + same + " were already the default.");

        // ---- 2. word lists ----------------------------------------------------------------------------
        String stamp = STAMP.format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()));
        Path backup = folder.resolve("config.yml.bak-" + stamp);
        for (int n = 2; Files.exists(backup); n++) {
            backup = folder.resolve("config.yml.bak-" + stamp + "-" + n);
        }
        String backupName = backup.getFileName().toString();

        Map<String, String> files = new LinkedHashMap<>(); // relative path -> text
        Map<String, String> codeOf = new LinkedHashMap<>(); // old list name (lower-case) -> new code
        List<String> listSummary = new ArrayList<>();
        Set<String> usedCodes = new LinkedHashSet<>();
        for (String name : old.keys("word-lists")) {
            if (name.equalsIgnoreCase(WordLists.CUSTOM)) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            String code = WordLists.legacyCode(key);
            if (code == null) {
                code = key.replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
                if (code.isEmpty() || code.equals(WordLists.CUSTOM)) {
                    code = "list";
                }
            }
            String unique = code;
            for (int n = 2; !usedCodes.add(unique); n++) {
                unique = code + "-" + n;
            }
            codeOf.put(key, unique);

            Map<Integer, List<String>> tiers = new LinkedHashMap<>();
            Object value = old.get(List.of("word-lists", name));
            if (value instanceof List<?> flat) {
                tiers.put(3, strings(flat)); // old flat lists were active at every protection level, like level-3
            } else if (value instanceof Map<?, ?> map) {
                for (int tier = 1; tier <= 3; tier++) {
                    if (map.get("level-" + tier) instanceof List<?> list) {
                        tiers.put(tier, strings(list));
                    }
                }
            }
            int count = tiers.values().stream().mapToInt(List::size).sum();
            String bundled = Resources.bundledWordList(unique);
            YamlTree bundledYaml = bundled == null ? YamlTree.parse("") : YamlTree.parse(bundled);
            files.put(WordLists.FOLDER + "/" + unique + ".yml", WordList.render(unique, bundledYaml.getString("name", name),
                bundledYaml.getString("script", "latin"),
                "Your own list '" + name + "' from the word-lists section of the old config.yml (backup: " + backupName + ")",
                "Kept exactly as it was so the filter behaves as before the update. "
                    + (bundled == null ? "" : "To switch to the bigger built-in list for this language, delete this file and run /moderra reload."),
                tiers));
            listSummary.add(name + " -> " + WordLists.FOLDER + "/" + unique + ".yml (" + count + " words)");
        }
        List<String> customWords = old.getStringList("word-lists.custom");
        String customText = Resources.bundledWordList(WordLists.CUSTOM);
        if (customText == null) {
            customText = WordList.render(WordLists.CUSTOM, "Custom words (the admin's own list)", "latin", "Written by the server admin",
                "Put the words your community needs here.", Map.of());
        }
        if (!customWords.isEmpty()) {
            // level-3 = active at every protection level, exactly like the old flat custom list
            customText = YamlEditor.of(customText).set("level-3", customWords).text();
        }
        files.put(WordLists.FOLDER + "/" + WordLists.CUSTOM + ".yml", customText);
        log.add("Word lists written from the old config: " + (listSummary.isEmpty() ? "none" : String.join(", ", listSummary)) + ".");
        log.add("Custom words: " + customWords.size() + " -> " + WordLists.FOLDER + "/custom.yml. Whitelist entries: "
            + old.getStringList(CoreSettings.WHITELIST.key()).size() + " (stay in config.yml).");

        // ---- 3. exactly the same languages stay on ----------------------------------------------------
        List<String> oldEnabled = new ArrayList<>();
        for (String name : old.getStringList(CoreSettings.ENABLED_LANGUAGES.key())) {
            String key = name.toLowerCase(Locale.ROOT).trim();
            if (!key.isBlank() && !oldEnabled.contains(key)) {
                oldEnabled.add(key);
            }
        }
        if (oldEnabled.isEmpty()) {
            oldEnabled.addAll(codeOf.keySet()); // the old plugin switched every list on when none was named
        }
        List<String> enabled = new ArrayList<>();
        for (String name : codeOf.keySet()) { // the old plugin used the lists in the order of the word-lists section
            if (oldEnabled.contains(name)) {
                enabled.add(codeOf.get(name));
            }
        }
        for (String name : oldEnabled) {
            if (!codeOf.containsKey(name) && !name.equals(WordLists.CUSTOM)) {
                enabled.add(name); // named but never defined: still reported as missing, like before
            }
        }
        enabled.add(WordLists.CUSTOM);
        config.set(CoreSettings.ENABLED_LANGUAGES.key(), enabled);
        log.add("Languages switched on (same as before): " + String.join(", ", enabled) + ".");

        // ---- 4. things we do not know ------------------------------------------------------------------
        List<String> unknown = new ArrayList<>();
        collectUnknown("", old.root(), registry, unknown);
        if (!unknown.isEmpty()) {
            warn.accept("Old config.yml keys that are not known and were not copied (they are still in " + backupName + "): "
                + String.join(", ", unknown));
        }

        // ---- 5. write: backup first, new config.yml last ----------------------------------------------
        Files.createDirectories(folder);
        Files.writeString(backup, oldText, StandardCharsets.UTF_8);
        log.add("Backup of the old file: " + backupName + " (the old file is never deleted).");
        for (Map.Entry<String, String> file : files.entrySet()) {
            YamlEditor.writeAtomically(folder.resolve(file.getKey()), file.getValue());
        }
        for (Map.Entry<String, YamlEditor> e : editors.entrySet()) {
            if (!e.getKey().equals(CoreSettings.CONFIG)) {
                e.getValue().save(folder.resolve(e.getKey()));
            }
        }
        config.save(folder.resolve(CoreSettings.CONFIG));
        log.add("Migration finished. New files: " + String.join(", ", editors.keySet()) + ", " + WordLists.FOLDER + "/*.yml.");
        return backup;
    }

    private static YamlEditor editor(Map<String, YamlEditor> editors, SettingsRegistry registry, String file) {
        return editors.computeIfAbsent(file, f -> YamlEditor.of(DefaultFiles.render(registry, f)));
    }

    @SuppressWarnings("unchecked")
    private static <T> Object yamlValue(Setting<T> setting, Object value) {
        return setting.toYaml((T) value);
    }

    private static List<String> strings(List<?> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !(o instanceof Map<?, ?>) && !(o instanceof List<?>)) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private static void collectUnknown(String prefix, Map<String, Object> map, SettingsRegistry registry, List<String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (key.equals("word-lists") || registry.entry(key) != null) {
                continue;
            }
            String childPrefix = key + ".";
            boolean parent = registry.entries().stream().anyMatch(x -> x.key().startsWith(childPrefix));
            if (parent && e.getValue() instanceof Map<?, ?> child) {
                collectUnknown(key, YamlTree.stringKeys(child), registry, out);
            } else {
                out.add(key);
            }
        }
    }
}
