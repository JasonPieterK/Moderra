package com.jason.plugins.antibadwords.engine.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.FakePlatform;
import com.jason.plugins.antibadwords.engine.ModerationEvent;
import com.jason.plugins.antibadwords.engine.config.Setting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The DiscordSRV hook logic against a fake bridge (no Discord, no DiscordSRV). */
class DiscordLinkTest {
    /** Records what the engine asks the Discord side to do. */
    static final class FakeBridge implements DiscordBridge {
        final List<String> channelMessages = new ArrayList<>();
        final List<String> notified = new ArrayList<>();
        final List<String> knownChannels = new ArrayList<>(List.of("alerts", "staff-log"));
        boolean rewrite;

        @Override
        public String name() {
            return "FakeSRV 1.0";
        }

        @Override
        public boolean sendToChannel(String channelName, String text) {
            if (!knownChannels.contains(channelName)) {
                return false;
            }
            channelMessages.add(channelName + "|" + text);
            return true;
        }

        @Override
        public void notifyUser(String discordUserId, String text) {
            notified.add(discordUserId + "|" + text);
        }

        @Override
        public boolean canRewrite() {
            return rewrite;
        }
    }

    @TempDir
    Path dir;
    FakePlatform platform;
    Engine engine;
    FakeBridge bridge;
    DiscordLink link;
    final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    final List<String> replies = new ArrayList<>();
    final List<ModerationEvent> events = new ArrayList<>();
    UUID bob;

    @BeforeEach
    void start() throws IOException {
        platform = new FakePlatform(dir);
        bob = platform.join("Bob");
        engine = com.jason.plugins.antibadwords.engine.TestEngines.track(Engine.load(platform, dir, now::get));
        bridge = new FakeBridge();
        link = engine.discord().attachBridge(bridge);
        engine.addCustomWord("zorkword");
        engine.events().subscribe(events::add);
        engine.set(DiscordSettings.ENABLED, true);
    }

    @Test
    void masterSwitchAndModeOffSwitchEverythingOff() throws IOException {
        set(DiscordSrvSettings.INBOUND_ENABLED, true);
        set(DiscordSettings.ENABLED, false);
        assertEquals(DiscordLink.Decision.Type.ALLOW, say("zorkword").type());
        set(DiscordSettings.ENABLED, true);
        set(DiscordSettings.MODE, DiscordSettings.MODE_OFF);
        assertEquals(DiscordLink.Decision.Type.ALLOW, say("zorkword").type());
        set(DiscordSettings.MODE, DiscordSettings.MODE_AUTO);
        assertEquals(DiscordLink.Decision.Type.BLOCK, say("zorkword").type());
    }

    <T> void set(Setting<T> setting, T value) throws IOException {
        engine.set(setting, value);
    }

    DiscordLink.Incoming msg(String channel, String user, Set<String> roles, String text) {
        return new DiscordLink.Incoming("100", channel, user, "Mod" + user, roles, text);
    }

    DiscordLink.Decision say(String text) {
        return link.onMessage(msg("general", "7", Set.of(), text), replies::add);
    }

    // --------------------------------------------------------------------- (a) alerts

    @Test
    void modeAutoHandsWebhooksOverAndAlertsGoToTheChannel() throws IOException {
        set(DiscordSettings.ENABLED, true);
        set(DiscordSettings.ALERTS_URL, "http://127.0.0.1:1/api/webhooks/1/tok");
        assertFalse(engine.discord().active(), "webhooks are suppressed while DiscordSRV carries the alerts");
        engine.events().publish(new ModerationEvent("punishment", 1L, bob, "Bob", "world", "chat", Map.of("step", "s1", "count", "3")));
        assertEquals(1, bridge.channelMessages.size());
        assertTrue(bridge.channelMessages.get(0).startsWith("alerts|**[punishment]** Bob was punished"), bridge.channelMessages.get(0));
    }

    @Test
    void webhookModeKeepsWebhooksAndSendsNothingToDiscordSrv() throws IOException {
        set(DiscordSettings.ENABLED, true);
        set(DiscordSettings.MODE, DiscordSettings.MODE_WEBHOOK);
        set(DiscordSettings.ALERTS_URL, "http://127.0.0.1:1/api/webhooks/1/tok");
        assertTrue(engine.discord().active());
        engine.events().publish(new ModerationEvent("punishment", 1L, bob, "Bob", "world", "chat", Map.of()));
        assertTrue(bridge.channelMessages.isEmpty());
    }

    @Test
    void discordsrvModeAndDetachRestoreWebhooks() throws IOException {
        set(DiscordSettings.ENABLED, true);
        set(DiscordSettings.MODE, DiscordSettings.MODE_DISCORDSRV);
        assertFalse(engine.discord().active());
        engine.discord().detachBridge();
        set(DiscordSettings.MODE, DiscordSettings.MODE_AUTO);
        assertTrue(engine.discord().active(), "no bridge: auto means webhooks again");
    }

    @Test
    void routingAndLogChannelAndUnknownChannelWarning() throws IOException {
        set(DiscordSettings.ENABLED, true);
        set(DiscordSrvSettings.LOG_CHANNEL, "staff-log");
        engine.events().publish(new ModerationEvent("violation", 1L, bob, "Bob", "world", "chat", Map.of("word", "x")));
        assertEquals(1, bridge.channelMessages.size(), "violation is only in the log routing (*)");
        assertTrue(bridge.channelMessages.get(0).startsWith("staff-log|"));
        set(DiscordSrvSettings.ALERT_CHANNEL, "nope");
        engine.events().publish(new ModerationEvent("punishment", 1L, bob, "Bob", "world", "chat", Map.of()));
        assertTrue(platform.warnings.stream().anyMatch(w -> w.contains("'nope'")));
    }

    // --------------------------------------------------------------------- (b) inbound

    @Test
    void inboundIsOffByDefault() {
        assertEquals(DiscordLink.Decision.Type.ALLOW, say("zorkword").type());
    }

    @Test
    void blocksBadWordNotifiesSenderAndPublishesEvent() throws IOException {
        set(DiscordSrvSettings.INBOUND_ENABLED, true);
        assertEquals(DiscordLink.Decision.Type.ALLOW, say("hello there").type());
        assertEquals(DiscordLink.Decision.Type.BLOCK, say("you zorkword").type());
        assertEquals(1, bridge.notified.size());
        assertTrue(bridge.notified.get(0).startsWith("7|"));
        ModerationEvent e = events.stream().filter(x -> x.kind().equals("violation")).findFirst().orElseThrow();
        assertEquals("discord-in", e.source());
        assertEquals("block", e.data().get("action"));
        assertEquals("Discord:Mod7", e.playerName());
        assertEquals(0, engine.strikes().get(UUID.nameUUIDFromBytes("discord:7".getBytes())), "no strikes for Discord users");
    }

    @Test
    void logOnlyDeliversButRecords() throws IOException {
        set(DiscordSrvSettings.INBOUND_ENABLED, true);
        set(DiscordSrvSettings.INBOUND_ACTION, DiscordSrvSettings.ACTION_LOG_ONLY);
        assertEquals(DiscordLink.Decision.Type.ALLOW, say("zorkword").type());
        assertTrue(bridge.notified.isEmpty());
        assertEquals("log-only", events.stream().filter(x -> x.kind().equals("violation")).findFirst().orElseThrow().data().get("action"));
    }

    @Test
    void censorNeedsABridgeThatCanRewrite() throws IOException {
        set(DiscordSrvSettings.INBOUND_ENABLED, true);
        set(DiscordSrvSettings.INBOUND_ACTION, DiscordSrvSettings.ACTION_CENSOR);
        assertEquals(DiscordLink.Decision.Type.BLOCK, say("a zorkword b").type(), "cannot rewrite: blocked instead");
        bridge.rewrite = true;
        platform.rewritable.add("discord-in");
        DiscordLink.Decision d = say("a zorkword b");
        // whether the word list censors or blocks depends on its own setting; both outcomes are fine, but never unchanged
        assertTrue(d.type() == DiscordLink.Decision.Type.REPLACE ? !d.text().contains("zorkword") : d.type() == DiscordLink.Decision.Type.BLOCK);
    }

    @Test
    void channelListExceptionsAndBypass() throws IOException {
        set(DiscordSrvSettings.INBOUND_ENABLED, true);
        set(DiscordSrvSettings.INBOUND_CHANNELS, List.of("general", "100x"));
        set(DiscordSrvSettings.INBOUND_EXCEPT, List.of("Offtopic"));
        set(DiscordSrvSettings.BYPASS_ROLES, List.of("555"));
        set(DiscordSrvSettings.BYPASS_USERS, List.of("9"));
        assertEquals(DiscordLink.Decision.Type.BLOCK, link.onMessage(msg("general", "7", Set.of("1"), "zorkword"), replies::add).type());
        assertEquals(DiscordLink.Decision.Type.ALLOW, link.onMessage(msg("random", "7", Set.of(), "zorkword"), replies::add).type());
        assertEquals(DiscordLink.Decision.Type.ALLOW, link.onMessage(msg("general", "7", Set.of("555"), "zorkword"), replies::add).type());
        assertEquals(DiscordLink.Decision.Type.ALLOW, link.onMessage(msg("general", "9", Set.of(), "zorkword"), replies::add).type());
        set(DiscordSrvSettings.INBOUND_CHANNELS, List.of("*"));
        assertEquals(DiscordLink.Decision.Type.ALLOW, link.onMessage(msg("offtopic", "7", Set.of(), "zorkword"), replies::add).type());
    }

    @Test
    void notificationCanBeSwitchedOff() throws IOException {
        set(DiscordSrvSettings.INBOUND_ENABLED, true);
        set(DiscordSrvSettings.NOTIFY_SENDER, false);
        say("zorkword");
        assertTrue(bridge.notified.isEmpty());
    }

    // --------------------------------------------------------------------- (c) commands

    void openCommands() throws IOException {
        set(DiscordSrvSettings.COMMAND_CHANNELS, List.of("staff"));
        set(DiscordSrvSettings.ALLOWED_ROLES, List.of("900"));
        set(DiscordSrvSettings.ALLOWED_USERS, List.of("42"));
    }

    DiscordLink.Decision cmd(String user, Set<String> roles, String text) {
        return link.onMessage(new DiscordLink.Incoming("200", "staff", user, "Mod" + user, roles, text), replies::add);
    }

    @Test
    void commandsAreOffAndFailClosedByDefault() throws IOException {
        // nothing configured: the prefix is just chat and nothing runs
        assertEquals(DiscordLink.Decision.Type.ALLOW, link.onMessage(new DiscordLink.Incoming("200", "staff", "42", "x", Set.of("900"),
            "!abw reload"), replies::add).type());
        openCommands();
        // channel + user allowed but the command itself is off: told so, nothing run
        assertEquals(DiscordLink.Decision.Type.BLOCK, cmd("42", Set.of(), "!abw mute Bob 1h spam").type());
        assertEquals(List.of("The command 'mute' is switched off on this server."), replies);
        assertFalse(engine.mutes().isMuted(bob));
    }

    @Test
    void unlistedUserGetsNoAnswerAndNothingRuns() throws IOException {
        openCommands();
        set(DiscordSrvSettings.MUTE, true);
        assertEquals(DiscordLink.Decision.Type.BLOCK, cmd("1", Set.of("1"), "!abw mute Bob 1h").type(), "still not relayed");
        assertTrue(replies.isEmpty());
        assertFalse(engine.mutes().isMuted(bob));
        assertTrue(events.stream().noneMatch(e -> e.kind().equals("admin-command")));
    }

    @Test
    void emptyAllowListsMeanNobody() throws IOException {
        set(DiscordSrvSettings.COMMAND_CHANNELS, List.of("*"));
        set(DiscordSrvSettings.MUTE, true);
        cmd("42", Set.of("900"), "!abw mute Bob 1h");
        assertFalse(engine.mutes().isMuted(bob));
        assertTrue(replies.isEmpty());
    }

    @Test
    void mutesThroughTheRealCommandAndAuditsWithTheDiscordUser() throws IOException {
        openCommands();
        set(DiscordSrvSettings.MUTE, true);
        set(DiscordSrvSettings.UNMUTE, true);
        cmd("5", Set.of("900"), "!abw mute Bob 1h being rude");
        assertTrue(engine.mutes().isMuted(bob), String.valueOf(replies));
        assertEquals(1, replies.size());
        assertFalse(replies.get(0).contains("§"));
        ModerationEvent audit = events.stream().filter(e -> e.kind().equals("admin-command") && e.source().equals("discord")).findFirst().orElseThrow();
        assertEquals("Discord:Mod5(5)", audit.playerName());
        assertEquals("abw mute Bob 1h being rude", audit.data().get("command"));
        assertEquals("5", audit.data().get("discord-user"));
        cmd("42", Set.of(), "!abw unmute Bob");
        assertFalse(engine.mutes().isMuted(bob));
    }

    @Test
    void otherCommandsWorkAndStayInsideTheirOwnPermission() throws IOException {
        openCommands();
        set(DiscordSrvSettings.STRIKES, true);
        set(DiscordSrvSettings.CHECK, true);
        set(DiscordSrvSettings.RELOAD, true);
        cmd("42", Set.of(), "!abw strikes Bob");
        assertTrue(replies.get(0).contains("Bob"), replies.get(0));
        cmd("42", Set.of(), "!abw check this is zorkword");
        assertNotNull(replies.get(1));
        cmd("42", Set.of(), "!abw reload");
        assertTrue(replies.get(2).contains("reloaded"), replies.get(2));
        // a command that is not on the list cannot be reached even though the registry knows it
        cmd("42", Set.of(), "!abw addword evil");
        assertTrue(replies.get(3).startsWith("Commands you can use here: strikes, check, reload"), replies.get(3));
        assertFalse(engine.customWords().contains("evil"));
    }

    @Test
    void playerNamesAreResolvedSafely() throws IOException {
        openCommands();
        set(DiscordSrvSettings.MUTE, true);
        set(DiscordSrvSettings.HISTORY, true);
        cmd("42", Set.of(), "!abw mute Bob;op 1h");
        cmd("42", Set.of(), "!abw mute @everyone 1h");
        cmd("42", Set.of(), "!abw mute Nobody 1h");
        cmd("42", Set.of(), "!abw history purge");
        cmd("42", Set.of(), "!abw mute");
        assertEquals("That is not a valid player name.", replies.get(0));
        assertEquals("That is not a valid player name.", replies.get(1));
        assertTrue(replies.get(2).startsWith("Player not found"));
        assertEquals("That is not a valid player name.", replies.get(3));
        assertTrue(replies.get(4).startsWith("Usage: mute <player>"));
        assertEquals(0, engine.mutes().size());
    }

    @Test
    void reasonTextIsCleaned() throws IOException {
        openCommands();
        set(DiscordSrvSettings.MUTE, true);
        cmd("42", Set.of(), "!abw mute Bob 1h bad§4 reason\u0007 here");
        assertEquals("bad 4 reason here", engine.mutes().get(bob).orElseThrow().reason());
    }

    @Test
    void rateLimitPerUser() throws IOException {
        openCommands();
        set(DiscordSrvSettings.RELOAD, true);
        set(DiscordSrvSettings.RATE_MAX, 2);
        cmd("42", Set.of(), "!abw reload");
        cmd("42", Set.of(), "!abw reload");
        cmd("42", Set.of(), "!abw reload");
        assertTrue(replies.get(2).startsWith("Slow down"));
        cmd("43", Set.of("900"), "!abw reload");
        assertTrue(replies.get(3).contains("reloaded"), "another user has their own budget");
        now.addAndGet(31_000);
        cmd("42", Set.of(), "!abw reload");
        assertTrue(replies.get(4).contains("reloaded"));
    }

    @Test
    void answersCannotPingAnybody() throws IOException {
        openCommands();
        set(DiscordSrvSettings.CHECK, true);
        cmd("42", Set.of(), "!abw check @everyone zorkword");
        assertFalse(replies.get(0).contains("@everyone"));
    }

    @Test
    void prefixNeedsAWordBoundaryAndCanBeChanged() throws IOException {
        openCommands();
        set(DiscordSrvSettings.RELOAD, true);
        assertEquals(DiscordLink.Decision.Type.ALLOW, cmd("42", Set.of(), "!abwx reload").type());
        set(DiscordSrvSettings.PREFIX, "?mod");
        assertEquals(DiscordLink.Decision.Type.BLOCK, cmd("42", Set.of(), "?MOD reload").type());
        assertTrue(replies.get(0).contains("reloaded"));
    }

    @Test
    void statusMentionsTheBridge() {
        assertTrue(engine.discord().status().stream().anyMatch(l -> l.contains("connected (FakeSRV 1.0)")));
        engine.discord().detachBridge();
        assertTrue(engine.discord().status().stream().anyMatch(l -> l.contains("not connected")));
    }
}
