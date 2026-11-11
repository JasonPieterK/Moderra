package com.jason.plugins.antibadwords.mod;

import static org.junit.jupiter.api.Assertions.*;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.PunishmentAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModCoreTest {
    @TempDir
    Path dir;
    final List<String> warnings = new ArrayList<>();
    final AtomicLong now = new AtomicLong(1_000_000);

    ModCore core() {
        return new ModCore(dir, "1", new ModCore.Log() {
            public void info(String m) {
            }

            public void warn(String m) {
                warnings.add(m);
            }
        }, now::get);
    }

    void edit(String from, String to) throws Exception {
        core(); // first start writes the default config.yml
        Path file = dir.resolve("config.yml");
        String text = Files.readString(file);
        assertTrue(text.contains(from), "config.yml no longer contains: " + from);
        Files.writeString(file, text.replace(from, to));
    }

    @Test
    void loadsThePluginsOwnConfigYml() {
        ModCore core = core();
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(core.matcher().size() > 100);
        AntiBadWordsSettings s = core.settings();
        assertEquals(2, s.violationsBeforePunish());
        assertTrue(s.scanChat() && s.scanCommands() && s.scanSigns() && s.scanBooks() && s.scanAnvil());
        assertTrue(s.scannedCommands().containsAll(List.of("msg", "tell", "say", "me")));
        assertTrue(s.ignoredCommands().containsAll(List.of("abw", "antibadwords", "antiwords")));
        // 6 effects + 11 attributes + 2 sounds + 2 commands, exactly one enabled by default (villager_no).
        assertEquals(21, s.punishmentActions().size());
        List<PunishmentAction> enabled = s.punishmentActions().stream().filter(PunishmentAction::enabled).toList();
        assertEquals(1, enabled.size());
        assertEquals("sound", enabled.get(0).type());
        assertEquals(List.of("entity.villager.no", "1.0", "1.0"), enabled.get(0).args());
        PunishmentAction blindness = s.punishmentActions().get(0);
        assertEquals(List.of("minecraft:blindness", "0", "3"), blindness.args());
        assertEquals("§c{PLAYER} SAID THE WORD §f{WORD}§c WHICH IS NOT ALLOWED.", s.warningBroadcastMessage());
    }

    @Test
    void matchingAndProtectionLevels() throws Exception {
        ModCore core = core();
        assertEquals("fuck", core.find("what the f.u.c.k"));
        assertNull(core.find("nice glass house"));
        assertNotNull(core.find("you idiot"));
        edit("protection-level: 3", "protection-level: 1");
        core.reload();
        assertNull(core.find("you idiot"), "level-1 words are ignored on LOW");
        assertNotNull(core.find("fuck"));
    }

    @Test
    void commandScanningRules() {
        ModCore core = core();
        assertEquals("Bob fuck", core.commandTextToScan("/msg Bob fuck"));
        assertEquals("x y", core.commandTextToScan("/Minecraft:MSG x y"));
        assertNull(core.commandTextToScan("/abw addword fuck"), "ignored command");
        assertNull(core.commandTextToScan("/gamemode creative"), "not in scanned-commands");
        assertNull(core.commandTextToScan("/home"), "no arguments");
    }

    @Test
    void warnThenPunishThenReset() {
        ModCore core = core();
        UUID id = UUID.randomUUID();
        ModCore.Outcome first = core.handleViolation(id, "Bob", "fuck", "chat");
        assertFalse(first.punish());
        assertTrue(first.broadcast().contains("Bob"));
        assertTrue(first.playerMessage().contains("§f1§e"), "REMAINING is 1: " + first.playerMessage());
        assertTrue(first.logLine().contains("violation 1/2"));
        ModCore.Outcome second = core.handleViolation(id, "Bob", "fuck", "sign");
        assertTrue(second.punish());
        assertEquals(1, second.actions().size());
        assertEquals(0, core.getViolation(id));
        ModCore.Punished done = core.finishPunishment(second, List.of(Names.soundDescription("entity.villager.no")));
        assertTrue(done.playerMessage().contains("villager no sound"), done.playerMessage());
        assertNull(done.finalCommand());
    }

    @Test
    void punishmentDisabledOnlyBlocks() throws Exception {
        edit("enabled: true\n  # How many violations", "enabled: false\n  # How many violations");
        ModCore core = core();
        UUID id = UUID.randomUUID();
        core.handleViolation(id, "Bob", "fuck", "chat");
        ModCore.Outcome second = core.handleViolation(id, "Bob", "fuck", "chat");
        assertFalse(second.punish());
        assertTrue(second.playerMessage().contains("PUNISHMENT IS CURRENTLY DISABLED"));
    }

    @Test
    void violationsExpire() throws Exception {
        edit("violations-expire-minutes: 0", "violations-expire-minutes: 5");
        ModCore core = core();
        UUID id = UUID.randomUUID();
        assertEquals(1, core.addViolation(id));
        now.addAndGet(4 * 60_000L);
        assertEquals(2, core.addViolation(id));
        now.addAndGet(6 * 60_000L);
        assertEquals(0, core.getViolation(id));
        assertEquals(1, core.addViolation(id), "old violations expired first");
    }

    @Test
    void finalCommandIsFilled() throws Exception {
        edit("final-command:\n    enabled: false\n    # Runs from the console after the actions below. Supports {player}, {word}, {source}.\n    # Example: \"lp user {player} permission settemp antibadwords.muted true 30s\"\n    command: \"\"",
            "final-command:\n    enabled: true\n    command: \"/kick {player} {word}\"");
        ModCore core = core();
        UUID id = UUID.randomUUID();
        core.handleViolation(id, "Bob", "fuck", "chat");
        ModCore.Outcome o = core.handleViolation(id, "Bob", "fuck", "chat");
        assertEquals("kick Bob fuck", core.finishPunishment(o, List.of()).finalCommand());
    }

    @Test
    void customWordsPersistAndKeepComments() throws Exception {
        ModCore core = core();
        String before = Files.readString(dir.resolve("config.yml"));
        assertTrue(core.addCustomWord("Zorblax"));
        assertFalse(core.addCustomWord("zorblax"));
        assertFalse(core.addCustomWord("x"));
        assertEquals("zorblax", core.find("hey zorblax"));
        assertEquals(List.of("zorblax"), core().listWords("custom"));
        String after = Files.readString(dir.resolve("config.yml"));
        assertEquals(before.lines().filter(l -> l.trim().startsWith("#")).count(),
            after.lines().filter(l -> l.trim().startsWith("#")).count(), "comments must survive");
        assertTrue(core.removeCustomWord("ZORBLAX"));
        assertNull(core.find("hey zorblax"));
        assertEquals(before, Files.readString(dir.resolve("config.yml")), "back to the original file");
    }

    @Test
    void brokenConfigKeepsPreviousSettings() throws Exception {
        ModCore core = core();
        int words = core.matcher().size();
        Files.writeString(dir.resolve("config.yml"), "filters: [unclosed");
        core.reload();
        assertEquals(words, core.matcher().size());
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Could not read config.yml")));
    }

    @Test
    void listWordsAndLists() {
        ModCore core = core();
        assertTrue(core.getAvailableLists().containsAll(List.of("english", "german", "custom", "active")));
        assertFalse(core.listWords("english").isEmpty());
        assertTrue(core.listWords("nope").isEmpty());
    }
}
