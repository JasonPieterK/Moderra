package com.jason.plugins.antibadwords.engine.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for configuration. Every feature adds its settings here (in a
 * {@code static void register(SettingsRegistry r)} method). From the registry come: reading and checking the files,
 * the generated default files with comments, {@code 6-guides/CONFIGURATION.md}, validation messages and (later) the GUI.
 *
 * <p>Registration order is kept: it is the order of keys in the generated files and of rows in the docs.
 */
public final class SettingsRegistry {
    private final Map<String, ConfigEntry> entries = new LinkedHashMap<>();
    private final Map<String, String> fileHeaders = new LinkedHashMap<>();

    /** @throws IllegalArgumentException when the key is already taken in that file or clashes with a section */
    public <T> Setting<T> register(Setting<T> setting) {
        add(setting);
        return setting;
    }

    public Section register(Section section) {
        add(section);
        return section;
    }

    private void add(ConfigEntry entry) {
        for (ConfigEntry other : entries.values()) {
            if (!other.file().equals(entry.file())) {
                continue;
            }
            if (other.key().equals(entry.key()) || other.key().startsWith(entry.key() + ".") || entry.key().startsWith(other.key() + ".")) {
                throw new IllegalArgumentException("Setting key '" + entry.key() + "' clashes with '" + other.key() + "' in " + entry.file());
            }
        }
        if (entries.containsKey(entry.key())) {
            throw new IllegalArgumentException("Setting key '" + entry.key() + "' is registered twice (keys must be unique across all files)");
        }
        entries.put(entry.key(), entry);
    }

    /** Comment block written at the top of a generated file. */
    public void fileHeader(String file, String header) {
        fileHeaders.put(file, header);
    }

    public String fileHeader(String file) {
        return fileHeaders.getOrDefault(file, "");
    }

    /** The setting with this key, or {@code null}. */
    public Setting<?> get(String key) {
        return entries.get(key) instanceof Setting<?> s ? s : null;
    }

    /** The free-form section with this key, or {@code null}. */
    public Section section(String key) {
        return entries.get(key) instanceof Section s ? s : null;
    }

    public ConfigEntry entry(String key) {
        return entries.get(key);
    }

    /** The section that {@code key} is, or lies inside of; {@code null} when there is none. */
    public Section sectionCovering(String key) {
        for (ConfigEntry e : entries.values()) {
            if (e instanceof Section s && (key.equals(s.key()) || key.startsWith(s.key() + "."))) {
                return s;
            }
        }
        return null;
    }

    public Collection<ConfigEntry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public List<Setting<?>> settings() {
        List<Setting<?>> out = new ArrayList<>();
        for (ConfigEntry e : entries.values()) {
            if (e instanceof Setting<?> s) {
                out.add(s);
            }
        }
        return out;
    }

    /** Every file that owns at least one entry, in registration order. */
    public List<String> files() {
        Set<String> out = new LinkedHashSet<>();
        entries.values().forEach(e -> out.add(e.file()));
        return new ArrayList<>(out);
    }

    public List<ConfigEntry> inFile(String file) {
        return entries.values().stream().filter(e -> e.file().equals(file)).toList();
    }

    /** Categories in registration order (one GUI page each). */
    public List<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        entries.values().forEach(e -> out.add(e.category()));
        return new ArrayList<>(out);
    }

    public List<ConfigEntry> inCategory(String category) {
        return entries.values().stream().filter(e -> e.category().equals(category)).toList();
    }
}
