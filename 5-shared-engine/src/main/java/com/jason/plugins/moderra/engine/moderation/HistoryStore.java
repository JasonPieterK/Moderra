package com.jason.plugins.moderra.engine.moderation;

import com.jason.plugins.moderra.engine.EngineLog;
import com.jason.plugins.moderra.engine.ModerationEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The moderation history: one JSON object per line in {@code data/history/YYYY-MM.jsonl} (month in UTC). Fed by the
 * engine's event stream ({@link #record}); the caller only builds a small record and queues it, a single background
 * thread does all file work, so the server thread never waits for the disk when writing. Thread-safe.
 *
 * <p>Never written: secrets, or any event data that is not in the fixed field list. The chat text only when
 * {@link Options#storeText()} is on, then optionally with the bad word masked.
 */
public final class HistoryStore {
    public static final String FOLDER = "history";
    public static final String PERMISSION = "moderra.history";
    private static final Pattern FILE_NAME = Pattern.compile("(\\d{4})-(\\d{2})\\.jsonl");
    private static final int MAX_FIELD = 300;
    private static final long CLEANUP_EVERY_MILLIS = 3_600_000L;
    private static final long DAY = 86_400_000L;

    /** Settings, read for every event. {@code kinds} holds lower-case kinds or {@code *}. */
    public record Options(boolean enabled, Set<String> kinds, boolean storeText, boolean redactWords, int retentionDays, long maxTotalBytes,
                          int pageSize) {
    }

    /** One line of the history. {@code points} is -1 when the event carries no strike count; {@code text} is null when not stored. */
    public record Entry(long time, String kind, UUID playerId, String player, String world, String source, String check, String detail,
                        String actor, int points, String step, String text) {
    }

    /** One page of a search: the entries and how many matched in all. */
    public record Page(List<Entry> entries, int total) {
    }

    private final Path dir;
    private final LongSupplier clock;
    private final EngineLog log;
    private final Supplier<Options> options;
    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private ExecutorService executor;
    private boolean closed;
    // touched only by the writer thread
    private long lastCleanup = Long.MIN_VALUE;
    private long lastFullWarning = Long.MIN_VALUE;

    /** @param dataFolder the {@code data} folder; the history lives in its {@code history} sub-folder */
    public HistoryStore(Path dataFolder, LongSupplier clock, EngineLog log, Supplier<Options> options) {
        this.dir = dataFolder.resolve(FOLDER);
        this.clock = clock;
        this.log = log;
        this.options = options;
    }

    // ------------------------------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------------------------------

    /** The event subscriber. Cheap; the file work happens on the writer thread. */
    public void record(ModerationEvent event) {
        Options o = options.get();
        if (!o.enabled() || (!o.kinds().contains("*") && !o.kinds().contains(event.kind().toLowerCase(Locale.ROOT)))) {
            return;
        }
        enqueue(toEntry(event, o));
    }

    /** Queues one entry. */
    public void enqueue(Entry entry) {
        synchronized (this) {
            if (closed) {
                return;
            }
            queue.add(entry);
            if (scheduled.compareAndSet(false, true)) {
                executor().execute(this::drain);
            }
        }
    }

    static Entry toEntry(ModerationEvent event, Options o) {
        Map<String, String> d = event.data();
        String detail = firstNotBlank(d.get("detail"), d.get("reason"), d.get("actions"), d.get("command"));
        String duration = d.get("duration");
        if (duration != null && !duration.isBlank() && !detail.contains(duration)) {
            detail = detail.isEmpty() ? duration : detail + " [" + duration + "]";
        }
        String actor = firstNotBlank(d.get("actor"), d.get("sender"));
        int points = -1;
        try {
            points = Integer.parseInt(d.getOrDefault("count", "-1").trim());
        } catch (NumberFormatException ignored) {
            // no strike count
        }
        String text = null;
        if (o.storeText() && d.get("text") != null && !d.get("text").isBlank()) {
            text = o.redactWords() ? redact(d.get("text"), List.of(d.getOrDefault("word", ""), d.getOrDefault("detail", ""))) : d.get("text");
        }
        return new Entry(event.time(), event.kind(), event.playerId(), nz(event.playerName()), nz(event.world()), nz(event.source()),
            nz(d.get("check")), cut(detail), actor.isEmpty() ? "system" : cut(actor), points, nz(d.get("step")), text == null ? null : cut(text));
    }

    /**
     * Replaces every occurrence of the words by stars (case-insensitive). {@code null} when none of the words occurs: then the
     * bad word is written in a way that cannot be masked (disguised) and the text is better left out.
     */
    static String redact(String text, List<String> words) {
        String out = text;
        boolean found = false;
        for (String word : words) {
            if (word == null || word.strip().length() < 2) {
                continue;
            }
            Matcher m = Pattern.compile(Pattern.quote(word.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                found = true;
                m.appendReplacement(sb, Matcher.quoteReplacement("*".repeat(m.group().length())));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return found ? out : null;
    }

    private static String firstNotBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String cut(String s) {
        return s.length() > MAX_FIELD ? s.substring(0, MAX_FIELD) : s;
    }

    private synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Moderra-history");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }

    private void drain() {
        try {
            while (true) {
                List<Entry> batch = new ArrayList<>();
                for (Entry e; batch.size() < 1000 && (e = queue.poll()) != null; ) {
                    batch.add(e);
                }
                if (batch.isEmpty()) {
                    scheduled.set(false);
                    if (queue.isEmpty() || !scheduled.compareAndSet(false, true)) {
                        return;
                    }
                    continue;
                }
                write(batch);
            }
        } catch (RuntimeException | Error ex) {
            scheduled.set(false);
            log.warn("The history writer failed: " + ex);
        }
    }

    private void write(List<Entry> batch) {
        long now = clock.getAsLong();
        Options o = options.get();
        try {
            Files.createDirectories(dir);
            if (lastCleanup == Long.MIN_VALUE || now - lastCleanup >= CLEANUP_EVERY_MILLIS) {
                cleanup(o);
            }
            if (o.maxTotalBytes() > 0 && totalSize() >= o.maxTotalBytes()) {
                cleanup(o);
                if (totalSize() >= o.maxTotalBytes()) {
                    if (lastFullWarning == Long.MIN_VALUE || now - lastFullWarning >= CLEANUP_EVERY_MILLIS) {
                        lastFullWarning = now;
                        log.warn("The history is over its size limit (history.max-total-size-mb); " + batch.size() + " events were not written.");
                    }
                    return;
                }
            }
            Map<Path, StringBuilder> perFile = new LinkedHashMap<>();
            for (Entry e : batch) {
                perFile.computeIfAbsent(dir.resolve(fileName(e.time())), k -> new StringBuilder()).append(toJson(e)).append('\n');
            }
            for (Map.Entry<Path, StringBuilder> f : perFile.entrySet()) {
                Files.writeString(f.getKey(), f.getValue(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException ex) {
            log.warn("Could not write the history: " + ex.getMessage());
        }
    }

    static String fileName(long time) {
        ZonedDateTime t = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC);
        return String.format(Locale.ROOT, "%04d-%02d.jsonl", t.getYear(), t.getMonthValue());
    }

    /** First instant after the month of the file, or -1 when the name is not a history file. */
    private static long monthEnd(Path file) {
        Matcher m = FILE_NAME.matcher(file.getFileName().toString());
        if (!m.matches()) {
            return -1;
        }
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return -1;
        }
        return ZonedDateTime.of(Integer.parseInt(m.group(1)), month, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusMonths(1).toInstant().toEpochMilli();
    }

    private List<Path> files(boolean newestFirst) {
        List<Path> out = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> monthEnd(p) >= 0 && Files.isRegularFile(p)).forEach(out::add);
            } catch (IOException ex) {
                log.warn("Could not list the history folder: " + ex.getMessage());
            }
        }
        out.sort(newestFirst ? Collections.reverseOrder() : null);
        return out;
    }

    private long totalSize() {
        long sum = 0;
        for (Path p : files(false)) {
            try {
                sum += Files.size(p);
            } catch (IOException ignored) {
                // vanished
            }
        }
        return sum;
    }

    /** Deletes files past the retention and the oldest months while the size limit is exceeded (writer thread). */
    private void cleanup(Options o) {
        long now = clock.getAsLong();
        lastCleanup = now;
        List<Path> files = files(false); // oldest first
        if (o.retentionDays() > 0) {
            for (Path p : new ArrayList<>(files)) {
                if (monthEnd(p) < now - o.retentionDays() * DAY) {
                    delete(p, "older than " + o.retentionDays() + " days");
                    files.remove(p);
                }
            }
        }
        if (o.maxTotalBytes() > 0) {
            String current = fileName(now);
            long total = totalSize();
            for (Path p : files) {
                if (total < o.maxTotalBytes()) {
                    break;
                }
                if (p.getFileName().toString().equals(current)) {
                    continue;
                }
                long size = 0;
                try {
                    size = Files.size(p);
                } catch (IOException ignored) {
                    // vanished
                }
                delete(p, "size limit");
                total -= size;
            }
        }
    }

    private void delete(Path p, String why) {
        try {
            Files.deleteIfExists(p);
            log.info("Deleted history file " + p.getFileName() + " (" + why + ").");
        } catch (IOException ex) {
            log.warn("Could not delete history file " + p.getFileName() + ": " + ex.getMessage());
        }
    }

    /** Applies retention and the size limit now (this also happens at the first write and then at most hourly). Returns when done. */
    public void cleanupNow() {
        runOnWriter(() -> cleanup(options.get()));
    }

    // ------------------------------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------------------------------

    /** Waits until everything queued so far is written (before reads; tests). */
    public void flush() {
        runOnWriter(() -> { });
    }

    private void runOnWriter(Runnable task) {
        ExecutorService ex;
        synchronized (this) {
            ex = closed ? null : executor;
        }
        if (ex == null) {
            task.run();
            return;
        }
        try {
            ex.submit(task).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("The history writer did not answer: " + e);
        }
    }

    /**
     * Entries of one player (or of everybody when {@code playerId} is null), newest first.
     *
     * @param page 1-based
     * @param size entries per page
     */
    public Page find(UUID playerId, int page, int size) {
        flush();
        int skip = Math.max(0, page - 1) * size;
        List<Entry> out = new ArrayList<>();
        int total = 0;
        for (Path file : files(true)) {
            List<Entry> entries = read(file);
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry e = entries.get(i);
                if (playerId != null && !playerId.equals(e.playerId())) {
                    continue;
                }
                if (total >= skip && out.size() < size) {
                    out.add(e);
                }
                total++;
            }
        }
        return new Page(out, total);
    }

    /** The newest {@code n} entries of everybody. */
    public List<Entry> recent(int n) {
        flush();
        List<Entry> out = new ArrayList<>();
        for (Path file : files(true)) {
            List<Entry> entries = read(file);
            for (int i = entries.size() - 1; i >= 0 && out.size() < n; i--) {
                out.add(entries.get(i));
            }
            if (out.size() >= n) {
                break;
            }
        }
        return out;
    }

    private List<Entry> read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Could not read history file " + file.getFileName() + ": " + ex.getMessage());
            return List.of();
        }
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        List<Entry> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Entry e = parse(yaml, line);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    private static Entry parse(Yaml yaml, String line) {
        if (!line.startsWith("{")) {
            return null;
        }
        try {
            if (!(yaml.load(line) instanceof Map<?, ?> m)) {
                return null;
            }
            UUID id = m.get("uuid") == null ? null : UUID.fromString(String.valueOf(m.get("uuid")));
            return new Entry(((Number) m.get("time")).longValue(), str(m, "kind"), id, str(m, "player"), str(m, "world"), str(m, "source"),
                str(m, "check"), str(m, "detail"), str(m, "actor"), m.get("points") instanceof Number n ? n.intValue() : -1, str(m, "step"),
                m.get("text") == null ? null : String.valueOf(m.get("text")));
        } catch (RuntimeException ex) {
            return null; // a damaged line is skipped
        }
    }

    private static String str(Map<?, ?> m, String key) {
        return m.get(key) == null ? "" : String.valueOf(m.get(key));
    }

    // ------------------------------------------------------------------------------------------
    // Purge and shutdown
    // ------------------------------------------------------------------------------------------

    /** How many entries belong to the player. */
    public int count(UUID playerId) {
        return find(playerId, 1, 1).total();
    }

    /** Removes every entry of the player from all files; returns how many were removed. */
    public int purge(UUID playerId) {
        int[] removed = {0};
        runOnWriter(() -> removed[0] = purgeNow(playerId));
        return removed[0];
    }

    private int purgeNow(UUID playerId) {
        List<Entry> pending = new ArrayList<>(); // writes still queued go to disk first so they are purged too
        for (Entry e; (e = queue.poll()) != null; ) {
            pending.add(e);
        }
        if (!pending.isEmpty()) {
            write(pending);
        }
        String needle = "\"uuid\": \"" + playerId + "\"";
        int removed = 0;
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        for (Path file : files(false)) {
            try {
                List<String> keep = new ArrayList<>();
                int gone = 0;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.contains(needle) && parse(yaml, line) instanceof Entry e && playerId.equals(e.playerId())) {
                        gone++;
                    } else {
                        keep.add(line);
                    }
                }
                if (gone == 0) {
                    continue;
                }
                removed += gone;
                if (keep.isEmpty()) {
                    Files.deleteIfExists(file);
                } else {
                    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                    Files.write(tmp, keep, StandardCharsets.UTF_8);
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (IOException ex) {
                log.warn("Could not purge history file " + file.getFileName() + ": " + ex.getMessage());
            }
        }
        return removed;
    }

    /** Writes everything that is queued and stops the writer thread (server stop). Later events are ignored. */
    public void shutdown() {
        ExecutorService ex;
        synchronized (this) {
            if (closed) {
                return;
            }
            ex = executor;
        }
        flush();
        synchronized (this) {
            closed = true;
        }
        if (ex != null) {
            ex.shutdown();
            try {
                ex.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<Entry> rest = new ArrayList<>();
        for (Entry e; (e = queue.poll()) != null; ) {
            rest.add(e);
        }
        if (!rest.isEmpty()) {
            write(rest);
        }
    }

    // ------------------------------------------------------------------------------------------
    // JSON line
    // ------------------------------------------------------------------------------------------

    static String toJson(Entry e) {
        StringBuilder out = new StringBuilder("{");
        field(out, "time", e.time());
        field(out, "kind", e.kind());
        if (e.playerId() != null) {
            field(out, "uuid", e.playerId().toString());
        }
        field(out, "player", e.player());
        field(out, "world", e.world());
        field(out, "source", e.source());
        field(out, "check", e.check());
        field(out, "detail", e.detail());
        field(out, "actor", e.actor());
        if (e.points() >= 0) {
            field(out, "points", e.points());
        }
        field(out, "step", e.step());
        if (e.text() != null) {
            field(out, "text", e.text());
        }
        out.setLength(out.length() - 1); // the last comma
        return out.append('}').toString();
    }

    private static void field(StringBuilder out, String key, Object value) {
        if (value instanceof String s && s.isEmpty()) {
            return;
        }
        out.append('"').append(key).append("\": ");
        if (value instanceof Number) {
            out.append(value);
        } else {
            quote(out, (String) value);
        }
        out.append(',');
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
