package com.jason.plugins.antibadwords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PunishmentParserTest {
    private static List<PunishmentAction> parse(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return PunishmentParser.parse(config, Logger.getAnonymousLogger());
    }

    private static PunishmentAction find(List<PunishmentAction> actions, String id) {
        return actions.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void shippedConfigParsesCompletely() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        List<PunishmentAction> actions = PunishmentParser.parse(config, Logger.getAnonymousLogger());

        // every entry in the shipped config must produce a complete action
        assertEquals(6, actions.stream().filter(a -> a.type().equals("effect")).count());
        assertEquals(11, actions.stream().filter(a -> a.type().equals("attribute")).count());
        assertEquals(2, actions.stream().filter(a -> a.type().equals("sound")).count());
        assertEquals(2, actions.stream().filter(a -> a.type().equals("command")).count());
        for (PunishmentAction a : actions) {
            assertEquals(a.type().equals("command") ? 1 : 3, a.args().size(), a.id());
        }
        assertEquals(List.of("minecraft:slowness", "2", "3"), find(actions, "slowness").args());
        assertEquals(List.of("minecraft:movement_speed", "0.05", "8"), find(actions, "reduce_speed").args());
        assertEquals(List.of("entity.villager.no", "1.0", "1.0"), find(actions, "villager_no").args());
        assertTrue(find(actions, "villager_no").enabled());
        assertFalse(find(actions, "blindness").enabled());
        assertEquals(List.of("kick {player} Please watch your language!"), find(actions, "kick").args());
    }

    @Test
    void legacyFormatStringsAreParsedFromTheRight() {
        List<PunishmentAction> actions = parse("""
            punishment:
              effects:
                old:
                  enabled: true
                  format: "minecraft:blindness-1-5"
              attributes:
                neg:
                  enabled: true
                  format: "minecraft:generic.max_health--2.5-10"
              commands:
                c:
                  enabled: true
                  format: "say hi-there {player}"
            """);
        assertEquals(List.of("minecraft:blindness", "1", "5"), find(actions, "old").args());
        assertEquals(List.of("minecraft:generic.max_health", "-2.5", "10"), find(actions, "neg").args());
        assertEquals(List.of("say hi-there {player}"), find(actions, "c").args());
    }

    @Test
    void incompleteEntriesAreSkipped() {
        List<PunishmentAction> actions = parse("""
            punishment:
              effects:
                broken:
                  enabled: true
                  effect-name: "minecraft:blindness"
            """);
        assertTrue(actions.isEmpty());
    }
}
