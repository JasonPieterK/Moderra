package com.jason.plugins.antibadwords.engine.config;

import com.jason.plugins.antibadwords.PunishmentAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code punishment.effects / attributes / sounds / commands} sections into {@link PunishmentAction}s
 * (same rules as the old plugin, including the legacy one-line {@code format:} entries).
 */
public final class PunishmentParser {
    /** name-value-value, where the value may be negative: {@code minecraft:max_health--2.5-10}. */
    private static final Pattern LEGACY = Pattern.compile("(.+?)\\s*-\\s*(-?\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)");

    private PunishmentParser() {
    }

    /** @param warn receives one line for every entry that is enabled but incomplete */
    public static List<PunishmentAction> parse(Map<String, Object> effects, Map<String, Object> attributes, Map<String, Object> sounds,
                                               Map<String, Object> commands, Consumer<String> warn) {
        List<PunishmentAction> actions = new ArrayList<>();
        read(effects, "punishment.effects", "effect", actions, warn);
        read(attributes, "punishment.attributes", "attribute", actions, warn);
        read(sounds, "punishment.sounds", "sound", actions, warn);
        read(commands, "punishment.commands", "command", actions, warn);
        return actions;
    }

    private static void read(Map<String, Object> section, String sectionPath, String type, List<PunishmentAction> out, Consumer<String> warn) {
        for (Map.Entry<String, Object> e : section.entrySet()) {
            YamlTree entry = e.getValue() instanceof Map<?, ?> m ? YamlTree.of(YamlTree.stringKeys(m)) : YamlTree.parse("");
            boolean enabled = entry.getBoolean("enabled", false);
            List<String> args = switch (type) {
                case "effect" -> structured(entry, "effect-name", "amplifier", "duration");
                case "attribute" -> structured(entry, "attribute-name", "value", "duration");
                case "sound" -> structured(entry, "sound-name", "volume", "pitch");
                default -> {
                    String command = entry.getString("command", "").trim();
                    yield command.isEmpty() ? legacy(entry, 1) : List.of(command);
                }
            };
            if (args.isEmpty()) {
                if (enabled) {
                    warn.accept(sectionPath + "." + e.getKey() + " is enabled but incomplete; it will be ignored.");
                }
                continue;
            }
            out.add(new PunishmentAction(enabled, type, e.getKey(), List.copyOf(args)));
        }
    }

    /** Reads the structured keys, or falls back to the legacy single-line {@code format:} key. */
    private static List<String> structured(YamlTree entry, String first, String second, String third) {
        String a = entry.getString(first, "").trim();
        Object b = entry.get(second);
        Object c = entry.get(third);
        if (!a.isEmpty() && b != null && c != null) {
            return List.of(a, String.valueOf(b).trim(), String.valueOf(c).trim());
        }
        return legacy(entry, 3);
    }

    /** Legacy format {@code name-value-value}; the last two parts are taken from the right. */
    private static List<String> legacy(YamlTree entry, int expectedParts) {
        String format = entry.getString("format", "").trim();
        if (format.isEmpty()) {
            return List.of();
        }
        if (expectedParts == 1) {
            return List.of(format);
        }
        Matcher m = LEGACY.matcher(format);
        return m.matches() ? List.of(m.group(1).trim(), m.group(2), m.group(3)) : List.of();
    }
}
