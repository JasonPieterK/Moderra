package com.jason.plugins.moderra.engine.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.EngineLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentStrikeServiceTest {
    private static final long MINUTE = 60_000L;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final UUID EVE = UUID.fromString("00000000-0000-0000-0000-0000000000e0");

    @TempDir
    Path dir;
    Path file;
    final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    final List<String> warnings = new ArrayList<>();
    /** Saves that were asked for and have not run yet: {delay, task}. */
    final List<Map.Entry<Long, Runnable>> pending = new ArrayList<>();
    volatile PersistentStrikeService.Options options = new PersistentStrikeService.Options(0, 0, 1, 0, 5000, true);
    final EngineLog log = new EngineLog() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    };

    @BeforeEach
    void setUp() {
        file = dir.resolve("data").resolve("strikes.json");
    }

    private PersistentStrikeService service() {
        return new PersistentStrikeService(file, now::get, () -> options, log, (task, delay) -> {
            synchronized (pending) {
                pending.add(Map.entry(delay, task));
            }
        });
    }

    private void options(long expire, long decayEvery, int decayPoints, int max) {
        options = new PersistentStrikeService.Options(expire, decayEvery, decayPoints, max, 5000, true);
    }

    @Test
    void countsPerPlayerAndResets() {
        PersistentStrikeService s = service();
        assertEquals(0, s.get(BOB));
        assertEquals(1, s.add(BOB, 1));
        assertEquals(4, s.add(BOB, 3));
        assertEquals(2, s.add(EVE, 2));
        assertEquals(Map.of(BOB, 4, EVE, 2), s.all());
        s.reset(BOB);
        assertEquals(0, s.get(BOB));
        assertEquals(2, s.get(EVE));
        s.clear();
        assertEquals(Map.of(), s.all());
    }

    @Test
    void expiryForgetsEverythingAfterTheLastStrike() {
        options(5 * MINUTE, 0, 1, 0);
        PersistentStrikeService s = service();
        s.add(BOB, 1);
        now.addAndGet(4 * MINUTE);
        assertEquals(2, s.add(BOB, 1), "a new strike within the time keeps the old ones");
        now.addAndGet(5 * MINUTE);
        assertEquals(2, s.get(BOB), "exactly at the limit they still count (as before)");
        now.addAndGet(1);
        assertEquals(0, s.get(BOB));
        assertEquals(1, s.add(BOB, 1), "starts again at one");
    }

    @Test
    void decayTakesPointsAwayStepByStep() {
        options(0, 10 * MINUTE, 1, 0);
        PersistentStrikeService s = service();
        s.add(BOB, 3);
        now.addAndGet(9 * MINUTE);
        assertEquals(3, s.get(BOB));
        now.addAndGet(MINUTE);
        assertEquals(2, s.get(BOB), "one point after one interval");
        now.addAndGet(5 * MINUTE);
        assertEquals(3, s.add(BOB, 1), "a new strike does not restart the interval");
        now.addAndGet(5 * MINUTE);
        assertEquals(2, s.get(BOB));
        now.addAndGet(25 * MINUTE);
        assertEquals(0, s.get(BOB), "never below zero");
        assertEquals(1, s.add(BOB, 1));
        now.addAndGet(9 * MINUTE);
        assertEquals(1, s.get(BOB), "after reaching zero the interval starts with the next first strike");
    }

    @Test
    void decayCanTakeSeveralPointsAndWorksTogetherWithExpiry() {
        options(60 * MINUTE, 10 * MINUTE, 2, 0);
        PersistentStrikeService s = service();
        s.add(BOB, 9);
        now.addAndGet(20 * MINUTE);
        assertEquals(5, s.get(BOB));
        now.addAndGet(41 * MINUTE);
        assertEquals(0, s.get(BOB), "expired (61 minutes after the last strike) although decay alone would leave 1");
    }

    @Test
    void capLimitsTheCount() {
        options(0, 0, 1, 5);
        PersistentStrikeService s = service();
        assertEquals(4, s.add(BOB, 4));
        assertEquals(5, s.add(BOB, 4));
        assertEquals(5, s.add(BOB, Integer.MAX_VALUE));
        options(0, 0, 1, 3);
        assertEquals(3, s.get(BOB), "a lowered cap applies to existing counts");
        options(0, 0, 1, 0);
        assertEquals(Integer.MAX_VALUE, s.add(BOB, Integer.MAX_VALUE), "no cap: still no overflow");
    }

    @Test
    void savesDebouncedAndAtomically() throws IOException {
        PersistentStrikeService s = service();
        s.add(BOB, 1);
        s.add(BOB, 1);
        s.add(EVE, 1);
        assertEquals(1, pending.size(), "three changes, one save asked for");
        assertEquals(5000L, pending.get(0).getKey());
        assertFalse(Files.exists(file), "nothing is written before the delay is over");
        pending.remove(0).getValue().run();
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"" + BOB + "\"") && text.contains("\"points\": 2"), text);
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of("strikes.json"), files.map(p -> p.getFileName().toString()).toList(), "the temp file is gone");
        }
        s.add(EVE, 1);
        assertEquals(1, pending.size(), "the next change asks for the next save");
        assertTrue(s.flush());
        assertTrue(Files.readString(file).contains("\"points\": 2"));
        pending.remove(0).getValue().run(); // the late task finds nothing to do
    }

    @Test
    void survivesARestart() {
        options(0, 10 * MINUTE, 1, 0);
        PersistentStrikeService first = service();
        first.add(BOB, 5);
        first.add(EVE, 1);
        first.shutdown(true);
        assertEquals(0, first.get(BOB), "memory is released on shutdown");

        now.addAndGet(25 * MINUTE); // the server was off for a while: decay goes on
        PersistentStrikeService second = service();
        assertEquals(1, second.load(), "Eve's single point faded away while the server was off");
        assertEquals(3, second.get(BOB));
        assertEquals(0, second.get(EVE));
        assertEquals(4, second.add(BOB, 1));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void memoryOnlyModeNeverWritesByItself() {
        options = new PersistentStrikeService.Options(0, 0, 1, 0, 5000, false);
        PersistentStrikeService s = service();
        s.add(BOB, 1);
        assertTrue(pending.isEmpty());
        s.shutdown(false);
        assertFalse(Files.exists(file));
    }

    @Test
    void damagedFileIsKeptAsideAndTheServiceStartsEmpty() throws IOException {
        Files.createDirectories(file.getParent());
        String damaged = "{\"version\": 1, \"strikes\": {\"" + BOB + "\": {\"points\": 3, \"la";
        Files.writeString(file, damaged);
        PersistentStrikeService s = service();
        assertEquals(0, s.load());
        assertEquals(0, s.get(BOB));
        List<Path> copies;
        try (Stream<Path> files = Files.list(file.getParent())) {
            copies = files.filter(p -> p.getFileName().toString().startsWith("strikes.json.corrupt-")).toList();
        }
        assertEquals(1, copies.size());
        assertTrue(copies.get(0).getFileName().toString().matches("strikes\\.json\\.corrupt-\\d{8}-\\d{6}"), copies.toString());
        assertEquals(damaged, Files.readString(copies.get(0)));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("DAMAGED DATA FILE")), warnings.toString());

        // it keeps working and the next save replaces the damaged file
        s.add(BOB, 1);
        s.flush();
        PersistentStrikeService again = service();
        assertEquals(1, again.load());
        assertEquals(1, again.get(BOB));
    }

    @Test
    void otherBrokenContentIsHandledToo() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "this is not json");
        assertEquals(0, service().load());
        Files.writeString(file, "");
        assertEquals(0, service().load());
        Files.writeString(file, "{\"version\": 1, \"strikes\": {\"not-a-uuid\": {\"points\": 3, \"last\": 5}, \"" + BOB
            + "\": {\"points\": \"many\"}, \"" + EVE + "\": {\"points\": 2, \"last\": " + now.get() + "}}}");
        PersistentStrikeService s = service();
        assertEquals(1, s.load(), "the good entry is read, the two broken ones are skipped");
        assertEquals(2, s.get(EVE));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("2 entries")), warnings.toString());
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().contains(".corrupt-")).count(), "only 'not json' was damaged");
        }
    }

    @Test
    void manyThreadsAddingStrikes() throws Exception {
        PersistentStrikeService s = new PersistentStrikeService(file, now::get, () -> options, log, (task, delay) -> task.run());
        int threads = 16;
        int perThread = 500;
        List<UUID> ids = List.of(BOB, EVE, UUID.randomUUID(), UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int t = 0; t < threads; t++) {
            int thread = t;
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        s.add(ids.get((thread + i) % ids.size()), 1);
                        if (i % 50 == 0) {
                            s.get(ids.get(i % ids.size()));
                            s.flush();
                        }
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(threads * perThread, s.all().values().stream().mapToInt(Integer::intValue).sum(), "no strike was lost");
        s.flush();
        PersistentStrikeService reloaded = service();
        reloaded.load();
        assertEquals(s.all(), reloaded.all(), "the file holds the final counts");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
