package com.jason.plugins.moderra.mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers the original value of every attribute that a punishment temporarily changed, so it can always
 * be put back: when the timer ends, when the player leaves, when the server stops - and, because the
 * ledger is also written to disk, after a crash (the plugin keeps the same data in the player's persistent
 * data container). Stacking two punishments on one attribute never overwrites the remembered original.
 *
 * <p>Thread-safe; knows nothing about Minecraft (attributes are identified by their registry path).
 */
public final class AttributeLedger {
    private record Pending(double original, int token) {
    }

    private final Path file;
    private final Map<UUID, Map<String, Pending>> pending = new HashMap<>();
    /** Left over from a previous process (crash / forced shutdown); restored when the player joins. */
    private final Map<UUID, Map<String, Double>> leftovers = new HashMap<>();
    private int counter;

    public AttributeLedger(Path file) {
        this.file = file;
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String[] p = line.split("\\|");
                    if (p.length == 3) {
                        leftovers.computeIfAbsent(UUID.fromString(p[0]), k -> new HashMap<>()).put(p[1], Double.parseDouble(p[2]));
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            // A damaged ledger must never stop the mod from starting.
        }
    }

    /** Original value to restore later: the already remembered one when stacking, else {@code currentBase}. */
    public synchronized double original(UUID player, String attribute, double currentBase) {
        Pending existing = pending.getOrDefault(player, Map.of()).get(attribute);
        return existing != null ? existing.original() : currentBase;
    }

    /** Registers a new punishment on the attribute and returns its token (only the newest token may restore). */
    public synchronized int register(UUID player, String attribute, double original) {
        int token = ++counter;
        pending.computeIfAbsent(player, k -> new HashMap<>()).put(attribute, new Pending(original, token));
        save();
        return token;
    }

    public synchronized boolean isLatest(UUID player, String attribute, int token) {
        Pending p = pending.getOrDefault(player, Map.of()).get(attribute);
        return p != null && p.token() == token;
    }

    /** Removes and returns the original value of one attribute, or {@code null} if nothing is pending. */
    public synchronized Double take(UUID player, String attribute) {
        Map<String, Pending> forPlayer = pending.get(player);
        Pending p = forPlayer == null ? null : forPlayer.remove(attribute);
        if (forPlayer != null && forPlayer.isEmpty()) {
            pending.remove(player);
        }
        save();
        return p == null ? null : p.original();
    }

    /** Everything pending for one player (quit/kick/disable), removed from the ledger. */
    public synchronized Map<String, Double> takeAll(UUID player) {
        Map<String, Double> out = new HashMap<>();
        Map<String, Pending> forPlayer = pending.remove(player);
        if (forPlayer != null) {
            forPlayer.forEach((k, v) -> out.put(k, v.original()));
        }
        save();
        return out;
    }

    public synchronized List<UUID> playersWithPending() {
        return new ArrayList<>(pending.keySet());
    }

    /** Values a crashed process left behind for this player; removed from the ledger. */
    public synchronized Map<String, Double> takeLeftovers(UUID player) {
        Map<String, Double> out = leftovers.remove(player);
        save();
        return out == null ? Map.of() : out;
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        leftovers.forEach((id, m) -> m.forEach((k, v) -> sb.append(id).append('|').append(k).append('|').append(v).append('\n')));
        pending.forEach((id, m) -> m.forEach((k, v) -> sb.append(id).append('|').append(k).append('|').append(v.original()).append('\n')));
        try {
            if (sb.length() == 0) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            // Best effort: the in-memory restore still works.
        }
    }
}
