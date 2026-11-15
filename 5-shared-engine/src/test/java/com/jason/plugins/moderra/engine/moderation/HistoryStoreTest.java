package com.jason.plugins.moderra.engine.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.EngineLog;
import com.jason.plugins.moderra.engine.ModerationEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryStoreTest {
    private static final long JAN_15 = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
    private static final long DAY = 86_400_000L;

    @TempDir
    Path dir;
    final AtomicLong now = new AtomicLong(JAN_15);
    final List<String> warnings = new ArrayList<>();
    final AtomicReference<HistoryStore.Options> options = new AtomicReference<>(opts(false, false, 0, 0));
    HistoryStore store;
    final UUID bob = UUID.nameUUIDFromBytes("Bob".getBytes());
    final UUID amy = UUID.nameUUIDFromBytes("Amy".getBytes());

    static HistoryStore.Options opts(boolean text, boolean redact, int retentionDays, long maxBytes) {
        return new HistoryStore.Options(true, Set.of("*"), text, redact, retentionDays, maxBytes, 5);
    }

    @BeforeEach
    void setUp() {
        EngineLog log = new EngineLog() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
                synchronized (warnings) {
                    warnings.add(message);
                }
            }
        };
        store = new HistoryStore(dir.resolve("data"), now::get, log, options::get);
    }

    @AfterEach
    void tearDown() {
        store.shutdown();
    }

    private ModerationEvent event(String kind, long time, UUID id, String name, Map<String, String> data) {
        return new ModerationEvent(kind, time, id, name, "world", "chat", data);
    }

    private ModerationEvent violation(long time, UUID id, String name, int count, String text) {
        return event("violation", time, id, name, Map.of("check", "bad-word", "detail", "fuck", "word", "fuck", "count", Integer.toString(count),
            "step", "warn", "text", text));
    }

    private Path file(String name) {
        return dir.resolve("data/history/" + name);
    }

    private List<String> lines(String name) throws IOException {
        return Files.readAllLines(file(name));
    }

    @Test
    void writesOneJsonLinePerEventWithTheFields() throws IOException {
        store.record(event("punishment", JAN_15, bob, "Bob", Map.of("check", "bad-word", "detail", "fuck", "count", "4", "step", "kick",
            "actor", "Admin", "secret-token", "hunter2")));
        store.flush();
        List<String> lines = lines("2026-01.jsonl");
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.startsWith("{") && line.endsWith("}"), line);
        for (String part : List.of("\"time\": " + JAN_15, "\"kind\": \"punishment\"", "\"uuid\": \"" + bob + "\"", "\"player\": \"Bob\"",
            "\"world\": \"world\"", "\"source\": \"chat\"", "\"check\": \"bad-word\"", "\"detail\": \"fuck\"", "\"actor\": \"Admin\"",
            "\"points\": 4", "\"step\": \"kick\"")) {
            assertTrue(line.contains(part), part + " in " + line);
        }
        assertFalse(line.contains("hunter2"), "only the known fields are written");
        assertFalse(line.contains("\"text\""), "no text by default");
        HistoryStore.Entry e = store.find(bob, 1, 10).entries().get(0);
        assertEquals("kick", e.step());
        assertEquals(4, e.points());
        assertEquals("Admin", e.actor());
    }

    @Test
    void actorDefaultsToSystemAndAnyKindIsRecorded() {
        store.record(event("mute", JAN_15, bob, "Bob", Map.of("duration", "10m", "reason", "spam", "actor", "Admin")));
        store.record(event("mute-expired", JAN_15 + 1, bob, "Bob", Map.of()));
        HistoryStore.Entry newest = store.find(bob, 1, 10).entries().get(0);
        assertEquals("mute-expired", newest.kind());
        assertEquals("system", newest.actor());
        assertEquals("spam [10m]", store.find(bob, 1, 10).entries().get(1).detail());
    }

    @Test
    void kindFilterAndDisabled() {
        options.set(new HistoryStore.Options(true, Set.of("punishment"), false, false, 0, 0, 5));
        store.record(violation(JAN_15, bob, "Bob", 1, "x"));
        store.record(event("punishment", JAN_15, bob, "Bob", Map.of()));
        assertEquals(1, store.find(null, 1, 10).total());
        options.set(new HistoryStore.Options(false, Set.of("*"), false, false, 0, 0, 5));
        store.record(event("punishment", JAN_15, bob, "Bob", Map.of()));
        assertEquals(1, store.find(null, 1, 10).total());
    }

    @Test
    void textOnlyWhenAskedAndRedacted() {
        options.set(opts(true, false, 0, 0));
        store.record(violation(JAN_15, bob, "Bob", 1, "you Fuck"));
        assertEquals("you Fuck", store.find(bob, 1, 5).entries().get(0).text());

        options.set(opts(true, true, 0, 0));
        store.record(violation(JAN_15 + 1, bob, "Bob", 2, "FUCK this fuck"));
        assertEquals("**** this ****", store.find(bob, 1, 5).entries().get(0).text());

        // disguised: the word is not in the text, so the text is not stored
        store.record(violation(JAN_15 + 2, bob, "Bob", 3, "f.u.c.k"));
        assertNull(store.find(bob, 1, 5).entries().get(0).text());
        assertEquals(3, store.find(bob, 1, 5).entries().get(0).points());
    }

    @Test
    void specialCharactersSurviveTheRoundTrip() throws IOException {
        store.record(event("admin-command", JAN_15, bob, "Bob", Map.of("detail", "a\"b\\c\nd\t  😀 §")));
        store.flush();
        assertEquals(1, lines("2026-01.jsonl").size(), "one line even with a newline in the text");
        assertEquals("a\"b\\c\nd\t  😀 §", store.find(bob, 1, 5).entries().get(0).detail());
    }

    @Test
    void rotatesByMonth() throws IOException {
        long feb = ZonedDateTime.of(2026, 2, 1, 0, 0, 5, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        store.record(violation(feb - 10_000, bob, "Bob", 1, "x"));
        store.record(violation(feb, bob, "Bob", 2, "x"));
        store.flush();
        assertEquals(1, lines("2026-01.jsonl").size());
        assertEquals(1, lines("2026-02.jsonl").size());
        // newest first across the files
        assertEquals(List.of(2, 1), store.find(bob, 1, 10).entries().stream().map(HistoryStore.Entry::points).toList());
    }

    @Test
    void paginationNewestFirst() {
        for (int i = 1; i <= 12; i++) {
            store.record(violation(JAN_15 + i, bob, "Bob", i, "x"));
            store.record(violation(JAN_15 + i, amy, "Amy", 100 + i, "x"));
        }
        HistoryStore.Page p1 = store.find(bob, 1, 5);
        HistoryStore.Page p3 = store.find(bob, 3, 5);
        assertEquals(12, p1.total());
        assertEquals(List.of(12, 11, 10, 9, 8), p1.entries().stream().map(HistoryStore.Entry::points).toList());
        assertEquals(List.of(2, 1), p3.entries().stream().map(HistoryStore.Entry::points).toList());
        assertTrue(store.find(bob, 4, 5).entries().isEmpty());
        assertEquals(List.of(112, 12, 111), store.recent(3).stream().map(HistoryStore.Entry::points).toList());
        assertEquals(24, store.find(null, 1, 1).total());
    }

    @Test
    void retentionDeletesOldMonthsOnly() throws IOException {
        Files.createDirectories(file("x").getParent());
        Files.writeString(file("2025-06.jsonl"), "{\"time\": 1}\n");
        Files.writeString(file("2025-12.jsonl"), "{\"time\": 1}\n");
        Files.writeString(file("notes.txt"), "keep me");
        options.set(opts(false, false, 60, 0)); // 60 days before Jan 15: Nov 16 - December ended Dec 31 (keep), June is gone
        store.record(violation(JAN_15, bob, "Bob", 1, "x"));
        store.flush();
        assertFalse(Files.exists(file("2025-06.jsonl")));
        assertTrue(Files.exists(file("2025-12.jsonl")));
        assertTrue(Files.exists(file("notes.txt")));
        assertTrue(Files.exists(file("2026-01.jsonl")));

        now.addAndGet(70 * DAY);
        store.cleanupNow();
        assertFalse(Files.exists(file("2025-12.jsonl")), "70 days later December ended 84 days ago");
        assertTrue(Files.exists(file("2026-01.jsonl")), "January ended 53 days ago");
    }

    @Test
    void retentionZeroKeepsEverything() throws IOException {
        Files.createDirectories(file("x").getParent());
        Files.writeString(file("2001-01.jsonl"), "{\"time\": 1}\n");
        store.record(violation(JAN_15, bob, "Bob", 1, "x"));
        store.flush();
        assertTrue(Files.exists(file("2001-01.jsonl")));
    }

    @Test
    void sizeCapDeletesOldestThenStopsWriting() throws IOException {
        Files.createDirectories(file("x").getParent());
        Files.writeString(file("2025-11.jsonl"), "x".repeat(600) + "\n");
        Files.writeString(file("2025-12.jsonl"), "x".repeat(600) + "\n");
        options.set(opts(false, false, 0, 1000));
        store.record(violation(JAN_15, bob, "Bob", 1, "x"));
        store.flush();
        assertFalse(Files.exists(file("2025-11.jsonl")), "the oldest month went first");
        assertTrue(Files.exists(file("2025-12.jsonl")));
        assertEquals(1, store.find(bob, 1, 5).total());

        options.set(opts(false, false, 0, 700)); // December alone is over the limit, and January is the current file
        for (int i = 0; i < 5; i++) {
            store.record(violation(JAN_15 + i, bob, "Bob", 2, "x"));
        }
        store.flush();
        assertFalse(Files.exists(file("2025-12.jsonl")));
        assertEquals(6, store.find(bob, 1, 20).total(), "now there is room again");

        options.set(opts(false, false, 0, 100)); // the current file alone is over the limit
        store.record(violation(JAN_15 + 9, bob, "Bob", 9, "x"));
        store.flush();
        assertEquals(6, store.find(bob, 1, 20).total(), "not written while over the limit");
        assertEquals(1, warnings.stream().filter(w -> w.contains("size limit")).count());
    }

    @Test
    void purgeRemovesOnlyThatPlayerFromAllFiles() throws IOException {
        long feb = ZonedDateTime.of(2026, 2, 3, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        store.record(violation(JAN_15, bob, "Bob", 1, "x"));
        store.record(violation(JAN_15, amy, "Amy", 1, "x"));
        store.record(violation(feb, bob, "Bob", 2, "x"));
        assertEquals(2, store.count(bob));
        assertEquals(2, store.purge(bob));
        assertEquals(0, store.count(bob));
        assertEquals(1, store.count(amy));
        assertFalse(Files.exists(file("2026-02.jsonl")), "an empty file is removed");
        assertEquals(1, lines("2026-01.jsonl").size());
        assertEquals(0, store.purge(bob));
    }

    @Test
    void damagedLinesAreSkipped() throws IOException {
        Files.createDirectories(file("x").getParent());
        Files.writeString(file("2026-01.jsonl"), "garbage\n{\"time\": \"no\"}\n\n{\"time\": 5, \"kind\": \"violation\", \"uuid\": \"" + bob + "\"}\n");
        assertEquals(1, store.find(bob, 1, 5).total());
    }

    @Test
    void shutdownWritesWhatIsQueuedAndLaterEventsAreIgnored() throws IOException {
        for (int i = 0; i < 200; i++) {
            store.record(violation(JAN_15 + i, bob, "Bob", i, "x"));
        }
        store.shutdown();
        assertEquals(200, lines("2026-01.jsonl").size());
        store.record(violation(JAN_15, bob, "Bob", 1, "x"));
        store.shutdown();
        assertEquals(200, lines("2026-01.jsonl").size());
    }

    @Test
    void manyThreadsWriteWithoutLosingOrMixingLines() throws Exception {
        int threads = 8;
        int each = 250;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> all = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread th = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < each; i++) {
                    store.record(violation(JAN_15 + i, UUID.nameUUIDFromBytes(("p" + id).getBytes()), "P" + id, i, "x"));
                }
            });
            th.start();
            all.add(th);
        }
        go.countDown();
        for (Thread th : all) {
            th.join();
        }
        store.flush();
        List<String> lines = lines("2026-01.jsonl");
        assertEquals(threads * each, lines.size());
        assertTrue(lines.stream().allMatch(l -> l.startsWith("{") && l.endsWith("}")));
        for (int t = 0; t < threads; t++) {
            assertEquals(each, store.count(UUID.nameUUIDFromBytes(("p" + t).getBytes())));
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
