package com.jason.plugins.antibadwords.engine.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.EngineLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDirectoryTest {
    private static final long DAY = 86_400_000L;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final UUID EVE = UUID.fromString("00000000-0000-0000-0000-0000000000e0");

    @TempDir
    Path dir;
    final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    final AtomicLong forgetAfter = new AtomicLong(0);
    final List<String> warnings = new ArrayList<>();
    final List<Runnable> pending = new ArrayList<>();

    private PlayerDirectory directory() {
        EngineLog log = new EngineLog() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
                warnings.add(message);
            }
        };
        return new PlayerDirectory(dir.resolve("players.json"), now::get, () -> 1000L, forgetAfter::get, log, (task, delay) -> pending.add(task));
    }

    @Test
    void findsOfflinePlayersByNameAfterARestart() {
        PlayerDirectory d = directory();
        d.seen(BOB, "Bob");
        now.addAndGet(1000);
        d.seen(EVE, "Eve \"the\" \\ Bedrock_1");
        assertEquals(1, pending.size(), "one save for both");
        d.flush();

        PlayerDirectory again = directory();
        assertEquals(2, again.load());
        assertEquals(BOB, again.find("Bob").orElseThrow().id());
        assertEquals(BOB, again.find("bOB").orElseThrow().id(), "case does not matter");
        assertEquals(1_700_000_000_000L, again.find("Bob").orElseThrow().lastSeen());
        assertEquals("Eve \"the\" \\ Bedrock_1", again.get(EVE).orElseThrow().name(), "odd names survive the file");
        assertTrue(again.find("Nobody").isEmpty());
        assertTrue(again.find("").isEmpty());
        assertEquals(List.of("Bob"), again.names("b", 10));
        assertEquals(2, again.names("", 10).size());
        assertEquals(1, again.names("", 1).size());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void renamesAndReusedNames() {
        PlayerDirectory d = directory();
        d.seen(BOB, "Bob");
        now.addAndGet(DAY);
        d.seen(BOB, "Robert");
        assertTrue(d.find("Bob").isEmpty(), "the old name is gone");
        assertEquals(BOB, d.find("Robert").orElseThrow().id());
        now.addAndGet(DAY);
        d.seen(EVE, "robert");
        assertEquals(EVE, d.find("robert").orElseThrow().id(), "exact spelling wins");
        assertEquals(BOB, d.find("Robert").orElseThrow().id());
        assertEquals(EVE, d.find("ROBERT").orElseThrow().id(), "otherwise the one seen last");

        long before = d.get(BOB).orElseThrow().lastSeen();
        d.learn(BOB, "Robert");
        assertEquals(before, d.get(BOB).orElseThrow().lastSeen(), "learn does nothing for a known name");
        now.addAndGet(DAY);
        d.touch(BOB);
        assertEquals(now.get(), d.get(BOB).orElseThrow().lastSeen());
        d.touch(UUID.randomUUID()); // unknown: ignored
        assertEquals(2, d.size());
    }

    @Test
    void forgetsPlayersNotSeenForLong() {
        PlayerDirectory d = directory();
        d.seen(BOB, "Bob");
        now.addAndGet(20 * DAY);
        d.seen(EVE, "Eve");
        d.flush();
        now.addAndGet(15 * DAY);
        forgetAfter.set(30 * DAY);
        PlayerDirectory again = directory();
        assertEquals(1, again.load());
        assertTrue(again.find("Bob").isEmpty());
        assertTrue(again.find("Eve").isPresent());
    }

    @Test
    void damagedFileIsKeptAside() throws IOException {
        Files.writeString(dir.resolve("players.json"), "[1, 2");
        PlayerDirectory d = directory();
        assertEquals(0, d.load());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().startsWith("players.json.corrupt-")).count());
        }
        assertTrue(warnings.stream().anyMatch(w -> w.contains("DAMAGED DATA FILE")));
        d.seen(BOB, "Bob");
        d.flush();
        assertEquals(1, directory().load());
    }
}
