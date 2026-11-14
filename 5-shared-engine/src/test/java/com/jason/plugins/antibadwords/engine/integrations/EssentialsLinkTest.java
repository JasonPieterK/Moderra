package com.jason.plugins.antibadwords.engine.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.FakePlatform;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.TestEngines;
import com.jason.plugins.antibadwords.engine.Verdict;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The EssentialsX hook against a fake bridge. */
class EssentialsLinkTest {
    static final class FakeEssentials implements EssentialsBridge {
        final Map<UUID, ExternalMute> mutes = new HashMap<>();
        final Map<UUID, String> nicks = new HashMap<>();
        final Set<UUID> vanished = new HashSet<>();
        boolean refuseMute;

        @Override
        public Optional<ExternalMute> mute(UUID player) {
            return Optional.ofNullable(mutes.get(player));
        }

        @Override
        public boolean setMute(UUID player, String name, long until, String reason) {
            if (refuseMute) {
                return false;
            }
            mutes.put(player, new ExternalMute(until, reason));
            return true;
        }

        @Override
        public boolean clearMute(UUID player) {
            return mutes.remove(player) != null;
        }

        @Override
        public String nickname(UUID player) {
            return nicks.getOrDefault(player, "");
        }

        @Override
        public boolean resetNickname(UUID player) {
            return nicks.remove(player) != null;
        }

        @Override
        public boolean vanished(UUID player) {
            return vanished.contains(player);
        }
    }

    @TempDir
    Path dir;
    FakePlatform platform;
    Engine engine;
    FakeEssentials ess = new FakeEssentials();
    AtomicLong now = new AtomicLong(1_700_000_000_000L);
    UUID bob;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        engine = TestEngines.track(Engine.load(platform, dir, now::get));
        bob = platform.join("Bob");
    }

    private void set(String key, String value) throws Exception {
        Path file = dir.resolve("integrations.yml");
        String text = Files.readString(file);
        String changed = text.replaceAll("(?m)^(\s*" + key + ":).*$", "$1 " + value);
        assertTrue(!changed.equals(text) || text.contains(key + ": " + value), key);
        Files.writeString(file, changed);
        engine.reload();
    }

    private Verdict say(UUID id, String source, String text) {
        Message m = engine.message(id, platform.players.get(id), source, text);
        Verdict v = engine.inspect(m);
        engine.onViolation(m, v);
        return v;
    }

    private List<String> abw(String line) {
        List<String> out = new ArrayList<>();
        engine.commands().execute(new CommandSender() {
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
                out.add(message);
            }
        }, "abw", line.split(" "));
        return out;
    }

    @Test
    void withoutBridgeNothingChanges() {
        assertFalse(engine.essentials().active());
        assertTrue(say(bob, Message.COMMAND, "/helpop hello").allowed());
        assertTrue(say(bob, Message.COMMAND, "/helpop fuck you").allowed(), "helpop is not scanned without Essentials");
        assertEquals("Bob", engine.essentials().displayName(bob, "Bob"));
    }

    @Test
    void essentialsCommandsAreScannedOnlyWhileConnected() {
        engine.essentials().attach(ess);
        assertTrue(say(bob, Message.COMMAND, "/helpop fuck you").blocked());
        assertTrue(say(bob, Message.COMMAND, "/essentials:ac fuck you").blocked());
        assertTrue(say(bob, Message.COMMAND, "/helpop I need help").allowed());
        engine.essentials().detach();
        assertTrue(say(bob, Message.COMMAND, "/helpop fuck you").allowed());
    }

    @Test
    void ownModeIgnoresEssentialsMutes() {
        engine.essentials().attach(ess);
        ess.mutes.put(bob, new EssentialsBridge.ExternalMute(-1, "x"));
        assertTrue(say(bob, Message.CHAT, "hello").allowed());
        assertTrue(engine.muteHandler().mute(bob, "Bob", 60_000, "r", "system"));
        assertEquals("x", ess.mutes.get(bob).reason(), "Essentials untouched");
        assertEquals(1, engine.mutes().size());
    }

    @Test
    void essentialsModeMutesThereAndRespectsItsMutes() throws Exception {
        engine.essentials().attach(ess);
        set("mute-mode", "essentials");
        assertTrue(engine.muteHandler().mute(bob, "Bob", 600_000, "spam", "system"));
        assertEquals(now.get() + 600_000, ess.mutes.get(bob).until());
        assertEquals("spam", ess.mutes.get(bob).reason());
        assertEquals(0, engine.mutes().size(), "not in the built-in list");
        Verdict v = say(bob, Message.CHAT, "hello");
        assertTrue(v.blocked());
        assertFalse(v.hasHits());
        assertTrue(platform.sentTo(bob).stream().anyMatch(s -> s.contains("You are muted") && s.contains("spam")), platform.sentTo(bob).toString());
        // a mute made with Essentials' own /mute is respected too, and it runs out
        UUID carl = platform.join("Carl");
        ess.mutes.put(carl, new EssentialsBridge.ExternalMute(now.get() + 1000, null));
        assertTrue(say(carl, Message.CHAT, "hi").blocked());
        now.addAndGet(2000);
        assertTrue(say(carl, Message.CHAT, "hi").allowed());
        ess.mutes.put(carl, new EssentialsBridge.ExternalMute(0, ""));
        assertTrue(say(carl, Message.CHAT, "hi").blocked(), "permanent");
        // unmute lifts it in Essentials
        abw("unmute Bob");
        assertFalse(ess.mutes.containsKey(bob));
        assertTrue(say(bob, Message.CHAT, "hello").allowed());
    }

    @Test
    void bothModeKeepsBothAndFallsBackWhenEssentialsRefuses() throws Exception {
        engine.essentials().attach(ess);
        set("mute-mode", "both");
        assertTrue(engine.muteHandler().mute(bob, "Bob", DurationParser.PERMANENT, "", "system"));
        assertTrue(ess.mutes.containsKey(bob));
        assertEquals(1, engine.mutes().size());
        UUID carl = platform.join("Carl");
        set("mute-mode", "essentials");
        ess.refuseMute = true;
        assertTrue(engine.muteHandler().mute(carl, "Carl", 60_000, "", "system"));
        assertEquals(2, engine.mutes().size(), "built-in mute used because Essentials refused");
        assertTrue(platform.warnings.stream().anyMatch(w -> w.contains("did not accept")));
    }

    @Test
    void essentialsModeWithoutEssentialsUsesTheBuiltInMute() throws Exception {
        set("mute-mode", "essentials");
        assertTrue(platform.warnings.stream().anyMatch(w -> w.contains("not connected")), platform.warnings.toString());
        assertTrue(engine.muteHandler().mute(bob, "Bob", 60_000, "", "system"));
        assertEquals(1, engine.mutes().size());
    }

    @Test
    void nicknameShownInBroadcastsButNotInConsoleCommands() throws Exception {
        engine.essentials().attach(ess);
        ess.nicks.put(bob, "&cCoolBob");
        say(bob, Message.CHAT, "you fuck");
        assertTrue(platform.broadcasts.stream().noneMatch(b -> b.contains("CoolBob")), "off by default: " + platform.broadcasts);
        platform.broadcasts.clear();
        set("nickname-in-messages", "both");
        say(bob, Message.CHAT, "you fuck");
        assertTrue(platform.broadcasts.stream().anyMatch(b -> b.contains("CoolBob (Bob)")), platform.broadcasts.toString());
        assertFalse(platform.consoleCommands.stream().anyMatch(c -> c.contains("CoolBob")));
    }

    @Test
    void vanishedPlayersAreNotBroadcastToEveryone() {
        engine.essentials().attach(ess);
        UUID staff = platform.join("Staff", "antibadwords.notify");
        UUID carl = platform.join("Carl");
        ess.vanished.add(bob);
        say(bob, Message.CHAT, "you fuck");
        assertTrue(platform.broadcasts.isEmpty(), platform.broadcasts.toString());
        assertTrue(platform.sentTo(staff).stream().anyMatch(s -> s.contains("Bob")), platform.sentTo(staff).toString());
        assertTrue(platform.sentTo(carl).isEmpty(), platform.sentTo(carl).toString());
        ess.vanished.clear();
        say(bob, Message.CHAT, "you fuck");
        assertFalse(platform.broadcasts.isEmpty());
    }

    @Test
    void badNicknameOnJoinIsWarnedAndCanBeReset() throws Exception {
        engine.essentials().attach(ess);
        ess.nicks.put(bob, "&afuck");
        engine.onJoin(bob, "Bob");
        assertTrue(platform.sentTo(bob).stream().anyMatch(s -> s.contains("nickname is not allowed")), platform.sentTo(bob).toString());
        assertTrue(ess.nicks.containsKey(bob), "warn keeps it");
        set("nickname-action", "reset");
        engine.onJoin(bob, "Bob");
        assertFalse(ess.nicks.containsKey(bob));
        ess.nicks.put(bob, "CoolBob");
        platform.sentTo(bob).clear();
        engine.onJoin(bob, "Bob");
        assertTrue(platform.sentTo(bob).isEmpty());
        assertTrue(ess.nicks.containsKey(bob));
    }

    @Test
    void masterSwitchOffDisablesEverything() throws Exception {
        engine.essentials().attach(ess);
        set("enabled", "false");
        assertFalse(engine.essentials().active());
        assertTrue(say(bob, Message.COMMAND, "/helpop fuck you").allowed());
    }
}
