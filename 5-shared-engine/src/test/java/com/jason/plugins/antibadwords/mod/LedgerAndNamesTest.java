package com.jason.plugins.antibadwords.mod;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerAndNamesTest {
    @TempDir
    Path dir;
    final UUID bob = UUID.randomUUID();

    @Test
    void stackingKeepsTheFirstOriginalAndOnlyNewestRestores() {
        AttributeLedger ledger = new AttributeLedger(dir.resolve("p.txt"));
        double original = ledger.original(bob, "movement_speed", 0.1);
        int first = ledger.register(bob, "movement_speed", original);
        double second = ledger.original(bob, "movement_speed", 0.05); // already changed once
        assertEquals(0.1, second);
        int token = ledger.register(bob, "movement_speed", second);
        assertFalse(ledger.isLatest(bob, "movement_speed", first));
        assertTrue(ledger.isLatest(bob, "movement_speed", token));
        assertEquals(0.1, ledger.take(bob, "movement_speed"));
        assertNull(ledger.take(bob, "movement_speed"));
        assertFalse(Files.exists(dir.resolve("p.txt")), "empty ledger removes the file");
    }

    @Test
    void crashLeftoversAreRestoredOnJoin() {
        Path file = dir.resolve("p.txt");
        AttributeLedger crashed = new AttributeLedger(file);
        crashed.register(bob, "max_health", 20.0);
        // the process dies here without restoring
        AttributeLedger next = new AttributeLedger(file);
        assertEquals(Map.of("max_health", 20.0), next.takeLeftovers(bob));
        assertEquals(Map.of(), next.takeLeftovers(bob));
        assertFalse(Files.exists(file));
    }

    @Test
    void takeAllRestoresEverythingForOnePlayer() {
        AttributeLedger ledger = new AttributeLedger(dir.resolve("p.txt"));
        ledger.register(bob, "armor", 0.0);
        ledger.register(bob, "attack_speed", 4.0);
        assertEquals(Map.of("armor", 0.0, "attack_speed", 4.0), ledger.takeAll(bob));
        assertEquals(List.of(), ledger.playersWithPending());
    }

    @Test
    void nameHelpersMatchThePlugin() {
        assertEquals(List.of("movement_speed", "generic_movement_speed"), Names.attributeKeys("minecraft:generic.movement_speed"));
        assertEquals(List.of("max_health"), Names.attributeKeys("MAX_HEALTH"));
        assertEquals("slowness", Names.effectKey("minecraft:slow"));
        assertEquals("glowing", Names.effectKey("glow"));
        assertEquals("villager no sound", Names.soundDescription("entity.villager.no"));
        assertEquals("anvil land sound", Names.soundDescription("minecraft:block.anvil.land"));
        assertEquals("pling sound", Names.soundDescription("pling"));
        assertEquals("mining fatigue", Names.prettyName("minecraft:mining_fatigue"));
        assertEquals("a", Names.formatPunishmentList(List.of("a")));
        assertEquals("a and b", Names.formatPunishmentList(List.of("a", "b")));
        assertEquals("a, b, and c", Names.formatPunishmentList(List.of("a", "b", "c")));
        assertEquals("punishment", Names.formatPunishmentList(List.of()));
        assertEquals("§cHi &z & §Fx".replace("§F", "§f"), Names.colour("&cHi &z & &Fx"));
    }
}
