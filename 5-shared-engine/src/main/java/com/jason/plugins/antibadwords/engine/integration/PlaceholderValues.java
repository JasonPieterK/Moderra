package com.jason.plugins.antibadwords.engine.integration;

import com.jason.plugins.antibadwords.engine.EffectiveSettings;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import com.jason.plugins.antibadwords.engine.moderation.Ladder;
import com.jason.plugins.antibadwords.engine.moderation.MuteService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The values behind the PlaceholderAPI expansion {@code antibadwords} (usable by any other platform too). Safe on the main
 * thread: everything is answered from memory; the history count is cached and refreshed in the background. Only numbers,
 * names of steps and profiles, the mute data staff entered and yes/no values are returned: never chat text, word lists or secrets.
 *
 * <p>Names (without the {@code %antibadwords_} prefix): strikes, strikes_next, next_step, muted, mute_remaining, mute_reason,
 * profile, violations_total, protection_level.
 */
public final class PlaceholderValues {
    public static final List<String> NAMES = List.of("strikes", "strikes_next", "next_step", "muted", "mute_remaining",
        "mute_reason", "profile", "violations_total", "protection_level");
    /** How long a history count is reused (milliseconds). */
    static final long COUNT_TTL = 30_000L;

    private record Count(int value, long at) {
    }

    private final Engine engine;
    private final Map<UUID, Count> counts = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> refreshing = new ConcurrentHashMap<>();

    public PlaceholderValues(Engine engine) {
        this.engine = engine;
    }

    /**
     * @param player the player (online or not); {@code null} when PlaceholderAPI has none
     * @param name   the part after {@code antibadwords_}, any case
     * @return the text, or {@code null} for an unknown placeholder (PlaceholderAPI then leaves it as it is)
     */
    public String resolve(UUID player, String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!NAMES.contains(key)) {
            return null;
        }
        if (player == null) {
            return "";
        }
        return switch (key) {
            case "strikes" -> Integer.toString(engine.strikes().get(player));
            case "strikes_next" -> next(player).map(n -> Integer.toString(n.at())).orElse("");
            case "next_step" -> next(player).map(n -> n.step().name()).orElse("");
            case "muted" -> Boolean.toString(engine.mutes().isMuted(player));
            case "mute_remaining" -> engine.mutes().get(player).map(m -> DurationParser.format(m.remaining(engine.now()))).orElse("");
            case "mute_reason" -> engine.mutes().get(player).map(MuteService.Mute::reason).orElse("");
            case "profile" -> settings(player).profile();
            case "violations_total" -> Integer.toString(historyCount(player));
            case "protection_level" -> Integer.toString(settings(player).get(CoreSettings.PROTECTION_LEVEL));
            default -> null;
        };
    }

    private Optional<Ladder.Next> next(UUID player) {
        return settings(player).activeLadder().next(engine.strikes().get(player));
    }

    private EffectiveSettings settings(UUID player) {
        if (!engine.isOnline(player)) {
            return engine.settings(player, "", "", null);
        }
        return engine.settings(player, engine.platform().worldName(player), engine.platform().dimensionName(player),
            node -> engine.platform().has(player, node));
    }

    /** The cached number of history entries; a stale or missing value is refreshed off the main thread. */
    private int historyCount(UUID player) {
        long now = engine.now();
        Count cached = counts.get(player);
        if ((cached == null || now - cached.at() >= COUNT_TTL) && refreshing.putIfAbsent(player, Boolean.TRUE) == null) {
            try {
                engine.platform().runAsync(() -> {
                    try {
                        counts.put(player, new Count(engine.history().count(player), engine.now()));
                    } catch (RuntimeException ex) {
                        engine.platform().logger().warn("PlaceholderAPI: could not count the history of a player: " + ex);
                    } finally {
                        refreshing.remove(player);
                    }
                });
            } catch (RuntimeException ex) {
                refreshing.remove(player);
            }
            cached = counts.get(player);
        }
        return cached == null ? 0 : cached.value();
    }
}
