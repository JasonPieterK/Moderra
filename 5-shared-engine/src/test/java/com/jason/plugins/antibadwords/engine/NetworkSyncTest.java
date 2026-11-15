package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.network.NetworkSettings;
import com.jason.plugins.antibadwords.engine.network.SyncRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Two engines (two servers) sharing mutes and strikes through a temp folder, and the plugin messaging transport. */
class NetworkSyncTest {
    @TempDir
    Path dir;
    Path shared;
    Fixture a;
    Fixture b;
    UUID bob;

    @BeforeEach
    void setUp() throws Exception {
        shared = dir.resolve("shared");
        a = server("a", "lobby");
        b = server("b", "survival");
        bob = UUID.nameUUIDFromBytes("Bob".getBytes());
    }

    private Fixture server(String folder, String id) throws Exception {
        Path d = dir.resolve(folder);
        Files.createDirectories(d);
        Fixture f = new Fixture(d);
        f.start();
        net(f, "mode", "shared-folder");
        net(f, "server-id", id);
        net(f, "folder", shared.toString().replace('\\', '/'));
        f.engine.reload();
        f.platform.join("Bob");
        return f;
    }

    /** Sets a key of the integrations.network section in the file of a server (the value is quoted). */
    private static void net(Fixture f, String key, String value) {
        Path file = f.file("integrations.yml");
        String text = TestFiles.read(file);
        int from = text.indexOf("network:");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^(\\s+" + java.util.regex.Pattern.quote(key) + ":).*$").matcher(text);
        assertTrue(m.find(from), key);
        TestFiles.write(file, text.substring(0, m.start()) + m.group(1) + " \"" + value + "\"" + text.substring(m.end()));
    }

    @Test
    void muteAndUnmuteTravel() {
        a.engine.mutes().mute(bob, "Bob", 600_000, "spam", "Admin");
        assertFalse(b.engine.mutes().isMuted(bob));
        b.engine.network().poll();
        assertTrue(b.engine.mutes().isMuted(bob));
        assertEquals("spam", b.engine.mutes().get(bob).orElseThrow().reason());
        // not echoed back
        assertFalse(Files.exists(shared.resolve("sync-survival.jsonl")));
        b.now.addAndGet(10);
        b.engine.mutes().unmute(bob, "Admin");
        a.engine.network().poll();
        assertFalse(a.engine.mutes().isMuted(bob));
    }

    @Test
    void newestMuteChangeWins() {
        a.engine.mutes().mute(bob, "Bob", 600_000, "old", "A");
        b.now.addAndGet(5_000);
        b.engine.mutes().mute(bob, "Bob", 600_000, "newer", "B");
        a.engine.network().poll();
        b.engine.network().poll();
        assertEquals("newer", a.engine.mutes().get(bob).orElseThrow().reason());
        assertEquals("newer", b.engine.mutes().get(bob).orElseThrow().reason());
    }

    @Test
    void strikesAddUpAndResetEverywhere() {
        a.engine.network().poll();
        b.engine.network().poll(); // both have read the (empty) journals: later records are live
        a.engine.strikes().add(bob, 2);
        b.engine.strikes().add(bob, 1);
        a.engine.network().poll();
        b.engine.network().poll();
        assertEquals(3, a.engine.strikes().get(bob));
        assertEquals(3, b.engine.strikes().get(bob));
        a.now.addAndGet(50);
        a.engine.strikes().reset(bob);
        b.engine.network().poll();
        assertEquals(0, b.engine.strikes().get(bob));
        b.engine.network().poll(); // polling again changes nothing
        assertEquals(0, b.engine.strikes().get(bob));
    }

    @Test
    void strikeOlderThanResetIsDropped() {
        a.engine.network().poll();
        b.engine.network().poll();
        b.now.addAndGet(3_000);
        b.engine.strikes().add(bob, 1);
        b.engine.strikes().reset(bob); // reset 3 s after A's clock
        a.engine.strikes().add(bob, 4); // live on B, but older than the reset
        b.engine.network().poll();
        assertEquals(0, b.engine.strikes().get(bob));
    }

    @Test
    void newServerGetsMutesButNotOldStrikes() throws Exception {
        a.engine.mutes().mute(bob, "Bob", -1, "forever", "Admin");
        a.engine.strikes().add(bob, 3);
        Fixture c = server("c", "creative");
        c.engine.network().poll();
        assertTrue(c.engine.mutes().isMuted(bob));
        assertTrue(c.engine.mutes().get(bob).orElseThrow().permanent());
        assertEquals(0, c.engine.strikes().get(bob));
        a.engine.strikes().add(bob, 1);
        c.engine.network().poll();
        assertEquals(1, c.engine.strikes().get(bob));
    }

    @Test
    void restartDoesNotRepeatRecords() {
        b.engine.network().poll();
        a.engine.strikes().add(bob, 1);
        b.engine.network().poll();
        assertEquals(1, b.engine.strikes().get(bob));
        b.engine.shutdown();
        b.start();
        b.engine.network().poll();
        // strikes are not kept across restarts by default; the old record must not be applied a second time
        assertEquals(0, b.engine.strikes().get(bob));
        a.engine.strikes().add(bob, 2);
        b.engine.network().poll();
        assertEquals(2, b.engine.strikes().get(bob));
    }

    @Test
    void syncSwitchesAndModeOff() {
        net(b, "sync-strikes", "false");
        b.engine.reload();
        b.engine.network().poll();
        a.engine.strikes().add(bob, 2);
        b.engine.network().poll();
        assertEquals(0, b.engine.strikes().get(bob));
        net(b, "mode", "off");
        b.engine.reload();
        assertEquals("off", b.engine.network().mode());
    }

    @Test
    void damagedLinesAreSkipped() throws Exception {
        Files.createDirectories(shared);
        Files.writeString(shared.resolve("sync-other.jsonl"), "garbage\n{\"k\":\"nope\"}\n"
            + SyncRecord.mute("other", 5, bob, "Bob", -1, "r", "x").toLine() + "\n{\"half");
        b.engine.network().poll();
        assertTrue(b.engine.mutes().isMuted(bob));
    }

    @Test
    void recordRoundTrip() {
        SyncRecord r = SyncRecord.mute("s", 42, bob, "Bo\"b\n\\", 99, "line1\nline2 é ", "Admin");
        String line = r.toLine();
        assertFalse(line.contains("\n"));
        assertEquals(r, SyncRecord.parse(line));
        assertNull(SyncRecord.parse("not json at all: ["));
    }

    @Test
    void pluginMessagingWithoutTransportWarns() {
        net(b, "mode", "plugin-messaging");
        b.engine.reload();
        assertEquals("off", b.engine.network().mode());
        assertTrue(b.platform.warnings.stream().anyMatch(w -> w.contains("plugin messaging")));
    }

    @Test
    void pluginMessagingTransport() throws Exception {
        Fixture p = server("p", "proxy-a");
        Fixture q = server("q", "proxy-b");
        List<byte[]> wire = new ArrayList<>();
        p.engine.network().setTransport((channel, payload) -> {
            assertEquals(NetworkSettings.CHANNEL, channel);
            wire.add(payload);
        });
        q.engine.network().setTransport((channel, payload) -> { });
        for (Fixture f : new Fixture[] {p, q}) {
            net(f, "mode", "plugin-messaging");
            f.engine.reload();
            assertEquals("plugin-messaging", f.engine.network().mode());
        }
        p.engine.mutes().mute(bob, "Bob", 600_000, "x", "Admin");
        assertEquals(2, wire.size(), "the bridge hello and the mute");
        q.engine.network().receive(wire.get(1));
        q.engine.network().poll();
        assertTrue(q.engine.mutes().isMuted(bob));
    }
}
