package com.jason.plugins.antibadwords;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies the configured punishments to a player.
 *
 * <p>All methods must be called on the main server thread.
 *
 * <p><strong>Attribute safety:</strong> attribute punishments temporarily change a base value. The
 * original value is remembered (in memory <em>and</em> in the player's persistent data) so it is always
 * restored: when the timer ends, when the player quits, when the plugin is disabled, and - after a
 * crash - the next time the player joins. Stacking two attribute punishments never overwrites the
 * remembered original.
 */
public final class PunishmentExecutor {
    private static final String PDC_PREFIX = "attr_";

    private final AntiBadWordsPlugin plugin;
    /** player -> attribute -> pending restore. Main thread only. */
    private final Map<UUID, Map<Attribute, PendingRestore>> pending = new HashMap<>();
    private int restoreCounter;

    private record PendingRestore(double original, int token) {
    }

    public PunishmentExecutor(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs one action.
     *
     * @return a short human readable description for the punishment broadcast, or {@code null} when the
     *         action could not be applied (the reason is logged)
     */
    public String execute(Player player, PunishmentAction action) {
        try {
            return switch (action.type()) {
                case "effect" -> applyEffect(player, action);
                case "attribute" -> applyAttribute(player, action);
                case "sound" -> playSound(player, action);
                case "command" -> runCommand(player, action);
                default -> {
                    plugin.getLogger().warning("Unknown punishment type '" + action.type() + "' (" + action.id() + ")");
                    yield null;
                }
            };
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not apply " + action.type() + " punishment '" + action.id()
                + "' (" + String.join(", ", action.args()) + "): " + ex.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------------------------------

    private String applyEffect(Player player, PunishmentAction action) {
        List<String> args = action.args();
        String name = args.get(0);
        int amplifier = Math.max(0, parseInt(args.get(1)));
        int seconds = Math.max(1, parseInt(args.get(2)));
        PotionEffectType type = resolveEffect(name);
        if (type == null) {
            plugin.getLogger().warning("Unknown potion effect '" + name + "' (" + action.id() + ")");
            return null;
        }
        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier));
        return prettyName(name);
    }

    /** Legacy names (pre-1.20.5) that old configs may still use. */
    private static String effectAlias(String key) {
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

    private PotionEffectType resolveEffect(String raw) {
        String key = stripNamespace(raw).replace('.', '_');
        NamespacedKey namespaced = NamespacedKey.minecraft(effectAlias(key));
        return Registry.EFFECT.get(namespaced);
    }

    // ------------------------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------------------------

    private String applyAttribute(Player player, PunishmentAction action) {
        List<String> args = action.args();
        String name = args.get(0);
        double value = Double.parseDouble(args.get(1).trim());
        int seconds = Math.max(1, parseInt(args.get(2)));
        Attribute attribute = resolveAttribute(name);
        if (attribute == null) {
            plugin.getLogger().warning("Unknown attribute '" + name + "' (" + action.id() + ")");
            return null;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return null;
        }

        Map<Attribute, PendingRestore> forPlayer = pending.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        PendingRestore existing = forPlayer.get(attribute);
        double original = existing != null ? existing.original() : instance.getBaseValue();
        if (existing == null) {
            player.getPersistentDataContainer().set(pdcKey(attribute), PersistentDataType.DOUBLE, original);
        }

        instance.setBaseValue(value); // throws IllegalArgumentException when out of range -> caught by execute()
        int token = ++restoreCounter;
        forPlayer.put(attribute, new PendingRestore(original, token));

        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<Attribute, PendingRestore> current = pending.get(playerId);
            PendingRestore latest = current == null ? null : current.get(attribute);
            // Only the most recent punishment of this attribute is allowed to restore it.
            if (latest == null || latest.token() != token) {
                return;
            }
            // Look the player up again: after dying/respawning the old Player object is a stale entity,
            // and restoring on it would silently do nothing. Offline players are restored on quit.
            Player live = Bukkit.getPlayer(playerId);
            if (live != null) {
                restore(live, attribute);
            }
        }, seconds * 20L);
        return prettyName(name);
    }

    private void restore(Player player, Attribute attribute) {
        Map<Attribute, PendingRestore> forPlayer = pending.get(player.getUniqueId());
        PendingRestore entry = forPlayer == null ? null : forPlayer.remove(attribute);
        if (forPlayer != null && forPlayer.isEmpty()) {
            pending.remove(player.getUniqueId());
        }
        if (entry == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(entry.original());
        }
        player.getPersistentDataContainer().remove(pdcKey(attribute));
    }

    /** Restores every pending attribute of one player (quit/kick/disable). */
    public void restoreAll(Player player) {
        Map<Attribute, PendingRestore> forPlayer = pending.get(player.getUniqueId());
        if (forPlayer == null) {
            return;
        }
        for (Attribute attribute : List.copyOf(forPlayer.keySet())) {
            restore(player, attribute);
        }
    }

    /** Restores everything for every online player (plugin disable / reload). */
    public void restoreEverything() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreAll(player);
        }
        pending.clear();
    }

    /**
     * Repairs attributes left over from a crash or forced shutdown: if the player's data still holds a
     * remembered original value that this process does not know about, put it back.
     */
    public void restoreLeftovers(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (NamespacedKey key : List.copyOf(pdc.getKeys())) {
            if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT)) || !key.getKey().startsWith(PDC_PREFIX)) {
                continue;
            }
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key.getKey().substring(PDC_PREFIX.length())));
            Double original = pdc.get(key, PersistentDataType.DOUBLE);
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance != null && original != null) {
                instance.setBaseValue(original);
            }
            pdc.remove(key);
        }
    }

    private NamespacedKey pdcKey(Attribute attribute) {
        return new NamespacedKey(plugin, PDC_PREFIX + attribute.getKey().getKey());
    }

    /**
     * Accepts every spelling seen in the wild: {@code minecraft:movement_speed},
     * {@code minecraft:generic.movement_speed} (pre-1.21.2), {@code GENERIC_MOVEMENT_SPEED}, ...
     */
    private Attribute resolveAttribute(String raw) {
        String key = stripNamespace(raw).replace('.', '_');
        for (String prefix : new String[] {"generic_", "player_", "zombie_", "horse_"}) {
            if (key.startsWith(prefix)) {
                String shortKey = key.substring(prefix.length());
                Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(shortKey));
                if (attribute != null) {
                    return attribute;
                }
            }
        }
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
    }

    // ------------------------------------------------------------------------------------------
    // Sounds and commands
    // ------------------------------------------------------------------------------------------

    private String playSound(Player player, PunishmentAction action) {
        List<String> args = action.args();
        String name = stripNamespace(args.get(0));
        float volume = Float.parseFloat(args.get(1).trim());
        float pitch = Float.parseFloat(args.get(2).trim());
        String soundKey = resolveSoundKey(name);
        player.playSound(player.getLocation(), soundKey, volume, pitch);
        // "entity.villager.no" -> "villager no sound"
        int firstDot = name.indexOf('.');
        return prettyName(firstDot >= 0 ? name.substring(firstDot + 1) : name).replace('.', ' ') + " sound";
    }

    /** Accepts {@code entity.villager.no} as well as the old enum style {@code ENTITY_VILLAGER_NO}. */
    private String resolveSoundKey(String lowerName) {
        if (lowerName.indexOf('.') >= 0) {
            return lowerName;
        }
        String wanted = lowerName.replace('.', '_');
        for (Sound sound : Registry.SOUNDS) {
            String key = sound.getKey().getKey();
            if (key.replace('.', '_').equals(wanted)) {
                return key;
            }
        }
        return lowerName; // maybe a custom (resource pack) sound without dots
    }

    private String runCommand(Player player, PunishmentAction action) {
        String command = Placeholders.apply(action.args().get(0), Map.of("player", player.getName()));
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        return "server command";
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static int parseInt(String value) {
        return (int) Math.round(Double.parseDouble(value.trim()));
    }

    private static String stripNamespace(String raw) {
        String out = raw.toLowerCase(Locale.ROOT).trim();
        int colon = out.indexOf(':');
        return colon >= 0 ? out.substring(colon + 1) : out;
    }

    private static String prettyName(String rawName) {
        String base = stripNamespace(rawName);
        int lastDot = base.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < base.length()) {
            base = base.substring(lastDot + 1);
        }
        return base.replace('_', ' ');
    }
}
