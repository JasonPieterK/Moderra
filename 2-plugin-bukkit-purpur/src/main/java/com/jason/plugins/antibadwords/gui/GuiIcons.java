package com.jason.plugins.antibadwords.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.yaml.snakeyaml.Yaml;

/**
 * Maps the engine's abstract icon names ({@code Icons.TOGGLE_ON} ...) to Bukkit materials. The defaults are the
 * resource {@code gui-icons.yml} inside the jar; a file of the same name in the plugin folder overrides single entries.
 * An unknown name or a material this server version does not have gives the fallback material (never an error).
 */
public final class GuiIcons {
    public static final String FILE = "gui-icons.yml";
    private static final Material LAST_RESORT = Material.PAPER;

    private final Map<String, String> names = new HashMap<>();
    private String fallback = LAST_RESORT.name();

    /** Reads the jar defaults and then the override file in {@code dataFolder} (if present). Never throws. */
    public static GuiIcons load(Path dataFolder, Consumer<String> warn) {
        GuiIcons icons = new GuiIcons();
        try (InputStream in = GuiIcons.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in != null) {
                icons.merge(new String(in.readAllBytes(), StandardCharsets.UTF_8), warn);
            }
        } catch (IOException | RuntimeException ex) {
            warn.accept("Could not read the built-in " + FILE + ": " + ex.getMessage());
        }
        Path override = dataFolder == null ? null : dataFolder.resolve(FILE);
        if (override != null && Files.isRegularFile(override)) {
            try {
                icons.merge(Files.readString(override, StandardCharsets.UTF_8), warn);
            } catch (IOException | RuntimeException ex) {
                warn.accept("Could not read " + override + " (the defaults are used): " + ex.getMessage());
            }
        }
        return icons;
    }

    /** Merges one YAML text over the current mapping. */
    void merge(String yaml, Consumer<String> warn) {
        Object root = new Yaml().load(yaml);
        if (!(root instanceof Map<?, ?> map)) {
            return;
        }
        Object fb = map.get("fallback");
        if (fb != null) {
            fallback = String.valueOf(fb).trim();
        }
        if (map.get("icons") instanceof Map<?, ?> icons) {
            icons.forEach((key, value) -> {
                if (key != null && value != null) {
                    names.put(String.valueOf(key).trim().toLowerCase(Locale.ROOT), String.valueOf(value).trim());
                }
            });
        } else if (map.get("icons") != null) {
            warn.accept(FILE + ": 'icons' must be a map of icon name to material name.");
        }
    }

    /** The material for an icon name; never null, never air. */
    public Material material(String icon) {
        String wanted = icon == null ? null : names.get(icon.toLowerCase(Locale.ROOT));
        Material material = wanted == null ? null : lookup(wanted);
        if (material == null) {
            material = lookup(fallback);
        }
        return material == null ? LAST_RESORT : material;
    }

    private static Material lookup(String name) {
        Material material = Material.matchMaterial(name);
        return material == null || material.isAir() || !material.isItem() ? null : material;
    }
}
