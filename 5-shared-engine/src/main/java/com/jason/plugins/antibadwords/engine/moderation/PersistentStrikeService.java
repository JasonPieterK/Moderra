package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.StrikeService;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Strike points per player UUID that survive a restart ({@code data/strikes.json}).
 *
 * <ul>
 *   <li><b>Expiry</b> (the old {@code violations-expire-minutes}): when the last strike is older than this, the
 *       whole count is forgotten.</li>
 *   <li><b>Decay</b>: every interval the player loses some points, counted from the moment they got their first
 *       point. Both rules can be on at the same time.</li>
 *   <li><b>Cap</b>: a count never goes above the maximum.</li>
 *   <li><b>Saving</b>: debounced through a {@link DataFile} (temp file + move); {@link #flush()} on server stop.</li>
 * </ul>
 * Expiry and decay are worked out from stored times whenever a count is read, so nothing has to tick in the
 * background and time that passes while the server is off counts too. Thread-safe; the file is never touched while the
 * lock on the counts is held by a caller (a save is asked for after the change is done).
 */
public final class PersistentStrikeService implements StrikeService {
    /**
     * The rules, read on every call so that {@code /abw reload} applies at once.
     *
     * @param expireMillis        forget everything this long after the last strike (0 = never)
     * @param decayIntervalMillis lose {@code decayPoints} every this long (0 = no decay)
     * @param decayPoints         points lost per interval
     * @param maxPoints           highest count a player can have (0 = no limit)
     * @param saveDelayMillis     wait this long after a change before the file is written
     * @param save                false = never write the file by itself (memory only)
     */
    public record Options(long expireMillis, long decayIntervalMillis, int decayPoints, int maxPoints, long saveDelayMillis, boolean save) {
    }

    private static final class Entry {
        int points;
        /** Time of the last strike (expiry counts from here). */
        long last;
        /** Decay counts from here; moves forward by whole intervals as points are taken away. */
        long anchor;
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final LongSupplier clock;
    private final Supplier<Options> options;
    private final EngineLog log;
    private final DataFile file;
    private volatile Listener listener;

    /** Told about strikes and resets that happen on this server (not those applied from elsewhere). Used by the network sync. */
    public interface Listener {
        void added(UUID player, int weight);

        void reset(UUID player);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Adds points that were given on another server of the network: counted and saved, but not passed on. */
    public int addRemote(UUID player, int weight) {
        Options o = options.get();
        int points = add(player, weight, o);
        changed(o);
        return points;
    }

    /** Resets a player because another server did; not passed on. */
    public void resetRemote(UUID player) {
        boolean removed;
        synchronized (this) {
            removed = entries.remove(player) != null;
        }
        if (removed) {
            changed(options.get());
        }
    }

    public PersistentStrikeService(Path file, LongSupplier clock, Supplier<Options> options, EngineLog log, DataFile.Scheduler scheduler) {
        this.clock = clock;
        this.options = options;
        this.log = log;
        this.file = new DataFile(file, log, clock, scheduler, this::snapshot);
    }

    /**
     * Replaces what is in memory with the content of the file. A missing file means "nobody has strikes"; a damaged
     * one is copied aside (see {@link DataFile#load()}) and also means "nobody".
     *
     * @return how many players with strikes were read
     */
    public int load() {
        Map<String, Object> root = file.load();
        synchronized (this) {
            entries.clear();
            if (root == null || !(root.get("strikes") instanceof Map<?, ?> saved)) {
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
                Entry entry = new Entry();
                entry.points = ((Number) row.get("points")).intValue();
                entry.last = ((Number) row.get("last")).longValue();
                entry.anchor = row.get("decay-from") instanceof Number n ? n.longValue() : entry.last;
                if (entry.points > 0) {
                    entries.put(UUID.fromString(String.valueOf(e.getKey())), entry);
                }
            } catch (RuntimeException ex) {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn(file.file().getFileName() + ": " + skipped + " entries could not be understood and were skipped.");
        }
        prune();
        return entries.size();
    }

    @Override
    public int add(UUID player, int weight) {
        Options o = options.get();
        int points = add(player, weight, o);
        changed(o);
        Listener l = listener;
        if (l != null && weight > 0) {
            l.added(player, weight);
        }
        return points;
    }

    private synchronized int add(UUID player, int weight, Options o) {
        long now = clock.getAsLong();
        Entry entry = live(player, now, o);
        if (entry == null) {
            if (weight <= 0) {
                return 0;
            }
            entry = new Entry();
            entry.anchor = now;
            entries.put(player, entry);
        }
        long total = (long) entry.points + weight;
        long cap = o.maxPoints() > 0 ? o.maxPoints() : Integer.MAX_VALUE;
        entry.points = (int) Math.max(0L, Math.min(cap, total));
        entry.last = now;
        if (entry.points == 0) {
            entries.remove(player);
        }
        return entry.points;
    }

    @Override
    public synchronized int get(UUID player) {
        Entry entry = live(player, clock.getAsLong(), options.get());
        return entry == null ? 0 : entry.points;
    }

    /** Time of the player's last strike in milliseconds (0 when the player has no points). */
    public synchronized long lastStrike(UUID player) {
        Entry entry = live(player, clock.getAsLong(), options.get());
        return entry == null ? 0L : entry.last;
    }

    @Override
    public void reset(UUID player) {
        boolean removed;
        synchronized (this) {
            removed = entries.remove(player) != null;
        }
        if (removed) {
            changed(options.get());
            Listener l = listener;
            if (l != null) {
                l.reset(player);
            }
        }
    }

    @Override
    public void clear() {
        boolean removed;
        synchronized (this) {
            removed = !entries.isEmpty();
            entries.clear();
        }
        if (removed) {
            changed(options.get());
        }
    }

    /** Everybody who has points right now. */
    public synchronized Map<UUID, Integer> all() {
        prune();
        Map<UUID, Integer> out = new HashMap<>();
        entries.forEach((id, entry) -> out.put(id, entry.points));
        return out;
    }

    /**
     * Sets a strikes file aside that must not be read any more because strikes are not kept right now (clear-on-quit is
     * on, or saving is off): otherwise a later switch of the setting would silently bring back strikes from long ago.
     * The file is renamed to {@code strikes.json.discarded} (a previous one is replaced), so nothing is lost for good.
     *
     * @param reason what to tell the admin ("clear-violations-on-quit is true")
     * @return true when there was a file and it was moved
     */
    public boolean discardFile(String reason) {
        Path path = file.file();
        if (!java.nio.file.Files.isRegularFile(path)) {
            return false;
        }
        Path aside = path.resolveSibling(path.getFileName() + ".discarded");
        try {
            try {
                java.nio.file.Files.move(path, aside, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException ex) {
                java.nio.file.Files.move(path, aside, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Ignoring the saved strikes in " + path.getFileName() + " because " + reason
                + ". The file was renamed to " + aside.getFileName() + " so old strikes do not come back later.");
            return true;
        } catch (java.io.IOException | RuntimeException ex) {
            log.warn("Could not set " + path + " aside (" + ex + "); it is ignored while " + reason + ".");
            return false;
        }
    }

    /** Writes the file now when something changed (server stop). */
    public boolean flush() {
        return file.flush();
    }

    /**
     * Server stop: writes the file (when {@code save} is true) and then lets go of the counts in memory. What was
     * written is read again by {@link #load()} at the next start.
     */
    public void shutdown(boolean save) {
        if (save) {
            file.markChanged();
            file.flush();
        }
        synchronized (this) {
            entries.clear();
        }
    }

    /** Must not be called while holding the lock (the save may run at once and needs it from another thread's view). */
    private void changed(Options o) {
        if (o.save()) {
            file.changed(o.saveDelayMillis());
        } else {
            file.markChanged();
        }
    }

    /** The player's entry after expiry and decay; {@code null} (and forgotten) when nothing is left. */
    private Entry live(UUID player, long now, Options o) {
        Entry entry = entries.get(player);
        if (entry == null) {
            return null;
        }
        if (!apply(entry, now, o)) {
            entries.remove(player);
            return null;
        }
        return entry;
    }

    /** Applies expiry, decay and the cap to an entry. @return false when no points are left */
    private static boolean apply(Entry entry, long now, Options o) {
        if (o.expireMillis() > 0 && now - entry.last > o.expireMillis()) {
            return false;
        }
        if (o.decayIntervalMillis() > 0 && now > entry.anchor) {
            long steps = (now - entry.anchor) / o.decayIntervalMillis();
            if (steps > 0) {
                long left = entry.points - steps * Math.max(1, o.decayPoints());
                entry.points = (int) Math.max(0L, left);
                entry.anchor += steps * o.decayIntervalMillis();
            }
        }
        if (o.maxPoints() > 0 && entry.points > o.maxPoints()) {
            entry.points = o.maxPoints();
        }
        return entry.points > 0;
    }

    private void prune() {
        long now = clock.getAsLong();
        Options o = options.get();
        for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
            if (!apply(it.next(), now, o)) {
                it.remove();
            }
        }
    }

    private synchronized Map<String, Object> snapshot() {
        prune();
        Map<String, Object> players = new TreeMap<>();
        entries.forEach((id, entry) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("points", entry.points);
            row.put("last", entry.last);
            row.put("decay-from", entry.anchor);
            players.put(id.toString(), row);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("strikes", players);
        return root;
    }
}
