package com.jason.plugins.moderra.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Name handling copied 1:1 from the plugin's PunishmentExecutor / ModerraListener. */
public final class Names {
    private Names() {
    }

    public static String stripNamespace(String raw) {
        String out = raw.toLowerCase(Locale.ROOT).trim();
        int colon = out.indexOf(':');
        return colon >= 0 ? out.substring(colon + 1) : out;
    }

    public static String prettyName(String rawName) {
        String base = stripNamespace(rawName);
        int lastDot = base.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < base.length()) {
            base = base.substring(lastDot + 1);
        }
        return base.replace('_', ' ');
    }

    public static int parseInt(String value) {
        return (int) Math.round(Double.parseDouble(value.trim()));
    }

    /** Legacy (pre-1.20.5) effect names that old configs may still use. */
    public static String effectKey(String raw) {
        String key = stripNamespace(raw).replace('.', '_');
        return switch (key) {
            case "slow" -> "slowness";
            case "confusion" -> "nausea";
            case "slow_digging" -> "mining_fatigue";
            case "fast_digging" -> "haste";
            case "damage_resistance" -> "resistance";
            case "increase_damage" -> "strength";
            case "harm" -> "instant_damage";
            case "heal" -> "instant_health";
            case "jump" -> "jump_boost";
            case "glow" -> "glowing";
            default -> key;
        };
    }

    /**
     * Candidate registry paths for an attribute name, best first: {@code minecraft:generic.movement_speed},
     * {@code GENERIC_MOVEMENT_SPEED} and {@code movement_speed} all give {@code movement_speed}.
     */
    public static List<String> attributeKeys(String raw) {
        String key = stripNamespace(raw).replace('.', '_');
        List<String> out = new ArrayList<>();
        for (String prefix : new String[] {"generic_", "player_", "zombie_", "horse_"}) {
            if (key.startsWith(prefix)) {
                out.add(key.substring(prefix.length()));
            }
        }
        out.add(key);
        return out;
    }

    /** "entity.villager.no" -> "villager no sound" (same as the plugin's PunishmentExecutor.describeSound). */
    public static String soundDescription(String rawName) {
        String name = stripNamespace(rawName);
        int firstDot = name.indexOf('.');
        String rest = firstDot >= 0 && firstDot + 1 < name.length() ? name.substring(firstDot + 1) : name;
        return rest.replace('.', ' ').replace('_', ' ') + " sound";
    }

    public static String formatPunishmentList(List<String> items) {
        if (items.isEmpty()) {
            return "punishment";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
    }

    /** {@code "/Minecraft:Msg"} -> {@code "msg"}. */
    public static String commandLabel(String firstWord) {
        String label = firstWord.startsWith("/") ? firstWord.substring(1) : firstWord;
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        return label.toLowerCase(Locale.ROOT);
    }

    /** Same as Bukkit's {@code ChatColor.translateAlternateColorCodes('&', ...)}. */
    public static String colour(String text) {
        if (text == null) {
            return "";
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) >= 0) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
