package com.jason.plugins.antibadwords.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads and writes the config folder, driven by the {@link SettingsRegistry}: creates missing files with their
 * commented defaults, reads and checks every registered value, warns about unknown keys, and edits single values while
 * keeping comments ({@link YamlEditor}).
 */
public final class ConfigStore {
    /**
     * Everything read from the files at one moment.
     *
     * @param values   setting key -> checked value (every registered setting is present)
     * @param sections section key -> raw content (every registered section is present)
     * @param problems what was wrong; the affected values are defaults or the previous good ones
     */
    public record Snapshot(Map<String, Object> values, Map<String, Map<String, Object>> sections, List<ConfigProblem> problems) {
        @SuppressWarnings("unchecked")
        public <T> T get(Setting<T> setting) {
            Object value = values.get(setting.key());
            return value == null ? setting.defaultValue() : (T) value;
        }

        public Map<String, Object> section(Section section) {
            return sections.getOrDefault(section.key(), Map.of());
        }
    }

    private final Path folder;
    private final SettingsRegistry registry;

    public ConfigStore(Path folder, SettingsRegistry registry) {
        this.folder = folder;
        this.registry = registry;
    }

    public Path folder() {
        return folder;
    }

    public Path file(String relative) {
        return folder.resolve(relative);
    }

    /** Creates every registered file that does not exist yet. @return the files that were created */
    public List<String> writeMissingDefaults() throws IOException {
        List<String> created = new ArrayList<>();
        for (String file : registry.files()) {
            Path path = file(file);
            if (Files.notExists(path)) {
                YamlEditor.writeAtomically(path, DefaultFiles.render(registry, file));
                created.add(file);
            }
        }
        return created;
    }

    /**
     * Reads all files. Never throws.
     *
     * @param previous the last good snapshot, used for files that cannot be read now; may be {@code null}
     */
    public Snapshot load(Snapshot previous) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
        List<ConfigProblem> problems = new ArrayList<>();
        for (String file : registry.files()) {
            YamlTree tree = null;
            try {
                Path path = file(file);
                tree = Files.exists(path) ? YamlTree.parse(Files.readString(path, StandardCharsets.UTF_8)) : YamlTree.parse("");
            } catch (IOException | RuntimeException ex) {
                problems.add(new ConfigProblem(file, null, "cannot be read (" + WordLists.firstLine(ex) + "); "
                    + (previous == null ? "using the built-in defaults for this file." : "keeping the previous settings of this file.")));
            }
            for (ConfigEntry entry : registry.inFile(file)) {
                if (entry instanceof Setting<?> setting) {
                    values.put(setting.key(), read(setting, tree, previous, problems));
                } else if (entry instanceof Section section) {
                    sections.put(section.key(), readSection(section, tree, previous, problems));
                }
            }
            if (tree != null) {
                reportUnknownKeys(file, "", tree.root(), problems);
            }
        }
        return new Snapshot(Map.copyOf(values), Map.copyOf(sections), List.copyOf(problems));
    }

    private Object read(Setting<?> setting, YamlTree tree, Snapshot previous, List<ConfigProblem> problems) {
        if (tree == null) {
            Object old = previous == null ? null : previous.values().get(setting.key());
            return old != null ? old : setting.defaultValue();
        }
        Setting.Parsed<?> parsed = setting.parse(tree.get(setting.key()));
        if (parsed.problem() != null) {
            problems.add(new ConfigProblem(setting.file(), setting.key(), parsed.problem()));
        }
        return parsed.value();
    }

    private Map<String, Object> readSection(Section section, YamlTree tree, Snapshot previous, List<ConfigProblem> problems) {
        if (tree == null) {
            Map<String, Object> old = previous == null ? null : previous.sections().get(section.key());
            return old != null ? old : defaultSection(section);
        }
        Object raw = tree.get(section.key());
        if (raw == null) {
            // "key:" with nothing below it means empty; a key that is not in the file at all means the default
            return hasKey(tree, section.key()) ? Map.of() : defaultSection(section);
        }
        if (raw instanceof Map<?, ?> map) {
            return YamlTree.stringKeys(map);
        }
        problems.add(new ConfigProblem(section.file(), section.key(), "must be a section (key: value lines); using the default."));
        return defaultSection(section);
    }

    private static boolean hasKey(YamlTree tree, String key) {
        int dot = key.lastIndexOf('.');
        Map<String, Object> parent = dot < 0 ? tree.root() : tree.getMap(key.substring(0, dot));
        return parent.containsKey(key.substring(dot + 1));
    }

    public static Map<String, Object> defaultSection(Section section) {
        try {
            return YamlTree.parse(section.defaultYaml()).root();
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private void reportUnknownKeys(String file, String prefix, Map<String, Object> map, List<ConfigProblem> problems) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            ConfigEntry entry = registry.entry(key);
            if (entry != null) {
                if (!entry.file().equals(file)) {
                    problems.add(new ConfigProblem(file, key, "belongs in " + entry.file() + "; here it is ignored."));
                }
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> child && isParentOfEntry(key)) {
                reportUnknownKeys(file, key, YamlTree.stringKeys(child), problems);
            } else {
                problems.add(new ConfigProblem(file, key, "unknown key; it is ignored (check the spelling)."));
            }
        }
    }

    private boolean isParentOfEntry(String key) {
        String prefix = key + ".";
        return registry.entries().stream().anyMatch(e -> e.key().startsWith(prefix));
    }

    // ------------------------------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------------------------------

    /**
     * Changes a file with the comment-preserving editor and saves it atomically. The file is created with its defaults
     * first when it does not exist.
     */
    public void edit(String file, Consumer<YamlEditor> change) throws IOException {
        Path path = file(file);
        if (Files.notExists(path) && registry.files().contains(file)) {
            YamlEditor.writeAtomically(path, DefaultFiles.render(registry, file));
        }
        YamlEditor editor = YamlEditor.load(path);
        change.accept(editor);
        editor.save(path);
    }

    /** Writes one checked value into its file (comments stay). */
    public <T> void set(Setting<T> setting, T value) throws IOException {
        edit(setting.file(), editor -> editor.set(setting.key(), setting.toYaml(value)));
    }
}
