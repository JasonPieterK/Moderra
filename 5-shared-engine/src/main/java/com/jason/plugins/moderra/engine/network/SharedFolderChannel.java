package com.jason.plugins.moderra.engine.network;

import com.jason.plugins.moderra.engine.EngineLog;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Shared-folder transport: every server appends its changes to its own {@code sync-<id>.jsonl} (so two servers never write
 * the same file) and tails the journals of the others. How far each foreign journal was read is kept in the file
 * {@code read-by-<id>.txt} in the same folder (so a restart neither misses nor repeats records). A journal that has
 * never been read before is a backlog: its records go to the backlog sink. Only whole lines (ending in a line break) are read.
 *
 * <h2>Journal compaction</h2>
 * With a size limit (setting {@code journal-max-kb}) a server shrinks its OWN journal. The part that every other server has
 * already read is replaced by a short summary (the newest mute or unmute and reset of each player; mutes that ran out are
 * dropped). The rewrite goes to a temporary file that replaces the journal in one atomic move. The read positions of the other
 * servers stay valid because they are LOGICAL: the new first line {@code #moderra-journal base=<B> prefix=<S>} says that the
 * {@code S} bytes after it are a summary and that the next line is at logical position {@code B}. A reader with a position
 * below {@code B} reads the summary and then the rest; a reader at or above {@code B} continues where it was. A journal without
 * that line has base 0 and no summary (the old format), so old files read as before.
 */
public final class SharedFolderChannel implements SyncChannel {
    /** Read positions older than this are not waited for (a server that was removed from the network). */
    static final long STALE_READER_MILLIS = 7L * 24 * 60 * 60 * 1000;
    static final String HEADER = "#moderra-journal";
    private static final long MAX_COMPACT_BYTES = 256L * 1024 * 1024;

    private final Path folder;
    private final String origin;
    private final EngineLog log;
    private final Map<String, Long> offsets = new HashMap<>();
    private final Path offsetFile;
    /** Journals that existed when this server started but were never read: their content is history, not news. */
    private final java.util.Set<String> backlogIds = new java.util.HashSet<>();
    private final LongSupplier maxBytes;
    private final LongSupplier clock;
    private long nextCompactionSize;
    private boolean dirty;
    private boolean failedOnce;

    public SharedFolderChannel(Path folder, String origin, EngineLog log) {
        this(folder, origin, log, () -> 0L, System::currentTimeMillis);
    }

    /**
     * @param maxBytes size limit of this server's journal in bytes, 0 or less = never shrink (read at each use, so a reload applies)
     * @param clock    wall clock milliseconds (decides which mutes of a summary have run out)
     */
    public SharedFolderChannel(Path folder, String origin, EngineLog log, LongSupplier maxBytes, LongSupplier clock) {
        this.folder = folder;
        this.origin = origin;
        this.log = log;
        this.maxBytes = maxBytes;
        this.clock = clock;
        this.offsetFile = folder.resolve("read-by-" + origin + ".txt");
        try {
            Files.createDirectories(folder);
            if (Files.isRegularFile(offsetFile)) {
                for (String line : Files.readAllLines(offsetFile, StandardCharsets.UTF_8)) {
                    int eq = line.lastIndexOf('=');
                    if (eq > 0) {
                        offsets.put(line.substring(0, eq), Long.parseLong(line.substring(eq + 1).strip()));
                    }
                }
            }
            try (Stream<Path> files = Files.list(folder)) {
                files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("sync-") && n.endsWith(".jsonl"))
                    .map(n -> n.substring("sync-".length(), n.length() - ".jsonl".length()))
                    .filter(id -> !id.equals(origin) && !offsets.containsKey(id))
                    .forEach(backlogIds::add);
            }
        } catch (IOException | RuntimeException ex) {
            warn("Network sync: error. The plugin cannot prepare the folder " + folder + ": " + ex);
        }
    }

    private void warn(String message) {
        if (!failedOnce) {
            failedOnce = true; // one line per problem period, not one per poll
            log.warn(message);
        }
    }

    private Path ownJournal() {
        return folder.resolve("sync-" + origin + ".jsonl");
    }

    @Override
    public synchronized void send(SyncRecord record) {
        try {
            Files.writeString(ownJournal(), record.toLine() + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            warn("Network sync: error. The plugin cannot write the journal in " + folder + ": " + ex);
            return;
        }
        compactIfNeeded();
    }

    // ------------------------------------------------------------------------------------------
    // Journal layout and compaction
    // ------------------------------------------------------------------------------------------

    /** Where a journal's content starts: {@code hdr} bytes of header line, {@code prefix} bytes of summary, logical position {@code base}. */
    private record Layout(long hdr, long prefix, long base) {
        static final Layout PLAIN = new Layout(0, 0, 0);

        long logicalLength(long physicalLength) {
            return base + Math.max(0, physicalLength - hdr - prefix);
        }
    }

    private static Layout layout(byte[] head, int available) {
        if (available <= 0 || head[0] != '#') {
            return Layout.PLAIN;
        }
        int nl = -1;
        for (int i = 0; i < available; i++) {
            if (head[i] == '\n') {
                nl = i;
                break;
            }
        }
        if (nl < 0) {
            return Layout.PLAIN;
        }
        String line = new String(head, 0, nl, StandardCharsets.UTF_8);
        if (!line.startsWith(HEADER + " ")) {
            return Layout.PLAIN;
        }
        try {
            long base = -1;
            long prefix = -1;
            for (String part : line.substring(HEADER.length()).trim().split("\\s+")) {
                if (part.startsWith("base=")) {
                    base = Long.parseLong(part.substring(5));
                } else if (part.startsWith("prefix=")) {
                    prefix = Long.parseLong(part.substring(7));
                }
            }
            return base < 0 || prefix < 0 ? Layout.PLAIN : new Layout(nl + 1, prefix, base);
        } catch (NumberFormatException ex) {
            return Layout.PLAIN;
        }
    }

    private static Layout layout(RandomAccessFile in) throws IOException {
        byte[] head = new byte[160];
        in.seek(0);
        int n = in.read(head);
        return layout(head, n);
    }

    private void compactIfNeeded() {
        long limit = maxBytes.getAsLong();
        if (limit <= 0) {
            return;
        }
        try {
            Path journal = ownJournal();
            long size = Files.isRegularFile(journal) ? Files.size(journal) : 0;
            if (size <= limit || size < nextCompactionSize) {
                return;
            }
            if (size > MAX_COMPACT_BYTES) {
                warn("Network sync: notice. The journal " + journal.getFileName() + " is too large to shrink (" + size + " bytes).");
                return;
            }
            compact(journal, size);
        } catch (IOException | RuntimeException ex) {
            warn("Network sync: error. The plugin cannot shrink the journal: " + ex);
        }
    }

    /** The lowest logical position that every other (recent) server has read of this journal, or -1 when no server has read it yet. */
    private long lowestReaderPosition() {
        long lowest = -1;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (!n.startsWith("read-by-") || !n.endsWith(".txt") || n.equals(offsetFile.getFileName().toString())) {
                    continue;
                }
                if (clock.getAsLong() - Files.getLastModifiedTime(p).toMillis() > STALE_READER_MILLIS) {
                    continue;
                }
                long v = 0; // a server that has not read this journal yet is at the start of it
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    int eq = line.lastIndexOf('=');
                    if (eq > 0 && line.substring(0, eq).equals(origin)) {
                        v = Long.parseLong(line.substring(eq + 1).strip());
                    }
                }
                lowest = lowest < 0 ? v : Math.min(lowest, v);
            }
        } catch (IOException | RuntimeException ex) {
            return 0; // unsure: drop nothing
        }
        return lowest;
    }

    private void compact(Path journal, long size) throws IOException {
        byte[] all = Files.readAllBytes(journal);
        Layout old = layout(all, all.length);
        int tailStart = (int) Math.min(all.length, old.hdr() + old.prefix());
        long logicalEnd = old.logicalLength(all.length);
        long reader = lowestReaderPosition();
        long target = reader < 0 ? logicalEnd : Math.min(reader, logicalEnd);
        // cut at a line end that is not after the target
        long position = old.base();
        int cut = tailStart;
        while (cut < all.length) {
            int nl = cut;
            while (nl < all.length && all[nl] != '\n') {
                nl++;
            }
            if (nl >= all.length || position + (nl + 1 - cut) > target) {
                break; // an unfinished line or a line that is not read by everybody stays
            }
            position += nl + 1 - cut;
            cut = nl + 1;
        }
        if (cut == tailStart) {
            nextCompactionSize = size + size / 4; // nothing that every server has read: try again when it grew
            return;
        }
        // summary = old summary + dropped lines: the newest mute/unmute and the newest reset of each player
        Map<UUID, SyncRecord> mutes = new HashMap<>();
        Map<UUID, SyncRecord> resets = new HashMap<>();
        int from = (int) old.hdr();
        String text = new String(all, from, cut - from, StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            SyncRecord r = line.isBlank() ? null : SyncRecord.parse(line);
            if (r == null) {
                continue;
            }
            if (SyncRecord.MUTE.equals(r.kind()) || SyncRecord.UNMUTE.equals(r.kind())) {
                mutes.merge(r.player(), r, SharedFolderChannel::newer);
            } else if (SyncRecord.RESET.equals(r.kind())) {
                resets.merge(r.player(), r, SharedFolderChannel::newer);
            }
        }
        long now = clock.getAsLong();
        StringBuilder summary = new StringBuilder();
        mutes.values().stream().sorted(Comparator.comparingLong(SyncRecord::ts)).forEach(r -> {
            boolean expired = SyncRecord.MUTE.equals(r.kind()) && r.until() >= 0 && r.until() <= now;
            if (!expired) {
                summary.append(r.toLine()).append('\n');
            }
        });
        resets.values().stream().sorted(Comparator.comparingLong(SyncRecord::ts)).forEach(r -> summary.append(r.toLine()).append('\n'));
        byte[] summaryBytes = summary.toString().getBytes(StandardCharsets.UTF_8);
        byte[] headerBytes = (HEADER + " base=" + position + " prefix=" + summaryBytes.length + "\n").getBytes(StandardCharsets.UTF_8);
        Path temp = folder.resolve("sync-" + origin + ".jsonl.tmp");
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
            out.write(headerBytes);
            out.write(summaryBytes);
            out.write(all, cut, all.length - cut);
        }
        try {
            Files.move(temp, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }
        long newSize = headerBytes.length + summaryBytes.length + (all.length - cut);
        nextCompactionSize = newSize + newSize / 4;
        log.info("Network sync: the journal of this server is smaller now. Old size: " + size + " bytes. New size: " + newSize + " bytes.");
    }

    private static SyncRecord newer(SyncRecord a, SyncRecord b) {
        return b.ts() > a.ts() || (b.ts() == a.ts() && b.origin().compareTo(a.origin()) > 0) ? b : a;
    }

    // ------------------------------------------------------------------------------------------
    // Reading the journals of the other servers
    // ------------------------------------------------------------------------------------------

    @Override
    public synchronized void poll(Consumer<SyncRecord> sink, Consumer<SyncRecord> backlogSink) {
        compactIfNeeded();
        List<Path> journals = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().startsWith("sync-") && p.getFileName().toString().endsWith(".jsonl"))
                .sorted().forEach(journals::add);
        } catch (IOException ex) {
            warn("Network sync: error. The plugin cannot read the folder " + folder + ": " + ex);
            return;
        }
        for (Path journal : journals) {
            String name = journal.getFileName().toString();
            String id = name.substring("sync-".length(), name.length() - ".jsonl".length());
            if (id.equals(origin)) {
                continue;
            }
            boolean backlog = backlogIds.remove(id);
            long from = offsets.getOrDefault(id, 0L);
            try (RandomAccessFile in = new RandomAccessFile(journal.toFile(), "r")) {
                long length = in.length();
                Layout layout = layout(in);
                long logicalEnd = layout.logicalLength(length);
                if (from > logicalEnd) {
                    from = 0; // the journal was replaced by a shorter one: read it again
                }
                if (logicalEnd == from) {
                    offsets.putIfAbsent(id, 0L);
                    dirty = true;
                    continue;
                }
                long summaryEnd = layout.hdr() + layout.prefix();
                // a position below the base lies in the part that was replaced by the summary: read the summary, then the rest
                long start = from < layout.base() ? layout.hdr() : summaryEnd + (from - layout.base());
                long cap = 8L * 1024 * 1024 + (from < layout.base() ? layout.prefix() : 0);
                byte[] bytes = new byte[(int) Math.min(length - start, cap)];
                in.seek(start);
                in.readFully(bytes);
                int end = bytes.length;
                while (end > 0 && bytes[end - 1] != '\n') {
                    end--; // a line that is still being written
                }
                String text = new String(bytes, 0, end, StandardCharsets.UTF_8);
                for (String line : text.split("\n")) {
                    SyncRecord r = line.isBlank() ? null : SyncRecord.parse(line);
                    if (r != null && !origin.equals(r.origin())) {
                        (backlog ? backlogSink : sink).accept(r);
                    }
                }
                offsets.put(id, layout.base() + Math.max(0, start + end - summaryEnd));
                dirty = true;
            } catch (IOException | RuntimeException ex) {
                warn("Network sync: error. The plugin cannot read the journal " + journal.getFileName() + ": " + ex);
            }
        }
        if (!Files.exists(offsetFile)) {
            dirty = true; // tells the other servers that this one is a reader, so they keep what it has not read
        }
        saveOffsets();
    }

    private void saveOffsets() {
        if (!dirty) {
            return;
        }
        dirty = false;
        StringBuilder text = new StringBuilder();
        offsets.forEach((k, v) -> text.append(k).append('=').append(v).append('\n'));
        try {
            Files.writeString(offsetFile, text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            warn("Network sync: error. The plugin cannot write the file " + offsetFile.getFileName() + ": " + ex);
        }
    }

    @Override
    public synchronized void close() {
        dirty = true;
        saveOffsets();
    }

    @Override
    public String describe() {
        return "shared folder " + folder.toAbsolutePath().normalize() + " as server '" + origin + "'";
    }
}
