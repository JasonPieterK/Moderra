package com.jason.plugins.moderra.engine.filters;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the spam checks remember about players: when they last sent messages and commands and what their last few
 * messages were. Kept in memory only. Bounded: a player is forgotten on quit, after being idle, and the oldest
 * entries are dropped when more than the configured number of players is tracked. Thread-safe.
 */
public final class SpamState {
    /** Separate counters per kind of event. */
    public enum Counter {
        MESSAGES, COMMANDS
    }

    private record Sent(long time, String text) {
    }

    private static final class Player {
        final ArrayDeque<Long> messages = new ArrayDeque<>();
        final ArrayDeque<Long> commands = new ArrayDeque<>();
        final ArrayDeque<Sent> recent = new ArrayDeque<>();
        volatile long lastSeen;
    }

    private static final long SWEEP_EVERY_MILLIS = 30_000L;

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();
    private volatile long lastSweep;
    private volatile int maxPlayers = 5000;
    private volatile long idleMillis = 600_000L;

    /** Limits, re-read from the settings before every use. */
    public void limits(int maxTrackedPlayers, long forgetAfterMillis) {
        this.maxPlayers = Math.max(1, maxTrackedPlayers);
        this.idleMillis = Math.max(1000L, forgetAfterMillis);
    }

    private Player touch(UUID id, long now) {
        if (now - lastSweep > SWEEP_EVERY_MILLIS || now < lastSweep) {
            sweep(now);
        }
        Player player = players.computeIfAbsent(id, k -> new Player());
        player.lastSeen = now;
        if (players.size() > maxPlayers) {
            sweep(now);
        }
        return player;
    }

    /** Forgets idle players, then the longest-idle ones until the limit holds. */
    public void sweep(long now) {
        lastSweep = now;
        players.values().removeIf(p -> now - p.lastSeen > idleMillis);
        int over = players.size() - maxPlayers;
        if (over > 0) {
            List<Map.Entry<UUID, Player>> all = new ArrayList<>(players.entrySet());
            all.sort(Comparator.comparingLong(e -> e.getValue().lastSeen));
            for (int i = 0; i < over && i < all.size(); i++) {
                players.remove(all.get(i).getKey());
            }
        }
    }

    /**
     * Notes one event now and says how many events (this one included) the player produced within the window.
     *
     * @param keep how many time stamps are kept at most (the threshold plus one is enough)
     */
    public int hit(UUID id, Counter counter, long now, long windowMillis, int keep) {
        Player player = touch(id, now);
        synchronized (player) {
            ArrayDeque<Long> times = counter == Counter.MESSAGES ? player.messages : player.commands;
            while (!times.isEmpty() && (now - times.peekFirst() >= windowMillis || times.peekFirst() > now)) {
                times.removeFirst();
            }
            times.addLast(now);
            while (times.size() > Math.max(1, keep)) {
                times.removeFirst();
            }
            return times.size();
        }
    }

    /**
     * Counts the remembered messages of the player that are (nearly) the same as {@code text} and still inside the
     * window, then remembers {@code text}.
     *
     * @param similarity 1.0 = identical only; lower values also count messages that differ in a few characters
     * @param history    how many messages are remembered per player
     */
    public int repeats(UUID id, String text, long now, long windowMillis, double similarity, int history) {
        Player player = touch(id, now);
        synchronized (player) {
            while (!player.recent.isEmpty() && (now - player.recent.peekFirst().time() >= windowMillis || player.recent.peekFirst().time() > now)) {
                player.recent.removeFirst();
            }
            int same = 0;
            for (Sent sent : player.recent) {
                if (Similarity.similar(sent.text(), text, similarity)) {
                    same++;
                }
            }
            player.recent.addLast(new Sent(now, text));
            while (player.recent.size() > Math.max(1, history)) {
                player.recent.removeFirst();
            }
            return same;
        }
    }

    /** Player left. */
    public void forget(UUID id) {
        players.remove(id);
    }

    public void clear() {
        players.clear();
    }

    /** Number of players currently remembered. */
    public int size() {
        return players.size();
    }
}
