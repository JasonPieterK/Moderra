package com.jason.plugins.antibadwords.mod;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModCoreTest {
    @TempDir
    Path dir;

    private ModCore core(List<String> log) {
        return new ModCore(dir, log::add);
    }

    @Test
    void createsDefaultConfigAndBlocksWords() {
        ModCore core = core(new ArrayList<>());
        assertTrue(Files.exists(dir.resolve(ModCore.FILE_NAME)));
        assertTrue(core.wordCount() > 100);
        assertEquals("fuck", core.find("what the f.u.c.k"));
        assertNull(core.find("hello there, nice glass house"));
    }

    @Test
    void punishesAfterLimitAndResets() {
        ModCore core = core(new ArrayList<>());
        UUID id = UUID.randomUUID();
        ModCore.Verdict first = core.check(id, "you idiot");
        assertNotNull(first);
        assertFalse(first.punish());
        assertEquals(1, core.violations(id));
        ModCore.Verdict second = core.check(id, "idiot again");
        assertTrue(second.punish());
        assertFalse(second.commands().isEmpty(), "the villager_no sound is enabled by default");
        assertEquals(0, core.violations(id));
        assertNull(core.check(id, "all good"));
    }

    @Test
    void protectionLevelFiltersTiers() throws Exception {
        core(new ArrayList<>()); // writes defaults
        Path file = dir.resolve(ModCore.FILE_NAME);
        Files.writeString(file, Files.readString(file).replace("\"protection-level\": 3", "\"protection-level\": 1"));
        ModCore low = core(new ArrayList<>());
        assertNull(low.find("you idiot"), "level-1 words are ignored on LOW");
        assertEquals("fuck", low.find("fuck"));
    }

    @Test
    void customWordsPersist() {
        ModCore core = core(new ArrayList<>());
        assertTrue(core.addCustomWord("Zorblax"));
        assertFalse(core.addCustomWord("zorblax"));
        assertEquals("zorblax", core.find("hey zorblax"));
        assertEquals("zorblax", core(new ArrayList<>()).find("hey zorblax"));
        assertTrue(core.removeCustomWord("zorblax"));
        assertNull(core.find("hey zorblax"));
    }

    @Test
    void brokenConfigKeepsDefaults() throws Exception {
        core(new ArrayList<>());
        Files.writeString(dir.resolve(ModCore.FILE_NAME), "{ not json");
        List<String> log = new ArrayList<>();
        ModCore core = core(log);
        assertTrue(log.stream().anyMatch(l -> l.startsWith("Could not read")));
        assertNotNull(core.config());
    }

    @Test
    void formatsColoursAndPlaceholders() {
        assertEquals("§cBob said §fx", ModCore.format("&c{PLAYER} said &f{word}", Map.of("player", "Bob", "word", "x")));
        assertEquals("a & b", ModCore.colour("a & b"));
    }
}
