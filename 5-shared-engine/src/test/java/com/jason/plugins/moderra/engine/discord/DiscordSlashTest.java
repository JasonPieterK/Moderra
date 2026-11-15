package com.jason.plugins.moderra.engine.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.FakePlatform;
import com.jason.plugins.moderra.engine.ModerationEvent;
import com.jason.plugins.moderra.engine.TestEngines;
import com.jason.plugins.moderra.engine.config.Setting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Slash commands use the same rules as the prefix commands (allow-list, switches, channels, rate limit, audit). */
class DiscordSlashTest {
    @TempDir
    Path dir;
    Engine engine;
    DiscordLink link;
    UUID bob;
    final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    final List<String> replies = new ArrayList<>();
    final List<ModerationEvent> events = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        FakePlatform platform = new FakePlatform(dir);
        bob = platform.join("Bob");
        engine = TestEngines.track(Engine.load(platform, dir, now::get));
        link = engine.discord().attachBridge(new DiscordLinkTest.FakeBridge());
        engine.events().subscribe(events::add);
        engine.set(DiscordSettings.ENABLED, true);
    }

    <T> void set(Setting<T> s, T v) throws IOException {
        engine.set(s, v);
    }

    void open(String style) throws IOException {
        set(DiscordSrvSettings.COMMAND_CHANNELS, List.of("staff"));
        set(DiscordSrvSettings.ALLOWED_USERS, List.of("42"));
        set(DiscordSrvSettings.ALLOWED_ROLES, List.of("900"));
        set(DiscordSrvSettings.MUTE, true);
        set(DiscordSrvSettings.STYLE, style);
    }

    void slash(String user, Set<String> roles, String channel, String word, String args) {
        link.onSlashCommand(new DiscordLink.Incoming("200", channel, user, "Mod" + user, roles, ""), word, args, replies::add);
    }

    DiscordLink.Decision typed(String text) {
        return link.onMessage(new DiscordLink.Incoming("200", "staff", "42", "Mod", Set.of(), text), replies::add);
    }

    @Test
    void defaultStyleIsPrefixAndSlashIsRefused() throws IOException {
        assertEquals("prefix", engine.value(DiscordSrvSettings.STYLE));
        open("prefix");
        slash("42", Set.of(), "staff", "mute", "Bob 1h");
        assertEquals(List.of(DiscordLink.SLASH_REFUSED), replies);
        assertFalse(engine.mutes().isMuted(bob));
    }

    @Test
    void slashMutesAndAuditsLikeThePrefixCommand() throws IOException {
        open("slash");
        slash("5", Set.of("900"), "staff", "mute", "Bob 1h being rude");
        assertTrue(engine.mutes().isMuted(bob), String.valueOf(replies));
        ModerationEvent audit = events.stream().filter(e -> e.kind().equals("admin-command") && e.source().equals("discord")).findFirst().orElseThrow();
        assertEquals("moderra mute Bob 1h being rude", audit.data().get("command"));
        assertEquals("Discord:Mod5(5)", audit.playerName());
        assertEquals(1, events.stream().filter(e -> e.kind().equals("admin-command")).count());
    }

    @Test
    void slashRefusesWithoutSayingWhy() throws IOException {
        open("both");
        slash("1", Set.of("1"), "staff", "mute", "Bob 1h"); // not allowed
        slash("42", Set.of(), "general", "mute", "Bob 1h"); // wrong channel
        assertEquals(List.of(DiscordLink.SLASH_REFUSED, DiscordLink.SLASH_REFUSED), replies);
        assertFalse(engine.mutes().isMuted(bob));
        assertTrue(events.stream().noneMatch(e -> e.kind().equals("admin-command")));
    }

    @Test
    void switchedOffCommandAndBadInputAreAnsweredAndNothingRuns() throws IOException {
        open("slash");
        slash("42", Set.of(), "staff", "unmute", "Bob");
        slash("42", Set.of(), "staff", "mute", "Bob;op 1h");
        slash("42", Set.of(), "staff", "addword", "evil");
        assertEquals("[Moderra] Error. The command 'unmute' is switched off on this server.", replies.get(0));
        assertEquals("[Moderra] Error. The player name is not valid.", replies.get(1));
        assertEquals(3, replies.size());
        assertFalse(engine.mutes().isMuted(bob));
    }

    @Test
    void slashSharesTheRateLimit() throws IOException {
        open("both");
        set(DiscordSrvSettings.RATE_MAX, 2);
        slash("42", Set.of(), "staff", "mute", "Bob 1h");
        typed("!moderra mute Bob 1h");
        slash("42", Set.of(), "staff", "mute", "Bob 1h");
        assertEquals("[Moderra] Notice. You send too many commands. Try again in a moment.", replies.get(replies.size() - 1));
    }

    @Test
    void styleSlashTurnsThePrefixOff() throws IOException {
        open("slash");
        assertEquals(DiscordLink.Decision.Type.ALLOW, typed("!moderra mute Bob 1h").type(), "an ordinary message now");
        assertFalse(engine.mutes().isMuted(bob));
        set(DiscordSrvSettings.STYLE, "both");
        assertEquals(DiscordLink.Decision.Type.BLOCK, typed("!moderra mute Bob 1h").type());
        assertTrue(engine.mutes().isMuted(bob));
    }
}
