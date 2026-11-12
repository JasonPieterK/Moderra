package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.PunishmentAction;
import com.jason.plugins.antibadwords.engine.config.PunishmentParser;
import com.jason.plugins.antibadwords.engine.config.YamlTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Moved here from the plugin together with the parser (it used to need Bukkit's YamlConfiguration). */
class PunishmentParserTest {
    private final List<String> warnings = new ArrayList<>();

    private List<PunishmentAction> parse(String yaml) {
        YamlTree tree = YamlTree.parse(yaml);
        return PunishmentParser.parse(tree.getMap("punishment.effects"), tree.getMap("punishment.attributes"),
            tree.getMap("punishment.sounds"), tree.getMap("punishment.commands"), warnings::add);
    }

    private static PunishmentAction find(List<PunishmentAction> actions, String id) {
        return actions.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void shippedDefaultsParseCompletely() {
        List<PunishmentAction> actions = parse(TestFiles.legacyDefaultConfig());
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
        assertTrue(warnings.isEmpty());
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
    void incompleteEntriesAreSkippedWithAWarning() {
        List<PunishmentAction> actions = parse("""
            punishment:
              effects:
                broken:
                  enabled: true
                  effect-name: "minecraft:blindness"
                off_and_broken:
                  enabled: false
                not_a_section: 5
            """);
        assertTrue(actions.isEmpty());
        assertEquals(List.of("punishment.effects.broken is enabled but incomplete; it will be ignored."), warnings);
    }
}
