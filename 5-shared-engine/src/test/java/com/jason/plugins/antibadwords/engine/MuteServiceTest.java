package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import com.jason.plugins.antibadwords.engine.moderation.MuteHandler;
import com.jason.plugins.antibadwords.engine.moderation.MuteService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The built-in mute: enforcement in inspect, expiry, persistence, offline players and the commands. */
class MuteServiceTest {
    private static final String MUTE_YML = "moderation/mute.yml";

    @TempDir
    Path dir;
    Fixture f;
    UUID bob;
    List<ModerationEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        f.engine.events().subscribe(events::add);
        bob = f.platform.join("Bob");
    }

    private List<String> run(String line, String... permissions) {
        return runAs("Console", line);
    }

    private List<String> runAs(String who, String line) {
        List<String> out = new ArrayList<>();
        f.engine.commands().execute(new CommandSender() {
            @Override
            public String name() {
                return who;
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

    private Verdict say(UUID id, String source, String text) {
        return f.engine.inspect(f.engine.message(id, f.platform.players.get(id), source, text));
    }

    private List<String> kinds() {
        return events.stream().map(ModerationEvent::kind).toList();
    }

    @Test
    void mutedPlayerIsBlockedInChatWithoutStrikes() {
        assertTrue(f.engine.muteHandler().mute(bob, "Bob", 600_000, "spam", "Alice"));
        assertTrue(f.engine.muteHandler() instanceof MuteService);
        Verdict v = say(bob, Message.CHAT, "hello everyone");
        assertTrue(v.blocked());
        assertFalse(v.hasHits(), "no hits, so the loaders count no strikes");
        List<String> sent = f.platform.sentTo(bob);
        assertTrue(sent.stream().anyMatch(s -> s.contains("You are muted") && s.contains("10m") && s.contains("spam")), sent.toString());
        assertEquals(0, f.engine.strikes().get(bob));
        assertTrue(say(f.platform.join("Carl"), Message.CHAT, "hello everyone").allowed());
    }

    @Test
    void commandsFromTheListAreBlockedOthersAreNot() {
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        assertTrue(say(bob, Message.COMMAND, "/msg Alice hi").blocked());
        assertTrue(say(bob, Message.COMMAND, "/Minecraft:ME waves").blocked());
        assertTrue(say(bob, Message.COMMAND, "/r hi").blocked());
        assertTrue(say(bob, Message.COMMAND, "/spawn").allowed());
        assertTrue(say(bob, Message.COMMAND, "/home").allowed());
    }

    @Test
    void wildcardsInTheCommandList() {
        TestFiles.edit(f.file(MUTE_YML), "- msg\n", "- msg*\n");
        f.engine.reload();
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        assertTrue(say(bob, Message.COMMAND, "/msgall hello").blocked());
        assertTrue(MuteService.matches("*", "anything"));
        assertTrue(MuteService.matches("ms*", "msg"));
        assertFalse(MuteService.matches("ms*", "tell"));
    }

    @Test
    void signsAndBooksOnlyWhenSwitchedOn() {
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        assertTrue(say(bob, Message.SIGN, "hi").allowed());
        assertTrue(say(bob, Message.BOOK, "hi").allowed());
        TestFiles.edit(f.file(MUTE_YML), "signs: false", "signs: true");
        TestFiles.edit(f.file(MUTE_YML), "books: false", "books: true");
        f.engine.reload();
        assertTrue(say(bob, Message.SIGN, "hi").blocked());
        assertTrue(say(bob, Message.SIGN_BACK, "hi").blocked());
        assertTrue(say(bob, Message.BOOK, "hi").blocked());
        assertTrue(say(bob, Message.ANVIL, "hi").allowed());
    }

    @Test
    void bypassPermissionAndSwitchedOffMuteAreNotHeldBack() {
        UUID staff = f.platform.join("Staff", MuteService.BYPASS_PERMISSION);
        f.engine.mutes().mute(staff, "Staff", DurationParser.PERMANENT, "", "system");
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        assertTrue(say(staff, Message.CHAT, "hello").allowed());
        assertTrue(say(bob, Message.CHAT, "hello").blocked());
        TestFiles.edit(f.file(MUTE_YML), "enabled: true", "enabled: false");
        f.engine.reload();
        assertTrue(say(bob, Message.CHAT, "hello").allowed(), "mute.enabled: false");
        assertFalse(f.engine.mutes().mute(bob, "Bob", 1000, "", "x"));
    }

    @Test
    void badWordsOfAMutedPlayerAreNotCountedAsStrikes() {
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        Verdict v = f.say(bob, Message.CHAT, "you are a fuck");
        assertTrue(v.blocked());
        assertEquals(0, f.engine.strikes().get(bob));
    }

    @Test
    void muteRunsOutLazilyWithMessageAndEvent() {
        f.engine.mutes().mute(bob, "Bob", 60_000, "x", "Alice");
        f.now.addAndGet(59_000);
        assertTrue(say(bob, Message.CHAT, "hi").blocked());
        f.now.addAndGet(1_000);
        assertTrue(say(bob, Message.CHAT, "hi").allowed());
        assertEquals(List.of("mute", "mute-expired"), kinds());
        assertTrue(f.platform.sentTo(bob).stream().anyMatch(s -> s.contains("has run out")));
        assertEquals(0, f.engine.mutes().size());
        assertTrue(say(bob, Message.CHAT, "hi").allowed());
        assertEquals(List.of("mute", "mute-expired"), kinds(), "expired once");
    }

    @Test
    void sweepEndsFinishedMutesOfOfflinePlayers() {
        UUID gone = UUID.randomUUID();
        f.engine.mutes().mute(gone, "Gone", 1000, "", "system");
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        f.now.addAndGet(5000);
        assertEquals(1, f.engine.mutes().sweep());
        assertEquals(1, f.engine.mutes().size());
        assertTrue(kinds().contains("mute-expired"));
        assertEquals(0, f.engine.mutes().sweep(), "a permanent mute never runs out");
    }

    @Test
    void periodicTaskIsScheduledWhileTimedMutesExist() {
        List<Long> delays = new ArrayList<>();
        Path other = dir.resolve("other");
        FakePlatform fake = new FakePlatform(other);
        Platform platform = (Platform) java.lang.reflect.Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[] {Platform.class},
            (proxy, method, args) -> {
                if (method.getName().equals("runSyncLater")) {
                    delays.add((Long) args[1]);
                    return null;
                }
                return method.invoke(fake, args);
            });
        Engine engine = TestEngines.track(Engine.load(platform, other, f.now::get));
        engine.mutes().mute(UUID.randomUUID(), "A", 100_000, "", "system");
        assertTrue(delays.contains(30_000L), delays.toString());
    }

    @Test
    void joinNoticeAndPersistenceAcrossARestart() {
        f.engine.mutes().mute(bob, "Bob", 3_600_000, "caps", "Alice");
        f.engine.shutdown();
        assertTrue(Files.exists(f.file("data/mutes.json")));
        String json = TestFiles.read(f.file("data/mutes.json"));
        assertTrue(json.contains("\"by\"") && json.contains("Alice") && json.contains("caps"), json);
        f.platform.sent.clear();
        f.now.addAndGet(600_000);
        f.start();
        assertTrue(f.engine.mutes().isMuted(bob));
        f.engine.onJoin(bob, "Bob");
        List<String> sent = f.platform.sentTo(bob);
        assertTrue(sent.stream().anyMatch(s -> s.contains("still muted") && s.contains("50m")), sent.toString());
        assertTrue(say(bob, Message.CHAT, "hello").blocked());
        MuteService.Mute m = f.engine.mutes().get(bob).orElseThrow();
        assertEquals("Alice", m.by());
        assertEquals("caps", m.reason());
    }

    @Test
    void mutesThatRanOutWhileTheServerWasOffAreGoneAfterRestart() {
        f.engine.mutes().mute(bob, "Bob", 60_000, "", "system");
        f.engine.shutdown();
        f.now.addAndGet(120_000);
        f.start();
        assertTrue(say(bob, Message.CHAT, "hello").allowed());
        assertEquals(0, f.engine.mutes().size());
    }

    @Test
    void damagedMuteFileIsReplacedByAnEmptyList() throws Exception {
        f.engine.shutdown();
        Files.writeString(f.file("data/mutes.json"), "{ not json");
        f.start();
        assertEquals(0, f.engine.mutes().size());
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("mutes.json")));
    }

    @Test
    void joinNoticeCanBeSwitchedOff() {
        TestFiles.edit(f.file(MUTE_YML), "notify-on-join: true", "notify-on-join: false");
        f.engine.reload();
        f.engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "system");
        f.platform.sent.clear();
        f.engine.onJoin(bob, "Bob");
        assertTrue(f.platform.sentTo(bob).isEmpty());
    }

    // ---- commands ----

    @Test
    void muteCommandWithDurationAndReason() {
        List<String> out = run("mute Bob 30m too loud");
        assertTrue(out.get(0).contains("Muted Bob for 30m (too loud)"), out.toString());
        MuteService.Mute m = f.engine.mutes().get(bob).orElseThrow();
        assertEquals("Console", m.by());
        assertEquals("too loud", m.reason());
        assertEquals(f.now.get() + 1_800_000, m.until());
        assertEquals(List.of("mute", "admin-command"), kinds());
        assertTrue(f.platform.sentTo(bob).stream().anyMatch(s -> s.contains("You have been muted for 30m")));
    }

    @Test
    void muteCommandWithoutDurationUsesTheDefaultAndKeepsTheWholeReason() {
        run("mute Bob 90 minutes of silence");
        MuteService.Mute m = f.engine.mutes().get(bob).orElseThrow();
        assertEquals("90 minutes of silence", m.reason());
        assertEquals(f.now.get() + 3_600_000, m.until(), "default 1h; a number without a unit is a reason word");
    }

    @Test
    void permanentMuteAndUsage() {
        run("mute Bob perm");
        assertTrue(f.engine.mutes().get(bob).orElseThrow().permanent());
        assertTrue(run("mute").get(0).contains("Usage: /abw mute <player>"));
        assertTrue(run("mute Nobody").get(0).contains("Player not found"));
    }

    @Test
    void offlinePlayersCanBeMutedAndUnmuted() {
        f.engine.onJoin(bob, "Bob");
        f.engine.onQuit(bob);
        f.platform.players.remove(bob);
        List<String> out = run("mute bob 1h");
        assertTrue(out.get(0).contains("Muted Bob") && out.get(0).contains("(offline)"), out.toString());
        f.platform.players.put(bob, "Bob");
        assertTrue(say(bob, Message.CHAT, "hi").blocked(), "muted before they come back");
        f.platform.players.remove(bob);
        out = run("unmute BOB");
        assertTrue(out.get(0).contains("Unmuted Bob"), out.toString());
        assertFalse(f.engine.mutes().isMuted(bob));
        assertEquals(List.of("mute", "admin-command", "unmute", "admin-command"), kinds());
    }

    @Test
    void mutedPlayerStaysFindableEvenWhenForgottenByTheDirectory() {
        UUID ghost = UUID.randomUUID();
        f.engine.mutes().mute(ghost, "Ghost", 3_600_000, "", "system");
        assertTrue(run("unmute Ghost").get(0).contains("Unmuted Ghost"));
    }

    @Test
    void unmuteOfAPlayerWhoIsNotMuted() {
        assertTrue(run("unmute Bob").get(0).contains("not muted"));
        assertTrue(run("unmute").get(0).contains("Usage"));
        run("mute Bob 5m");
        run("unmute Bob");
        assertTrue(f.platform.sentTo(bob).stream().anyMatch(s -> s.contains("no longer muted")));
    }

    @Test
    void mutesListIsPaginated() {
        assertTrue(run("mutes").get(0).contains("Nobody is muted"));
        for (int i = 0; i < 11; i++) {
            UUID id = f.platform.join("P" + String.format("%02d", i));
            f.engine.mutes().mute(id, "P" + String.format("%02d", i), 3_600_000, i == 0 ? "reason one" : "", "Alice");
        }
        List<String> page1 = run("mutes");
        assertEquals(9, page1.size());
        assertTrue(page1.get(0).contains("11") && page1.get(0).contains("1/2"));
        assertTrue(page1.get(1).contains("P00") && page1.get(1).contains("1h") && page1.get(1).contains("Alice") && page1.get(1).contains("reason one"));
        List<String> page2 = run("mutes 2");
        assertEquals(4, page2.size());
        assertTrue(page2.get(3).contains("P10"));
        assertTrue(run("mutes 3").get(0).contains("only 2 pages"));
        assertTrue(run("mutes x").get(0).contains("Usage"));
    }

    @Test
    void tabCompletion() {
        CommandSender console = new CommandSender() {
            public String name() {
                return "Console";
            }

            public UUID id() {
                return null;
            }

            public boolean has(String node) {
                return true;
            }

            public void send(String message) {
            }
        };
        f.platform.join("Bella");
        f.engine.onJoin(bob, "Bob");
        assertEquals(List.of("Bob", "Bella"), f.engine.commands().complete(console, new String[] {"mute", "b"}).stream().sorted(java.util.Comparator.reverseOrder()).toList());
        assertEquals(List.of("10m", "1h", "1d"), f.engine.commands().complete(console, new String[] {"mute", "Bob", "1"}));
        assertTrue(f.engine.commands().complete(console, new String[] {"mute", "Bob", "1h", ""}).isEmpty());
        f.engine.mutes().mute(bob, "Bob", 1000, "", "x");
        assertEquals(List.of("Bob"), f.engine.commands().complete(console, new String[] {"unmute", ""}));
        assertTrue(f.engine.commands().names().containsAll(Set.of("mute", "unmute", "mutes")));
    }

    @Test
    void permissionIsAntibadwordsMute() {
        List<String> out = new ArrayList<>();
        f.engine.commands().execute(new CommandSender() {
            public String name() {
                return "Joe";
            }

            public UUID id() {
                return null;
            }

            public boolean has(String node) {
                return node.equals("antibadwords.mute");
            }

            public void send(String message) {
                out.add(message);
            }
        }, "abw", new String[] {"mute", "Bob", "1m"});
        assertTrue(out.get(0).contains("Muted Bob"), out.toString());
        out.clear();
        f.engine.commands().execute(new CommandSender() {
            public String name() {
                return "Joe";
            }

            public UUID id() {
                return null;
            }

            public boolean has(String node) {
                return node.equals("antibadwords.admin");
            }

            public void send(String message) {
                out.add(message);
            }
        }, "abw", new String[] {"mute", "Bob", "1m"});
        assertTrue(out.get(0).contains("No permission"), out.toString());
        assertNotNull(f.engine.mutes());
    }

    @Test
    void ladderMuteActionUsesTheService() {
        assertTrue(f.engine.muteHandler().mute(bob, "Bob", 60_000, "ladder reason", MuteHandler.SYSTEM));
        assertEquals("ladder", events.get(0).source());
        assertEquals("system", events.get(0).data().get("by"));
    }
}
