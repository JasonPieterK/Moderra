package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.network.NetworkSettings;
import com.jason.plugins.antibadwords.engine.network.PluginMessagingChannel;
import com.jason.plugins.antibadwords.engine.network.SharedFolderChannel;
import com.jason.plugins.antibadwords.engine.network.SyncRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Journal compaction of the shared folder (two and three servers) and the start-up check of the plugin messaging bridge. */
class NetworkJournalTest {
    @TempDir
    Path dir;

    private Fixture server(String folder, String id, Path shared, String mode, int maxKb) throws Exception {
        Path d = dir.resolve(folder);
        Files.createDirectories(d);
        Fixture f = new Fixture(d);
        f.start();
        set(f, "mode", mode);
        set(f, "server-id", id);
        set(f, "folder", shared.toString().replace('\\', '/'));
        set(f, "journal-max-kb", String.valueOf(maxKb));
        f.engine.reload();
        return f;
    }

    private static void set(Fixture f, String key, String value) {
        Path file = f.file("integrations.yml");
        String text = TestFiles.read(file);
        int from = text.indexOf("network:");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^(\\s+" + java.util.regex.Pattern.quote(key) + ":).*$").matcher(text);
        assertTrue(m.find(from), key);
        boolean number = value.matches("\\d+|true|false");
        TestFiles.write(file, text.substring(0, m.start()) + m.group(1) + " " + (number ? value : "\"" + value + "\"") + text.substring(m.end()));
    }

    private static UUID player(int i) {
        return UUID.nameUUIDFromBytes(("player" + i).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void compactionKeepsReadersAndNewcomersConsistent() throws Exception {
        Path shared = dir.resolve("shared");
        Fixture a = server("a", "lobby", shared, "shared-folder", 1);
        Fixture b = server("b", "survival", shared, "shared-folder", 0);
        b.engine.network().poll(); // b exists as a reader (position 0)
        UUID first = player(0);
        a.engine.mutes().mute(first, "P0", 3_600_000, "first", "Admin");
        b.engine.network().poll();
        assertTrue(b.engine.mutes().isMuted(first));
        Path journal = shared.resolve("sync-lobby.jsonl");
        for (int i = 1; i < 30; i++) {
            a.engine.mutes().mute(player(i), "P" + i, 3_600_000, "reason " + i, "Admin");
            b.engine.network().poll(); // b keeps up, so everything it read can be summarised
        }
        long before = 30L * 150;
        String text = Files.readString(journal);
        assertTrue(text.startsWith("#abw-journal base="), text.substring(0, Math.min(80, text.length())));
        assertTrue(Files.size(journal) < before, "journal was shrunk: " + Files.size(journal));
        // a change after the rewrite still reaches the reader at its old position, exactly once
        a.now.addAndGet(10);
        a.engine.mutes().unmute(first, "Admin");
        b.engine.network().poll();
        assertFalse(b.engine.mutes().isMuted(first));
        assertTrue(b.engine.mutes().isMuted(player(29)));
        // a new server reads the summary and the rest as history and gets every active mute
        Fixture c = server("c", "creative", shared, "shared-folder", 0);
        c.engine.network().poll();
        assertFalse(c.engine.mutes().isMuted(first), "the unmute is in the summary or the rest");
        for (int i = 1; i < 30; i++) {
            assertTrue(c.engine.mutes().isMuted(player(i)), "player " + i);
        }
    }

    @Test
    void liveReaderBehindTheSummaryStillGetsItsStrikes() throws Exception {
        Path shared = dir.resolve("shared");
        Fixture a = server("a", "lobby", shared, "shared-folder", 1);
        Fixture b = server("b", "survival", shared, "shared-folder", 0);
        b.engine.network().poll();
        UUID bob = player(1);
        a.engine.strikes().add(bob, 2);
        // b has not read yet: nothing may be dropped, however many records follow
        for (int i = 2; i < 25; i++) {
            a.engine.mutes().mute(player(i), "P" + i, 3_600_000, "reason " + i, "Admin");
        }
        assertFalse(Files.readString(shared.resolve("sync-lobby.jsonl")).startsWith("#abw-journal"));
        b.engine.network().poll();
        assertEquals(2, b.engine.strikes().get(bob));
    }

    @Test
    void oldJournalWithoutHeaderIsReadAsBefore() throws Exception {
        Path shared = dir.resolve("shared");
        Files.createDirectories(shared);
        Fixture b = server("b", "survival", shared, "shared-folder", 0);
        b.engine.network().poll();
        Files.writeString(shared.resolve("sync-old.jsonl"),
            SyncRecord.mute("old", b.now.get(), player(1), "P1", b.now.get() + 600_000, "x", "A").toLine() + "\n");
        b.engine.network().poll();
        assertTrue(b.engine.mutes().isMuted(player(1)));
    }

    @Test
    void channelDropsExpiredMutesFromTheSummaryAndKeepsUnreadLines() throws Exception {
        Path shared = dir.resolve("chan");
        Files.createDirectories(shared);
        AtomicLong clock = new AtomicLong(1_000_000);
        List<String> warnings = new ArrayList<>();
        EngineLog log = new EngineLog() {
            public void info(String m) {
            }

            public void warn(String m) {
                warnings.add(m);
            }

        };
        SharedFolderChannel writer = new SharedFolderChannel(shared, "w", log, () -> 200, clock::get);
        SharedFolderChannel reader = new SharedFolderChannel(shared, "r", log);
        reader.poll(r -> {
        }, r -> {
        });
        UUID gone = player(1);
        UUID kept = player(2);
        writer.send(SyncRecord.mute("w", 10, gone, "G", 500_000, "old", "A")); // ran out at the clock time below
        writer.send(SyncRecord.mute("w", 11, kept, "K", -1, "perm", "A"));
        List<SyncRecord> got = new ArrayList<>();
        reader.poll(got::add, got::add);
        assertEquals(2, got.size());
        for (int i = 0; i < 6; i++) { // grows beyond the limit; the reader has read the first two lines only
            writer.send(SyncRecord.reset("w", 20 + i, player(10 + i)));
        }
        String file = Files.readString(shared.resolve("sync-w.jsonl"));
        assertTrue(file.startsWith("#abw-journal base="), file);
        assertFalse(file.contains(gone.toString()), "an expired mute is not kept in the summary");
        assertTrue(file.contains(kept.toString()), "a permanent mute is kept");
        assertEquals(6, countLines(file, "\"k\":\"reset\""), "unread lines stay");
        got.clear();
        reader.poll(got::add, got::add);
        assertEquals(6, got.stream().filter(r -> r.kind().equals(SyncRecord.RESET)).count(), "the reader gets every unread line once");
        got.clear();
        reader.poll(got::add, got::add);
        assertTrue(got.isEmpty());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    private static long countLines(String text, String needle) {
        return text.lines().filter(l -> l.contains(needle)).count();
    }

    // ------------------------------------------------------------------ bridge check

    private static final class Wire implements PluginMessagingChannel.Transport {
        final AtomicBoolean online = new AtomicBoolean(false);
        final List<byte[]> sent = new ArrayList<>();

        public void send(String channel, byte[] payload) {
            sent.add(payload);
        }

        public boolean available() {
            return online.get();
        }
    }

    private Fixture messaging(String folder, String id, Wire wire) throws Exception {
        Path d = dir.resolve(folder);
        Files.createDirectories(d);
        Fixture f = new Fixture(d);
        f.start();
        set(f, "mode", "plugin-messaging");
        set(f, "server-id", id);
        f.engine.network().setTransport(wire);
        f.engine.reload();
        return f;
    }

    private static boolean bridgeWarning(Fixture f) {
        return f.platform.warnings.stream().anyMatch(w -> w.contains("no other server answered"));
    }

    @Test
    void silentBridgeWarnsOnceAfterTheHelloWasSent() throws Exception {
        Wire wire = new Wire();
        Fixture a = messaging("a", "lobby", wire);
        long timeout = a.engine.value(NetworkSettings.CHECK_TIMEOUT).toMillis();
        a.now.addAndGet(timeout * 3);
        a.engine.network().poll();
        assertFalse(bridgeWarning(a), "nobody online: the hello is still queued, so no warning yet");
        wire.online.set(true);
        a.engine.network().poll();
        assertEquals(1, wire.sent.size());
        a.now.addAndGet(timeout - 1);
        a.engine.network().poll();
        assertFalse(bridgeWarning(a));
        a.now.addAndGet(2);
        a.engine.network().poll();
        assertTrue(bridgeWarning(a));
        a.engine.network().poll();
        assertEquals(1, a.platform.warnings.stream().filter(w -> w.contains("no other server answered")).count());
    }

    @Test
    void answerFromAnotherServerPassesTheCheckAndHelloIsAnswered() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        wa.online.set(true);
        wb.online.set(true);
        Fixture a = messaging("a", "lobby", wa);
        Fixture b = messaging("b", "survival", wb);
        assertEquals(1, wa.sent.size());
        // b receives the hello of a and answers with an ack
        int before = wb.sent.size();
        b.engine.network().receive(wa.sent.get(0));
        b.engine.network().poll();
        assertEquals(before + 1, wb.sent.size());
        assertTrue(new String(wb.sent.get(wb.sent.size() - 1), StandardCharsets.UTF_8).contains("\"k\":\"ack\""));
        // a receives the ack and the check passes without a warning, however long it waits
        a.engine.network().receive(wb.sent.get(wb.sent.size() - 1));
        a.engine.network().poll();
        a.now.addAndGet(Duration.ofHours(1).toMillis());
        a.engine.network().poll();
        assertFalse(bridgeWarning(a));
        assertEquals(java.util.Set.of("survival"), a.engine.network().peers().keySet());
    }

    @Test
    void checkCanBeSwitchedOff() throws Exception {
        Wire wire = new Wire();
        wire.online.set(true);
        Path d = dir.resolve("x");
        Files.createDirectories(d);
        Fixture f = new Fixture(d);
        f.start();
        set(f, "mode", "plugin-messaging");
        set(f, "check-bridge", "false");
        f.engine.network().setTransport(wire);
        f.engine.reload();
        assertEquals(0, wire.sent.size());
        f.now.addAndGet(Duration.ofHours(1).toMillis());
        f.engine.network().poll();
        assertFalse(bridgeWarning(f));
    }
}
