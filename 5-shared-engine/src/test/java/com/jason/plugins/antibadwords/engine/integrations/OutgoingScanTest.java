package com.jason.plugins.antibadwords.engine.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.FakePlatform;
import com.jason.plugins.antibadwords.engine.TestEngines;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScan.Kind;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutgoingScanTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    Engine engine;
    OutgoingScan scan;
    AtomicLong clock = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        engine = TestEngines.track(Engine.load(platform, dir, clock::get));
        scan = new OutgoingScan(engine);
    }

    private void on(String action) throws Exception {
        engine.set(OutgoingScanSettings.ENABLED, true);
        engine.set(OutgoingScanSettings.ACTION, action);
    }

    @Test
    void offByDefault() {
        assertFalse(scan.enabled());
        assertEquals(Kind.PASS, scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", true).kind());
        assertTrue(platform.infos.stream().noneMatch(s -> s.startsWith("Outgoing scan")));
    }

    @Test
    void logOnlyKeepsThePacket() throws Exception {
        on("log");
        assertEquals(Kind.PASS, scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", true).kind());
        assertTrue(platform.infos.stream().anyMatch(s -> s.startsWith("Outgoing scan: a system-chat packet for Bob")));
        assertEquals(Kind.PASS, scan.handle("system-chat", "Bob", "{\"text\":\"hello\"}", true).kind());
    }

    @Test
    void ownMessagesAreNeverTouched() throws Exception {
        on("cancel");
        String own = "{\"text\":\"[AntiBadWords] \",\"extra\":[{\"text\":\"Message blocked: shit\"}]}";
        assertEquals(Kind.PASS, scan.handle("system-chat", "Bob", own, true).kind());
    }

    @Test
    void cancelAndMask() throws Exception {
        on("cancel");
        assertEquals(Kind.CANCEL, scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", true).kind());
        on("mask");
        OutgoingScan.Outcome out = scan.handle("system-chat", "Bob", "{\"text\":\"a \",\"color\":\"red\",\"extra\":[{\"text\":\"shit\"}]}", true);
        assertEquals(Kind.REWRITE, out.kind());
        assertTrue(out.json().contains("\"color\":\"red\""));
        assertFalse(out.json().contains("shit"));
        // the same packet where the caller cannot write: logged, unchanged
        assertEquals(Kind.PASS, scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", false).kind());
    }

    @Test
    void sourceListAndNoStrikes() throws Exception {
        on("cancel");
        engine.set(OutgoingScanSettings.SOURCES, java.util.List.of("player-chat"));
        assertEquals(Kind.PASS, scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", true).kind());
        assertEquals(Kind.CANCEL, scan.handle("player-chat", "Bob", "{\"text\":\"you shit\"}", true).kind());
        assertTrue(platform.kicks.isEmpty());
    }

    @Test
    void logLimitPerMinute() throws Exception {
        on("log");
        engine.set(OutgoingScanSettings.LOG_LIMIT, 2);
        for (int i = 0; i < 5; i++) {
            scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", true);
        }
        assertEquals(2, platform.infos.stream().filter(s -> s.startsWith("Outgoing scan: a system-chat")).count());
        clock.addAndGet(61_000);
        scan.handle("system-chat", "Bob", "{\"text\":\"you shit\"}", true);
        assertTrue(platform.infos.stream().anyMatch(s -> s.contains("3 more hits")));
    }

    @Test
    void chatJsonRoundTrip() {
        assertEquals("ab", ChatJson.plain("{\"text\":\"a\",\"extra\":[\"b\"]}"));
        assertEquals("plain", ChatJson.plain("\"plain\""));
        assertEquals("not json", ChatJson.plain("not json"));
        String json = "{\"text\":\"x\\\"y\",\"bold\":true,\"clickEvent\":{\"action\":\"open_url\",\"value\":\"u\"}}";
        assertEquals(json, ChatJson.mapText(json, s -> s));
        assertEquals("{\"text\":\"X\\\"Y\",\"bold\":true,\"clickEvent\":{\"action\":\"open_url\",\"value\":\"u\"}}",
            ChatJson.mapText(json, String::toUpperCase));
        assertEquals("[\"ab\",{\"text\":\"c\"}]", ChatJson.mapText("[\"AB\",{\"text\":\"C\"}]", String::toLowerCase));
        assertEquals("a b", ChatJson.plain("{\"translate\":\"k\",\"with\":[\"a \",{\"text\":\"b\"}]}"));
    }
}
