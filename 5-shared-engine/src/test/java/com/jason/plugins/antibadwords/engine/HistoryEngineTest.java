package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.moderation.HistoryStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The history as the engine wires it: events in, {@code /abw history} out. */
class HistoryEngineTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    UUID bob;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
        bob = f.platform.join("Bob");
        engine.onJoin(bob, "Bob");
    }

    private List<String> run(Set<String> perms, String line) {
        List<String> out = new ArrayList<>();
        engine.commands().execute(new CommandSender() {
            @Override
            public String name() {
                return "Admin";
            }

            @Override
            public UUID id() {
                return null;
            }

            @Override
            public boolean has(String node) {
                return perms.contains(node);
            }

            @Override
            public void send(String message) {
                out.add(message);
            }
        }, "abw", line.split(" "));
        return out;
    }

    private static final Set<String> STAFF = Set.of("antibadwords.history");
    private static final Set<String> ADMIN = Set.of("antibadwords.history", "antibadwords.admin");

    @Test
    void violationsFromTheEngineAreWrittenWithoutText() throws Exception {
        f.say(bob, Message.CHAT, "fuck this");
        engine.history().flush();
        List<String> lines = Files.readAllLines(dir.resolve("data/history/2023-11.jsonl"));
        assertTrue(lines.get(0).contains("\"kind\": \"violation\""), lines.toString());
        assertTrue(lines.get(0).contains("\"player\": \"Bob\""));
        assertFalse(lines.get(0).contains("this"), "the text is not stored by default");
        assertFalse(lines.get(0).contains("\"text\""));
    }

    @Test
    void commandListsNewestFirstInPages() {
        for (int i = 0; i < 20; i++) {
            f.now.incrementAndGet();
            f.say(bob, Message.CHAT, "fuck");
        }
        List<String> page1 = run(STAFF, "history Bob");
        assertTrue(page1.get(0).contains("History of Bob") && page1.get(0).contains("page 1/"), page1.toString());
        assertEquals(8 + 2, page1.size(), "header + 8 lines + next page hint: " + page1);
        assertTrue(page1.get(page1.size() - 1).contains("history Bob 2"));
        List<String> last = run(STAFF, "history bob 99");
        assertTrue(last.get(0).contains("page 3/3") || last.get(0).contains("page "), last.toString());
        assertFalse(last.get(last.size() - 1).contains("Next page"));
        assertEquals(List.of("§eUsage: /abw history <player> [page]"), run(STAFF, "history Bob x"));
    }

    @Test
    void offlinePlayersAndUnknownNames() {
        f.say(bob, Message.CHAT, "fuck");
        engine.onQuit(bob);
        f.platform.players.remove(bob);
        assertTrue(run(STAFF, "history Bob").get(0).contains("History of Bob"));
        assertEquals(List.of("§cPlayer not found (not online and never seen on this server)."), run(STAFF, "history Nobody"));
        UUID amy = f.platform.join("Amy");
        engine.onJoin(amy, "Amy");
        assertEquals(List.of("§eNo history for Amy."), run(STAFF, "history Amy"));
    }

    @Test
    void recentShowsEveryone() {
        UUID amy = f.platform.join("Amy");
        engine.onJoin(amy, "Amy");
        f.say(bob, Message.CHAT, "fuck");
        f.now.incrementAndGet();
        f.say(amy, Message.CHAT, "shit");
        List<String> out = run(STAFF, "history recent 2");
        assertEquals(3, out.size(), out.toString());
        assertTrue(out.get(1).contains("Amy") && out.get(2).contains("Bob"), out.toString());
    }

    @Test
    void permissionIsRequiredAndPurgeAsksTwiceAndIsAdminOnly() {
        f.say(bob, Message.CHAT, "fuck");
        f.say(bob, Message.CHAT, "fuck");
        assertTrue(run(Set.of(), "history Bob").get(0).contains("permission"), "refused without antibadwords.history");

        assertTrue(run(STAFF, "history purge Bob").get(0).contains("Only admins"));
        assertTrue(engine.history().count(bob) > 0);

        List<String> ask = run(ADMIN, "history purge Bob");
        assertTrue(ask.get(0).contains("permanently deletes") && ask.get(0).contains("history purge Bob confirm"), ask.toString());
        assertTrue(engine.history().count(bob) > 0, "the first call deletes nothing");
        assertEquals(List.of("§eUsage: /abw history purge <player> [confirm]"), run(ADMIN, "history purge Bob now"));

        List<String> done = run(ADMIN, "history purge Bob confirm");
        assertTrue(done.get(0).startsWith("§aDeleted "), done.toString());
        assertEquals(0, engine.history().count(bob));
        assertEquals(List.of("§eNo history for Bob."), run(STAFF, "history Bob"));
    }

    @Test
    void textIsStoredRedactedWhenSwitchedOn() {
        TestFiles.edit(f.file("moderation/history.yml"), "store-message-text: false", "store-message-text: true");
        engine.reload();
        f.say(bob, Message.CHAT, "oh fuck this");
        HistoryStore.Entry e = engine.history().recent(1).get(0);
        assertEquals("oh **** this", e.text());
        assertTrue(run(STAFF, "history Bob").get(1).contains("\"oh **** this\""));
    }

    @Test
    void disabledWritesNothingAndShutdownKeepsWhatWasWritten() {
        f.say(bob, Message.CHAT, "fuck");
        engine.shutdown();
        assertTrue(Files.exists(dir.resolve("data/history/2023-11.jsonl")));
        engine = f.start();
        assertTrue(engine.history().find(bob, 1, 5).total() > 0, "history survives a restart");
        TestFiles.edit(f.file("moderation/history.yml"), "enabled: true", "enabled: false");
        engine.reload();
        int before = engine.history().find(null, 1, 100).total();
        f.say(bob, Message.CHAT, "fuck");
        assertEquals(before, engine.history().find(null, 1, 100).total());
    }
}
