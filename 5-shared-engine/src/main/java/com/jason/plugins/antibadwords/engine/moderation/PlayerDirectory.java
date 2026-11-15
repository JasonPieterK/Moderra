package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.EngineLog;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Remembers every player who was on the server ({@code data/players.json}: UUID, last name, last seen), so commands
 * can find a player who is offline by name. Thread-safe.
 */
public final class PlayerDirectory {
    /** A player the server has seen. */
    public record Known(UUID id, String name, long lastSeen, String ip) {
        public Known(UUID id, String name, long lastSeen) {
            this(id, name, lastSeen, null);
        }
    }

    private final Map<UUID, Known> byId = new HashMap<>();
    private final LongSupplier clock;
    private final LongSupplier saveDelayMillis;
    private final LongSupplier forgetAfterMillis;
    private final EngineLog log;
    private final DataFile file;
    private volatile java.util.function.BooleanSupplier storeIps = () -> true;

    /**
     * @param saveDelayMillis   wait this long after a change before the file is written (read on every change)
     * @param forgetAfterMillis players not seen for this long are dropped (0 = never; read when loading and saving)
     */
    public PlayerDirectory(Path file, LongSupplier clock, LongSupplier saveDelayMillis, LongSupplier forgetAfterMillis, EngineLog log,
                           DataFile.Scheduler scheduler) {
        this.clock = clock;
        this.saveDelayMillis = saveDelayMillis;
        this.forgetAfterMillis = forgetAfterMillis;
        this.log = log;
        this.file = new DataFile(file, log, clock, scheduler, this::snapshot);
    }

    /** Whether IP addresses are kept and written (read on every change and save). Default: yes. */
    public void storeIps(java.util.function.BooleanSupplier setting) {
        this.storeIps = setting;
    }

    /** Replaces what is in memory with the content of the file (missing or damaged file = nobody known). */
    public int load() {
        Map<String, Object> root = file.load();
        synchronized (this) {
            byId.clear();
            if (root == null || !(root.get("players") instanceof Map<?, ?> saved)) {
                return 0;
            }
            return read(saved);
        }
    }

    private int read(Map<?, ?> saved) {
        int skipped = 0;
        for (Map.Entry<?, ?> e : saved.entrySet()) {
            try {
                Map<?, ?> row = (Map<?, ?>) e.getValue();
                UUID id = UUID.fromString(String.valueOf(e.getKey()));
                String name = String.valueOf(row.get("name"));
                if (row.get("name") == null || name.isEmpty()) {
                    throw new IllegalArgumentException("no name");
                }
                String ip = row.get("ip") instanceof String text && !text.isEmpty() ? text : null;
                byId.put(id, new Known(id, name, ((Number) row.get("last-seen")).longValue(), ip));
            } catch (RuntimeException ex) {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn(file.file().getFileName() + ": " + skipped + " entries could not be understood and were skipped.");
        }
        prune();
        return byId.size();
    }

    /** The player is on the server right now (join): remembers the name and the time. */
    public void seen(UUID id, String name) {
        if (id == null || name == null || name.isEmpty()) {
            return;
        }
        synchronized (this) {
            Known old = byId.get(id);
            byId.put(id, new Known(id, name, clock.getAsLong(), old == null ? null : old.ip()));
        }
        file.changed(saveDelayMillis.getAsLong()); // outside the lock: the save may run at once
    }

    /** Remembers the last IP address of a known player ({@code null} or blank = nothing to remember). Ignored when storing is off. */
    public void ip(UUID id, String ip) {
        if (id == null || ip == null || ip.isBlank() || !storeIps.getAsBoolean()) {
            return;
        }
        synchronized (this) {
            Known known = byId.get(id);
            if (known == null || ip.equals(known.ip())) {
                return;
            }
            byId.put(id, new Known(id, known.name(), known.lastSeen(), ip));
        }
        file.changed(saveDelayMillis.getAsLong());
    }

    /** The last known IP of a player, or empty (never stored, or storing is switched off). */
    public Optional<String> ipOf(UUID id) {
        return get(id).map(Known::ip).filter(ip -> storeIps.getAsBoolean());
    }

    /** As {@link #seen} but only when the player is unknown or has a new name (cheap; for places that run often). */
    public void learn(UUID id, String name) {
        if (id == null || name == null) {
            return;
        }
        Known known = get(id).orElse(null);
        if (known == null || !known.name().equals(name)) {
            seen(id, name);
        }
    }

    /** Updates the "last seen" time of a known player (quit). */
    public void touch(UUID id) {
        Known known = id == null ? null : get(id).orElse(null);
        if (known != null) {
            seen(id, known.name());
        }
    }

    public synchronized Optional<Known> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Finds a player by name. Upper and lower case do not matter, but a name written exactly is preferred; when two
     * players had the same name (a name was given away), the one seen last wins.
     */
    public synchronized Optional<Known> find(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        Comparator<Known> best = Comparator.<Known, Boolean>comparing(k -> k.name().equals(name)).thenComparingLong(Known::lastSeen);
        return byId.values().stream().filter(k -> k.name().equalsIgnoreCase(name)).max(best);
    }

    /** Known names that start with the prefix (case-insensitive), sorted, at most {@code limit}. */
    public synchronized List<String> names(String prefix, int limit) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return byId.values().stream().map(Known::name).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p)).distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER).limit(Math.max(0, limit)).toList();
    }

    public synchronized int size() {
        return byId.size();
    }

    /** Writes the file now when something changed (server stop). */
    public boolean flush() {
        return file.flush();
    }

    private void prune() {
        long keep = forgetAfterMillis.getAsLong();
        if (keep > 0) {
            long now = clock.getAsLong();
            byId.values().removeIf(k -> now - k.lastSeen() > keep);
        }
    }

    private synchronized Map<String, Object> snapshot() {
        prune();
        Map<String, Object> players = new TreeMap<>();
        byId.forEach((id, known) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", known.name());
            row.put("last-seen", known.lastSeen());
            if (known.ip() != null && storeIps.getAsBoolean()) {
                row.put("ip", known.ip());
            }
            players.put(id.toString(), row);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("players", players);
        return root;
    }
}
