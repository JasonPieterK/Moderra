package com.jason.plugins.antibadwords.engine.network;

import com.jason.plugins.antibadwords.engine.EngineLog;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Shared-folder transport: every server appends its changes to its own {@code sync-<id>.jsonl} (so two servers never write
 * the same file) and tails the journals of the others. How far each foreign journal was read is kept in the file
 * {@code read-by-<id>.txt} in the same folder (so a restart neither misses nor repeats records). A journal that has
 * never been read before is a backlog: its records go to the backlog sink. Only whole lines (ending in a line break) are read.
 */
public final class SharedFolderChannel implements SyncChannel {
    private final Path folder;
    private final String origin;
    private final EngineLog log;
    private final Map<String, Long> offsets = new HashMap<>();
    private final Path offsetFile;
    /** Journals that existed when this server started but were never read: their content is history, not news. */
    private final java.util.Set<String> backlogIds = new java.util.HashSet<>();
    private boolean dirty;
    private boolean failedOnce;

    public SharedFolderChannel(Path folder, String origin, EngineLog log) {
        this.folder = folder;
        this.origin = origin;
        this.log = log;
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
            warn("Network sync: cannot prepare " + folder + ": " + ex);
        }
    }

    private void warn(String message) {
        if (!failedOnce) {
            failedOnce = true; // one line per problem period, not one per poll
            log.warn(message);
        }
    }

    @Override
    public synchronized void send(SyncRecord record) {
        try {
            Files.writeString(folder.resolve("sync-" + origin + ".jsonl"), record.toLine() + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            warn("Network sync: cannot write the journal in " + folder + ": " + ex);
        }
    }

    @Override
    public synchronized void poll(Consumer<SyncRecord> sink, Consumer<SyncRecord> backlogSink) {
        List<Path> journals = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().startsWith("sync-") && p.getFileName().toString().endsWith(".jsonl"))
                .sorted().forEach(journals::add);
        } catch (IOException ex) {
            warn("Network sync: cannot read " + folder + ": " + ex);
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
                if (from > length) {
                    from = 0; // the journal was replaced by a shorter one: read it again
                }
                if (length == from) {
                    offsets.putIfAbsent(id, 0L);
                    dirty = true;
                    continue;
                }
                byte[] bytes = new byte[(int) Math.min(length - from, 8L * 1024 * 1024)];
                in.seek(from);
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
                offsets.put(id, from + end);
                dirty = true;
            } catch (IOException | RuntimeException ex) {
                warn("Network sync: cannot read " + journal.getFileName() + ": " + ex);
            }
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
            warn("Network sync: cannot write " + offsetFile.getFileName() + ": " + ex);
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
