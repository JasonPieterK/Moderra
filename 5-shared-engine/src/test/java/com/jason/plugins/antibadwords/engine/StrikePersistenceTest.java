package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.engine.command.Chat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The persistent strike store and the player directory as the engine wires them. */
class StrikePersistenceTest {
    private static final String LADDER = "moderation/ladder.yml";

    @TempDir
    Path dir;
    Fixture f;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
    }

    private List<String> run(String line) {
        List<String> out = new ArrayList<>();
        f.engine.commands().execute(new CommandSender() {
            @Override
            public String name() {
                return "Console";
            }

            @Override
            public UUID id() {
                return null;
            }

            @Override
            public boolean has(String node) {
                return true;
            }

            @Override
            public void send(String message) {
                out.add(message);
            }
        }, "abw", line.split(" "));
        return out;
    }

    private void keepStrikes() {
        f.start();
        TestFiles.edit(f.file(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        TestFiles.edit(f.file(LADDER), "violations-before-punish: 2", "violations-before-punish: 10");
    }

    /** Join as a loader reports it. */
    private UUID join(String name) {
        UUID id = f.platform.join(name);
        f.engine.onJoin(id, name);
        return id;
    }

    private void quit(UUID id) {
        f.engine.onQuit(id);
        f.platform.players.remove(id);
    }

    @Test
    void defaultInstallBehavesAsBeforeAndKeepsNothing() {
        Engine engine = f.start();
        UUID bob = join("Bob");
        engine.strikes().add(bob, 1);
        assertFalse(Files.exists(f.file("data/strikes.json")), "with clear-on-quit (default) strikes are not written");
        engine.shutdown();
        engine = f.start();
        assertEquals(0, engine.strikes().get(bob));
        assertFalse(Files.exists(f.file("data/strikes.json")));
        assertTrue(Files.exists(f.file("data/players.json")), "the player list is always kept");
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());
    }

    @Test
    void strikesSurviveQuitAndRestartWhenClearOnQuitIsOff() {
        keepStrikes();
        Engine engine = f.start();
        UUID bob = join("Bob");
        engine.strikes().add(bob, 3);
        quit(bob);
        engine.shutdown();

        f.now.addAndGet(3_600_000L);
        engine = f.start();
        assertEquals(3, engine.strikes().get(bob), "read from data/strikes.json");
        assertTrue(f.platform.infos.stream().anyMatch(i -> i.contains("Loaded the strikes of 1 players")), f.platform.infos.toString());

        // offline lookups by name
        assertEquals(List.of(Chat.info("Violations of §fBob§7: §f3§7 of §f10§7§7 (offline)")), run("violations bob"));
        assertEquals(List.of(Chat.ok("The violations of §fBob§7 are reset.§7 (offline)")), run("resetviolations Bob"));
        assertEquals(0, engine.strikes().get(bob));
        assertEquals(List.of(Chat.error("Player not found (not online and never seen on this server).")), run("violations Nobody"));
        engine.shutdown();
        assertEquals(0, f.start().strikes().get(bob), "the reset was saved too");
    }

    @Test
    void savingCanBeSwitchedOff() {
        keepStrikes();
        TestFiles.edit(f.file(LADDER), "save-to-disk: true", "save-to-disk: false");
        Engine engine = f.start();
        UUID bob = join("Bob");
        engine.strikes().add(bob, 3);
        quit(bob);
        assertEquals(3, engine.strikes().get(bob));
        engine.shutdown();
        assertFalse(Files.exists(f.file("data/strikes.json")));
        assertEquals(0, f.start().strikes().get(bob));
    }

    @Test
    void decayAndCapComeFromTheSettings() {
        keepStrikes();
        TestFiles.edit(f.file(LADDER), "every: \"0s\"", "every: 10m");
        TestFiles.edit(f.file(LADDER), "max-points: 0", "max-points: 4");
        Engine engine = f.start();
        assertTrue(engine.problems().isEmpty(), engine.problems().toString());
        UUID bob = join("Bob");
        assertEquals(4, engine.strikes().add(bob, 9), "cap");
        f.now.addAndGet(20 * 60_000L);
        assertEquals(2, engine.strikes().get(bob), "two intervals, one point each");
        engine.shutdown();
        f.now.addAndGet(10 * 60_000L);
        assertEquals(1, f.start().strikes().get(bob), "decay goes on while the server is off");
    }

    @Test
    void damagedStrikeFileDoesNotStopTheEngine() {
        keepStrikes();
        TestFiles.write(f.file("data/strikes.json"), "{\"version\": 1, \"strikes\": {{{");
        TestFiles.write(f.file("data/players.json"), "\u0000\u0001garbage");
        Engine engine = f.start();
        assertEquals(2, f.platform.warnings.stream().filter(w -> w.contains("DAMAGED DATA FILE")).count(), f.platform.warnings.toString());
        UUID bob = join("Bob");
        assertEquals(1, engine.strikes().add(bob, 1));
        assertTrue(f.say(bob, Message.CHAT, "hello there").allowed());
    }

    @Test
    void directoryFollowsJoinQuitAndViolations() {
        keepStrikes();
        Engine engine = f.start();
        UUID bob = join("Bob");
        assertEquals(f.now.get(), engine.players().find("Bob").orElseThrow().lastSeen());
        f.now.addAndGet(5000);
        quit(bob);
        assertEquals(f.now.get(), engine.players().get(bob).orElseThrow().lastSeen(), "quit updates last seen");

        // a loader that never reported the join: the first counted violation makes the player known
        UUID eve = f.platform.join("Eve");
        f.say(eve, Message.CHAT, "you are shit");
        assertEquals(eve, engine.players().find("eve").orElseThrow().id());
        assertEquals(List.of(Chat.info("Violations of §fEve§7: §f1§7 of §f10§7")), run("violations Eve"));
        assertEquals(eve, engine.resolvePlayer("Eve").orElseThrow().id());
        assertEquals(bob, engine.resolvePlayer("BOB").orElseThrow().id());
        assertTrue(engine.resolvePlayer("Nobody").isEmpty());
        assertTrue(engine.isOnline(eve));
        assertFalse(engine.isOnline(bob));
    }
}
