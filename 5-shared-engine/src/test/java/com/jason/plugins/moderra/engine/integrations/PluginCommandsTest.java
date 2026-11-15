package com.jason.plugins.moderra.engine.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.FakePlatform;
import com.jason.plugins.moderra.engine.Message;
import com.jason.plugins.moderra.engine.TestEngines;
import com.jason.plugins.moderra.engine.Verdict;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** mcMMO / Towny / Factions / Citizens / DecentHolograms command scanning. */
class PluginCommandsTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    Engine engine;
    UUID bob;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        engine = TestEngines.track(Engine.load(platform, dir, System::currentTimeMillis));
        bob = platform.join("Bob");
    }

    private Verdict cmd(String line) {
        return engine.inspect(engine.message(bob, platform.players.get(bob), Message.COMMAND, line));
    }

    @Test
    void notInstalledDoesNothing() {
        assertNull(engine.pluginCommands().extraCommandText(Set.of(), "npc rename shit"));
        assertTrue(engine.pluginCommands().active().isEmpty());
    }

    @Test
    void subWordsAndBareLabels() {
        platform.plugins.addAll(List.of("Citizens", "Towny", "mcMMO", "DecentHolograms", "FactionsUUID"));
        PluginCommands pc = engine.pluginCommands();
        assertEquals("Fred", pc.extraCommandText(Set.of(), "/npc rename Fred"));
        assertEquals("Fred the Great", pc.extraCommandText(Set.of(), "npc create Fred the Great"));
        assertNull(pc.extraCommandText(Set.of(), "/npc select 3"));
        assertNull(pc.extraCommandText(Set.of(), "/npc rename"));
        assertEquals("hello all", pc.extraCommandText(Set.of(), "/tc hello all"));
        assertEquals("hello", pc.extraCommandText(Set.of(), "/p hello"));
        assertEquals("spawn hello", pc.extraCommandText(Set.of(), "/DH LINE ADD spawn hello"));
        assertEquals("Tag", pc.extraCommandText(Set.of(), "/f tag Tag"));
        assertNull(pc.extraCommandText(Set.of("tc"), "/tc hello"));
        assertTrue(pc.active().containsAll(List.of("Citizens", "Towny", "mcMMO", "DecentHolograms", "Factions")));
    }

    @Test
    void badNpcNameIsFlaggedAndGoodOneIsNot() {
        platform.plugins.add("Citizens");
        assertTrue(cmd("/npc rename fuck").hasHits());
        assertFalse(cmd("/npc rename Guard").hasHits());
        assertFalse(cmd("/npc select fuck").hasHits());
    }

    @Test
    void switchedOff() throws Exception {
        platform.plugins.add("Citizens");
        Path file = dir.resolve("integrations.yml");
        String text = Files.readString(file);
        assertTrue(text.contains("citizens:"));
        Files.writeString(file, text.replaceAll("(?m)^(\\s*)enabled: true$", "$1enabled: false"));
        engine.reload();
        assertFalse(cmd("/npc rename fuck").hasHits());
        assertTrue(engine.pluginCommands().active().isEmpty());
    }
}
