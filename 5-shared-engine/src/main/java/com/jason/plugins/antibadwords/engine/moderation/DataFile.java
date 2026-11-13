package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.EngineLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * One JSON state file in {@code data/} (strikes.json, players.json ...): safe loading, atomic writing and a debounced
 * save. The owner keeps the data in memory and hands out a snapshot when the file is written.
 *
 * <ul>
 *   <li>Writing: the text goes to {@code <name>.tmp} first and is then moved over the real file, so a crash in the
 *       middle never leaves a half-written file.</li>
 *   <li>Loading a damaged file: it is copied to {@code <name>.corrupt-<date>}, a loud warning is logged and the owner
 *       starts empty.</li>
 *   <li>Saving: {@link #changed(long)} marks the data as changed and asks the scheduler for one write after the delay;
 *       more changes in that time ride along. {@link #flush()} writes at once (server stop).</li>
 * </ul>
 * Thread-safe.
 */
public final class DataFile {
    /** Runs a task later, off the server thread when the server can do that. */
    @FunctionalInterface
    public interface Scheduler {
        void later(Runnable task, long delayMillis);
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path file;
    private final EngineLog log;
    private final LongSupplier clock;
    private final Scheduler scheduler;
    private final Supplier<Map<String, Object>> snapshot;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Object writeLock = new Object();

    /**
     * @param snapshot the complete content to write (maps, lists, texts, numbers, booleans); called while saving
     */
    public DataFile(Path file, EngineLog log, LongSupplier clock, Scheduler scheduler, Supplier<Map<String, Object>> snapshot) {
        this.file = file;
        this.log = log;
        this.clock = clock;
        this.scheduler = scheduler;
        this.snapshot = snapshot;
    }

    public Path file() {
        return file;
    }

    /**
     * Reads the file.
     *
     * @return the content, or {@code null} when there is no file, it is empty, or it was damaged (then a
     *     {@code .corrupt-<date>} copy was made and a warning logged)
     */
    public Map<String, Object> load() {
        synchronized (writeLock) {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String problem;
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.isBlank()) {
                    log.warn(file.getFileName() + " is empty; starting without saved data.");
                    return null;
                }
                // JSON is read with the YAML reader the engine already has (JSON is a subset of what it understands).
                LoaderOptions options = new LoaderOptions();
                options.setCodePointLimit(Integer.MAX_VALUE);
                options.setAllowDuplicateKeys(false);
                Object root = new Yaml(new SafeConstructor(options)).load(text);
                if (root instanceof Map<?, ?> map && text.stripLeading().startsWith("{") && map.get("version") instanceof Number) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) map;
                    return result;
                }
                problem = "it is not a data file of this plugin";
            } catch (IOException | RuntimeException ex) {
                problem = String.valueOf(ex.getMessage()).lines().findFirst().orElse(ex.toString());
            }
            quarantine(problem);
            return null;
        }
    }

    private void quarantine(String problem) {
        String stamp = STAMP.format(Instant.ofEpochMilli(clock.getAsLong()).atZone(ZoneId.systemDefault()));
        Path copy = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp);
        for (int n = 2; Files.exists(copy); n++) {
            copy = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp + "-" + n);
        }
        String kept;
        try {
            Files.copy(file, copy);
            kept = "A copy was kept as " + copy.getFileName() + ".";
        } catch (IOException ex) {
            kept = "A copy could NOT be made (" + ex.getMessage() + ").";
        }
        log.warn("=====================================================================");
        log.warn("DAMAGED DATA FILE: " + file + " cannot be read (" + problem + ").");
        log.warn(kept + " Starting with empty data; the damaged file is replaced at the next save.");
        log.warn("=====================================================================");
    }

    /** The data changed: write it once, {@code delayMillis} from now (changes until then are written together). */
    public void changed(long delayMillis) {
        dirty.set(true);
        if (scheduled.compareAndSet(false, true)) {
            try {
                scheduler.later(() -> {
                    scheduled.set(false);
                    flush();
                }, Math.max(0L, delayMillis));
            } catch (RuntimeException ex) {
                // for example: the server is stopping and takes no more tasks; shutdown writes the file
                scheduled.set(false);
            }
        }
    }

    /** The data changed, but nothing is scheduled; the next {@link #flush()} writes it. */
    public void markChanged() {
        dirty.set(true);
    }

    public boolean hasUnsavedChanges() {
        return dirty.get();
    }

    /**
     * Writes the file now when something changed since the last write.
     *
     * @return false when writing failed (logged; the change stays pending and is tried again at the next save)
     */
    public boolean flush() {
        synchronized (writeLock) {
            if (!dirty.compareAndSet(true, false)) {
                return true;
            }
            try {
                write(file, toJson(snapshot.get()));
                return true;
            } catch (IOException | RuntimeException ex) {
                dirty.set(true);
                log.warn("Could not save " + file + ": " + ex);
                return false;
            }
        }
    }

    /** Writes text to a temp file next to the target and moves it into place. */
    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---- a small JSON writer (maps, collections, texts, numbers, booleans, null) ----

    /** The value as indented JSON text. */
    public static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        json(out, value, 0);
        return out.append('\n').toString();
    }

    private static void json(StringBuilder out, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.append(first ? "\n" : ",\n").append("  ".repeat(depth + 1));
                quote(out, String.valueOf(e.getKey()));
                out.append(": ");
                json(out, e.getValue(), depth + 1);
                first = false;
            }
            out.append('\n').append("  ".repeat(depth)).append('}');
        } else if (value instanceof Collection<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                out.append(first ? "" : ", ");
                json(out, item, depth + 1);
                first = false;
            }
            out.append(']');
        } else if (value instanceof Number || value instanceof Boolean || value == null) {
            out.append(value);
        } else {
            quote(out, value.toString());
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 || c == 0xfeff || Character.isSurrogate(c)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
