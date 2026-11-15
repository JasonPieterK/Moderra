package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.config.ConfigProblem;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Feature 10: different rules per world or per group (worlds.yml). */
class WorldProfilesTest {
    private static final String WORLDS = """
        default-profile: default
        use-permission-profiles: true
        profiles:
          default: {}
          relaxed:
            filters.protection-level: 1
            punishment.enabled: false
            filters.scan.signs: false
          strict:
            filters:
              protection-level: 3
              enabled-word-lists: [en, de]
              matching:
                partial-match: true
              whitelist: []
            punishment.violations-before-punish: 1
            messages.warning-player: "&4Strict world, {player}!"
            punishment.sounds:
              ding:
                enabled: true
                sound-name: block.note_block.pling
                volume: 1
                pitch: 2
          vip:
            filters.scan.chat: false
        worlds:
          "creative_*": relaxed
          "minecraft:the_nether": relaxed
          Lobby: strict
          "world_?": strict
        """;

    @TempDir
    Path dir;
    Fixture f;
    Engine engine;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        TestFiles.write(f.file("worlds.yml"), WORLDS);
        engine = f.engine;
        List<ConfigProblem> problems = engine.reload();
        assertTrue(problems.isEmpty(), problems.toString());
    }

    private String profileFor(String world, String dimension, String... permissions) {
        Set<String> has = Set.of(permissions);
        return engine.settings(world, dimension, has::contains).profile();
    }

    @Test
    void resolutionOrderPermissionThenWorldThenDefault() {
        assertEquals(Set.of("default", "relaxed", "strict", "vip"), engine.profileNames());
        assertEquals("default", profileFor("world", ""));
        assertEquals("default", profileFor("", ""));
        assertEquals("relaxed", profileFor("creative_plots", ""));
        assertEquals("relaxed", profileFor("CREATIVE_2", ""), "case is ignored");
        assertEquals("default", profileFor("my_creative_", ""), "the whole name must match");
        assertEquals("strict", profileFor("lobby", ""));
        assertEquals("strict", profileFor("world_x", ""));
        assertEquals("default", profileFor("world_nether", ""), "? is exactly one character");
        assertEquals("relaxed", profileFor("world_nether", "minecraft:the_nether"), "dimension names match too");
        assertEquals("vip", profileFor("lobby", "", "moderra.profile.vip"), "a permission beats the world");
        assertEquals("relaxed", profileFor("lobby", "", "moderra.profile.vip", "moderra.profile.relaxed"), "first profile in file order");
        assertEquals("strict", profileFor("lobby", "", "moderra.profile.unknown"));
    }

    @Test
    void effectiveSettingsCarryTheOverrides() {
        EffectiveSettings normal = engine.settings();
        EffectiveSettings relaxed = engine.profile("relaxed");
        EffectiveSettings strict = engine.profile("strict");
        assertEquals("default", normal.profile());
        assertEquals(3, normal.get(CoreSettings.PROTECTION_LEVEL));
        assertEquals(1, relaxed.get(CoreSettings.PROTECTION_LEVEL));
        assertEquals(false, relaxed.get(CoreSettings.PUNISHMENT_ENABLED));
        assertEquals(false, relaxed.legacy().scanSigns());
        assertTrue(relaxed.legacy().scanChat(), "everything else stays as in the normal files");
        assertEquals(normal.get(CoreSettings.WHITELIST), relaxed.get(CoreSettings.WHITELIST));

        assertEquals(List.of("en", "de", "custom"), strict.enabledLanguages());
        assertEquals(true, strict.get(CoreSettings.PARTIAL_MATCH), "nested keys work like dotted keys");
        assertEquals(List.of(), strict.get(CoreSettings.WHITELIST));
        assertEquals(1, strict.legacy().violationsBeforePunish());
        assertEquals("§4Strict world, {player}!", strict.legacy().warningPlayerMessage());
        assertEquals(20, strict.legacy().punishmentActions().size(), "the sounds section is replaced as a whole: 6 + 11 + 1 + 2");
        assertEquals("ding", strict.legacy().punishmentActions().stream().filter(a -> a.type().equals("sound")).findFirst().orElseThrow().id());

        // different word matchers per profile
        assertNotNull(normal.matcher().find("you idiot"));
        assertNull(relaxed.matcher().find("you idiot"), "level-1 word at protection level 1");
        assertNotNull(relaxed.matcher().find("fuck"));
        assertNull(normal.matcher().find("arschloch"));
        assertNotNull(strict.matcher().find("arschloch"));
        assertNull(normal.matcher().find("xxfuckxx"));
        assertNotNull(strict.matcher().find("xxfuckxx"), "partial match is on in this profile only");
        assertNull(engine.profile("nope"));
    }

    @Test
    void aMessageIsJudgedWithTheProfileOfItsWorldAndPlayer() {
        UUID bob = f.platform.join("Bob");
        UUID vip = f.platform.join("Vip", "moderra.profile.vip");

        f.platform.worlds.put(bob, "world");
        Verdict v = f.say(bob, Message.CHAT, "you idiot");
        assertTrue(v.blocked());
        assertEquals("default", v.settings().profile());

        f.platform.worlds.put(bob, "creative_1");
        assertTrue(f.say(bob, Message.CHAT, "you idiot").allowed(), "relaxed world: mild words pass");
        assertTrue(f.say(bob, Message.SIGN, "fuck").allowed(), "relaxed world: signs are not scanned");
        f.platform.sentTo(bob).clear();
        Verdict severe = f.say(bob, Message.CHAT, "fuck");
        assertTrue(severe.blocked());
        assertEquals("relaxed", severe.settings().profile());
        f.say(bob, Message.CHAT, "fuck");
        f.say(bob, Message.CHAT, "fuck");
        assertTrue(f.platform.appliedActions.isEmpty(), "relaxed world: punishment is off");
        assertTrue(f.platform.sentTo(bob).get(2).contains("No penalty is applied at this time"));

        engine.strikes().reset(bob);
        f.platform.worlds.put(bob, "world_nether");
        f.platform.dimensions.put(bob, "minecraft:the_nether");
        assertTrue(f.say(bob, Message.CHAT, "you idiot").allowed(), "matched by dimension name");

        f.platform.dimensions.remove(bob);
        f.platform.worlds.put(bob, "Lobby");
        f.platform.sentTo(bob).clear();
        f.platform.broadcasts.clear();
        Verdict strict = f.say(bob, Message.CHAT, "arschloch");
        assertEquals("strict", strict.settings().profile());
        assertEquals(List.of("sound:ding"), f.platform.appliedActions, "strict world: punished on the first violation with its own sound");
        assertTrue(f.platform.broadcasts.get(0).contains("note block pling sound"), f.platform.broadcasts.toString());

        f.platform.worlds.put(vip, "Lobby");
        assertTrue(f.say(vip, Message.CHAT, "fuck").allowed(), "vip profile by permission: chat is not scanned");
        assertTrue(f.say(vip, Message.SIGN, "fuck").blocked(), "but signs are");
    }

    @Test
    void mistakesInWorldsYmlAreReportedAndIgnored() {
        TestFiles.write(f.file("worlds.yml"), """
            default-profile: missing
            use-permission-profiles: false
            profiles:
              broken: just text
              odd:
                filters.protection-level: 77
                filters.no-such-key: 1
                punishment.clear-violations-on-quit: false
                punishment.effects: nope
            worlds:
              "a*": odd
              "b*": nowhere
            """);
        List<ConfigProblem> problems = engine.reload();
        List<String> keys = problems.stream().map(ConfigProblem::key).toList();
        assertEquals(List.of("profiles.broken", "profiles.odd.filters.protection-level", "profiles.odd.filters.no-such-key",
            "profiles.odd.punishment.clear-violations-on-quit", "profiles.odd.punishment.effects", "default-profile", "worlds.b*"), keys, problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.file().equals("worlds.yml")));
        assertTrue(problems.get(3).message().contains("cannot be different per world"));
        assertEquals("default", engine.settings().profile());
        assertEquals(3, engine.profile("odd").get(CoreSettings.PROTECTION_LEVEL), "77 is cut down to the maximum");
        assertEquals("odd", profileFor("alpha", ""));
        assertEquals("default", profileFor("beta", ""));
        assertEquals("default", profileFor("beta", "", "moderra.profile.odd"), "permission profiles are switched off");
        assertEquals(6, engine.profile("odd").legacy().punishmentActions().stream().filter(a -> a.type().equals("effect")).count());
    }

    @Test
    void theGeneratedWorldsFileHasNoProfilesAndChangesNothing() {
        Fixture fresh = new Fixture(dir.resolve("fresh"));
        Engine e = fresh.start();
        assertEquals(Set.of("default"), e.profileNames());
        assertEquals("default", e.settings("anything", "minecraft:the_end", n -> true).profile());
        assertTrue(TestFiles.read(fresh.file("worlds.yml")).contains("# Named sets of rules."));
    }
}
