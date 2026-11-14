package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.integrations.BanService;
import com.jason.plugins.antibadwords.engine.moderation.Ladder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ban plugin commands for tempban/ipban and Bedrock names. */
class IntegrationsTest {
    private static final String LADDER = "moderation/ladder.yml";
    private static final String INTEGRATIONS = "integrations.yml";

    @TempDir
    Path dir;
    Fixture f;
    final List<String> mutes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
    }

    private void restart() {
        f.start();
        f.engine.setMuteHandler((player, name, millis, reason, actor) -> mutes.add(name + " " + millis + " " + reason));
    }

    @Test
    void durationFormats() {
        assertEquals("1d", BanService.formatDuration(86_400_000L, false));
        assertEquals("1h30m", BanService.formatDuration(5_400_000L, false));
        assertEquals("1d2h3m4s", BanService.formatDuration(93_784_000L, false));
        assertEquals("90m", BanService.formatDuration(5_400_000L, true));
        assertEquals("1d", BanService.formatDuration(86_400_000L, true));
        assertEquals("1s", BanService.formatDuration(10, false), "at least one second");
        assertEquals("2s", BanService.formatDuration(1_001, false), "rounded up");
    }

    @Test
    void actionsAreParsed() {
        Ladder.Action tempban = Ladder.action("tempban 1h30m Too rude");
        assertEquals(Ladder.TEMPBAN, tempban.type());
        assertEquals(List.of("5400000", "Too rude"), tempban.args());
        assertEquals(List.of(""), Ladder.action("ipban").args());
        assertThrows(IllegalArgumentException.class, () -> Ladder.action("tempban"));
        assertThrows(IllegalArgumentException.class, () -> Ladder.action("tempban perm"));
    }

    @Test
    void autoDetectsThePluginAndWritesTheCommand() {
        UUID bob = f.platform.join("Bob");
        assertEquals("vanilla", f.engine.bans().provider());
        f.platform.plugins.add("Essentials");
        assertEquals("essentials", f.engine.bans().provider());
        f.engine.bans().tempban(bob, "Bob", 5_400_000L, "Bad language").late().run();
        assertEquals(List.of("tempban Bob 1h30m Bad language"), f.platform.consoleCommands);

        f.platform.plugins.add("LibertyBans");
        assertEquals("libertybans", f.engine.bans().provider());
        f.platform.plugins.add("AdvancedBan");
        assertEquals("advancedban", f.engine.bans().provider());
        f.platform.consoleCommands.clear();
        f.engine.bans().tempban(bob, "Bob", 5_400_000L, "x").late().run();
        assertEquals(List.of("tempban Bob 90m x"), f.platform.consoleCommands, "AdvancedBan reads one unit");
        f.platform.plugins.add("LiteBans");
        f.platform.consoleCommands.clear();
        f.engine.bans().tempban(bob, "Bob", 86_400_000L, "x\ny").late().run();
        assertEquals(List.of("tempban Bob 1d x y -s"), f.platform.consoleCommands);
        f.platform.consoleCommands.clear();
        f.engine.bans().ipban(bob, "Bob", "x").late().run();
        assertEquals(List.of("ipban Bob x -s"), f.platform.consoleCommands);
    }

    @Test
    void explicitProviderAndEditableTemplate() {
        UUID bob = f.platform.join("Bob");
        TestFiles.edit(f.file(INTEGRATIONS), "provider: auto", "provider: custom");
        TestFiles.edit(f.file(INTEGRATIONS), "tempban: \"\"", "tempban: \"ban-temp {player} {seconds} {uuid} {reason}\"");
        restart();
        BanService.Result result = f.engine.bans().tempban(bob, "Bob", 60_000L, "r");
        assertEquals("a tempban (1m)", result.applied());
        result.late().run();
        assertEquals(List.of("ban-temp Bob 60 " + bob + " r"), f.platform.consoleCommands);
    }

    @Test
    void vanillaFallsBackToKickWithAWarning() {
        UUID bob = f.platform.join("Bob");
        BanService.Result result = f.engine.bans().tempban(bob, "Bob", 60_000L, "Rude");
        assertEquals("a kick", result.applied());
        result.late().run();
        assertEquals(List.of("Bob: Rude"), f.platform.kicks);
        assertTrue(f.platform.consoleCommands.isEmpty());
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("tempban") && w.contains("fallback: kick")), f.platform.warnings.toString());
    }

    @Test
    void vanillaFallbackMuteAndNone() {
        UUID bob = f.platform.join("Bob");
        TestFiles.edit(f.file(INTEGRATIONS), "fallback: kick", "fallback: mute");
        restart();
        assertEquals("a mute (1m)", f.engine.bans().tempban(bob, "Bob", 60_000L, "Rude").applied());
        assertEquals(List.of("Bob 60000 Rude"), mutes);
        TestFiles.edit(f.file(INTEGRATIONS), "fallback: mute", "fallback: none");
        restart();
        BanService.Result none = f.engine.bans().tempban(bob, "Bob", 60_000L, "Rude");
        assertEquals(null, none.applied());
        assertEquals(null, none.late());
    }

    @Test
    void vanillaIpBanUsesBanIp() {
        UUID bob = f.platform.join("Bob");
        f.engine.bans().ipban(bob, "Bob", "Evil").late().run();
        assertEquals(List.of("ban-ip Bob Evil"), f.platform.consoleCommands);
    }

    @Test
    void ladderStepRunsTempbanLast() {
        TestFiles.edit(f.file(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        TestFiles.edit(f.file(LADDER), "steps: {}", """
            steps:
                  out:
                    at: 1
                    message: "&cGone for {duration}."
                    actions: ["tempban 1d Swearing", "ipban Swearing"]""");
        f.platform.plugins.add("LiteBans");
        restart();
        assertEquals(List.of(), f.engine.problems());
        UUID bob = f.platform.join("Bob");
        assertTrue(f.say(bob, Message.CHAT, "fuck this").blocked());
        assertEquals(List.of("tempban Bob 1d Swearing -s", "ipban Bob Swearing -s"), f.platform.consoleCommands);
        assertEquals(List.of("§cGone for 1d."), f.platform.sentTo(bob));
    }

    @Test
    void bedrockDetectionAndNames() {
        assertFalse(f.engine.bedrock().enabled());
        assertFalse(f.engine.bedrock().isBedrock(".Steve"));
        f.platform.plugins.add("floodgate");
        assertTrue(f.engine.bedrock().enabled());
        assertTrue(f.engine.bedrock().isBedrock(".Steve"));
        assertFalse(f.engine.bedrock().isBedrock("Steve"));
        assertEquals("Steve", f.engine.bedrock().filterName(".Steve"));
        assertEquals("Bob", f.engine.bedrock().filterName("Bob"));
        assertEquals(".Steve_Smith", f.engine.bedrock().commandName(".Steve Smith"));
        TestFiles.edit(f.file(INTEGRATIONS), "strip-prefix-before-filter: true", "strip-prefix-before-filter: false");
        restart();
        assertEquals(".Steve", f.engine.bedrock().filterName(".Steve"));
        TestFiles.edit(f.file(INTEGRATIONS), "prefix: \".\"", "prefix: \"*\"");
        restart();
        assertTrue(f.engine.bedrock().isBedrock("*Alex"));
        assertFalse(f.engine.bedrock().isBedrock(".Alex"));
    }

    @Test
    void bedrockOffByModeIgnoresThePlugin() {
        f.platform.plugins.add("Geyser-Spigot");
        assertTrue(f.engine.bedrock().enabled());
        TestFiles.edit(f.file(INTEGRATIONS), "mode: auto", "mode: \"off\"");
        restart();
        assertFalse(f.engine.bedrock().enabled());
    }

    @Test
    void joinNameCheckSeesTheNameWithoutPrefix() {
        f.platform.plugins.add("floodgate");
        TestFiles.edit(f.file("config.yml"), "join-names: false", "join-names: true");
        restart();
        UUID id = f.platform.join(".fuckhead one");
        Verdict verdict = f.engine.onJoin(id, ".fuckhead one");
        assertFalse(verdict.allowed());
        assertEquals("fuck", verdict.primary().detail());
        UUID fine = f.platform.join(".Steve Smith");
        assertTrue(f.engine.onJoin(fine, ".Steve Smith").allowed());
    }

    @Test
    void playersWithPrefixAndSpacesAreFoundAndStored() {
        f.platform.plugins.add("floodgate");
        UUID id = UUID.nameUUIDFromBytes("x".getBytes());
        f.engine.onJoin(id, ".Steve Smith");
        assertEquals(id, f.engine.resolvePlayer(".Steve Smith").orElseThrow().id());
        assertEquals(id, f.engine.resolvePlayer("steve smith").orElseThrow().id(), "prefix may be left out");
        assertEquals(id, f.engine.resolvePlayer("Steve_Smith").orElseThrow().id(), "underscore for the space");
        assertEquals(id, f.engine.resolvePlayer(".Steve_Smith").orElseThrow().id());
        assertTrue(f.engine.resolvePlayer("Nobody").isEmpty());
        f.engine.shutdown();
        assertTrue(TestFiles.read(f.file("data/players.json")).contains(".Steve Smith"));
        restart();
        assertEquals(id, f.engine.resolvePlayer("Steve_Smith").orElseThrow().id(), "after a restart");
    }

    @Test
    void onlineBedrockPlayerIsFoundWithoutPrefix() {
        f.platform.plugins.add("floodgate");
        UUID id = f.platform.join(".Alex");
        assertEquals(id, f.engine.resolvePlayer("Alex").orElseThrow().id());
    }
}
