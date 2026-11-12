package com.jason.plugins.antibadwords;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Reads the {@code punishment.effects / attributes / sounds / commands} sections of config.yml into
 * {@link PunishmentAction}s. Kept separate from the plugin class so it can be unit tested with a plain
 * {@code YamlConfiguration}.
 */
public final class PunishmentParser {
    /** name-value-value, where the value may be negative: {@code minecraft:max_health--2.5-10}. */
    private static final java.util.regex.Pattern LEGACY =
        java.util.regex.Pattern.compile("(.+?)\\s*-\\s*(-?\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)");

    private PunishmentParser() {
    }

    public static List<PunishmentAction> parse(FileConfiguration config, Logger logger) {
        List<PunishmentAction> actions = new ArrayList<>();
        readTypedActions(config, logger, "punishment.effects", "effect", actions);
        readTypedActions(config, logger, "punishment.attributes", "attribute", actions);
        readTypedActions(config, logger, "punishment.sounds", "sound", actions);
        readTypedActions(config, logger, "punishment.commands", "command", actions);
        return actions;
    }

    private static void readTypedActions(FileConfiguration config, Logger logger, String sectionPath, String type, List<PunishmentAction> destination) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
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
                    logger.warning(sectionPath + "." + key + " is enabled but incomplete; it will be ignored.");
                }
                continue;
            }
            destination.add(new PunishmentAction(enabled, type, key, List.copyOf(args)));
        }
    }

    /** Reads the structured keys, or falls back to the legacy single-line {@code format:} key. */
    private static List<String> structuredArgs(FileConfiguration config, String basePath, String first, String second, String third) {
        String a = config.getString(basePath + "." + first, "").trim();
        Object bRaw = config.get(basePath + "." + second);
        Object cRaw = config.get(basePath + "." + third);
        if (!a.isEmpty() && bRaw != null && cRaw != null) {
            return List.of(a, String.valueOf(bRaw).trim(), String.valueOf(cRaw).trim());
        }
        return legacyArgs(config, basePath, 3);
    }

    /**
     * Legacy format {@code name-value-value}. The last two {@code -} separated parts are taken from the
     * right, so names and values may themselves contain dashes.
     */
    private static List<String> legacyArgs(FileConfiguration config, String basePath, int expectedParts) {
        String format = config.getString(basePath + ".format", "").trim();
        if (format.isEmpty()) {
            return List.of();
        }
        if (expectedParts == 1) {
            return List.of(format);
        }
        java.util.regex.Matcher m = LEGACY.matcher(format);
        if (!m.matches()) {
            return List.of();
        }
        return List.of(m.group(1).trim(), m.group(2), m.group(3));
    }
}
