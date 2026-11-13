package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.ConfigEntry;
import com.jason.plugins.antibadwords.engine.config.ConfigStore;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.DefaultFiles;
import com.jason.plugins.antibadwords.engine.config.DurationText;
import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import com.jason.plugins.antibadwords.engine.config.WorldProfiles;
import com.jason.plugins.antibadwords.engine.config.YamlTree;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettingsRegistryTest {
    private static SettingsRegistry registry() {
        SettingsRegistry r = new SettingsRegistry();
        Engine.registerSettings(r);
        return r;
    }

    @Test
    void everySettingOfTheOldConfigIsRegistered() {
        SettingsRegistry r = registry();
        YamlTree old = YamlTree.parse(TestFiles.legacyDefaultConfig());
        Set<String> missing = new HashSet<>();
        collectLeaves("", old.root(), r, missing);
        assertEquals(Set.of(), missing, "old config keys without a registry entry");
        // and the defaults are the old defaults (except the language list, which uses the new codes)
        for (Setting<?> s : r.settings()) {
            Object oldValue = old.get(s.key());
            if (s == CoreSettings.SCANNED_COMMANDS) {
                // new installs scan more commands; migrated installs keep the old list (LegacyMigrationTest)
                assertEquals(s.parse(oldValue).value(), CoreSettings.LEGACY_SCANNED_COMMANDS);
                assertTrue(CoreSettings.SCANNED_COMMANDS.defaultValue().containsAll(CoreSettings.LEGACY_SCANNED_COMMANDS));
            } else if (oldValue != null && s != CoreSettings.ENABLED_LANGUAGES) {
                assertEquals(s.parse(oldValue).value(), s.defaultValue(), s.key());
                assertNull(s.parse(oldValue).problem(), s.key());
            }
        }
        for (Section section : List.of(CoreSettings.EFFECTS, CoreSettings.ATTRIBUTES, CoreSettings.SOUNDS, CoreSettings.COMMANDS)) {
            assertEquals(old.getMap(section.key()), ConfigStore.defaultSection(section), section.key());
        }
    }

    private static void collectLeaves(String prefix, Map<String, Object> map, SettingsRegistry r, Set<String> missing) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (key.equals("word-lists") || r.entry(key) != null) {
                continue; // word lists moved to wordlists/*.yml
            }
            if (e.getValue() instanceof Map<?, ?> child) {
                collectLeaves(key, YamlTree.stringKeys(child), r, missing);
            } else {
                missing.add(key);
            }
        }
    }

    @Test
    void everyEntryIsDescribedAndOwnedByAFile() {
        SettingsRegistry r = registry();
        assertEquals(List.of("config.yml", "moderation/ladder.yml", "worlds.yml", "filters/spam.yml", "filters/links.yml", "filters/evasion.yml",
            "filters/confusables.yml"), r.files());
        assertEquals(List.of("General", "Languages", "Scanning", "Player names", "Matching", "Whitelist", "Messages", "Punishment", "Worlds",
            "Spam", "Links", "Evasion", "Look-alike tables", "Strikes"), r.categories());
        for (ConfigEntry e : r.entries()) {
            assertFalse(e.title().isBlank(), e.key());
            assertFalse(e.description().isBlank(), e.key());
            assertFalse(e.category().isBlank(), e.key());
            assertTrue(r.files().contains(e.file()), e.key());
        }
        assertTrue(CoreSettings.PROTECTION_LEVEL.overridable());
        assertTrue(CoreSettings.ENABLED_LANGUAGES.overridable());
        assertTrue(CoreSettings.PUNISHMENT_ENABLED.overridable());
        assertTrue(CoreSettings.SCAN_SIGNS.overridable());
        assertTrue(CoreSettings.REPEAT_THRESHOLD.overridable());
        assertFalse(CoreSettings.CONFIG_VERSION.overridable());
        assertFalse(WorldProfiles.DEFAULT_PROFILE.overridable());
        assertEquals(CoreSettings.WHITELIST, r.get("filters.whitelist"));
        assertEquals(SettingType.STRING_LIST, CoreSettings.WHITELIST.type());
        assertNotNull(r.section("punishment.effects"));
        assertEquals(CoreSettings.EFFECTS, r.sectionCovering("punishment.effects.blindness.enabled"));
        assertNull(r.get("nope"));
    }

    @Test
    void generatedDefaultFilesHaveCommentsAndReadBackAsTheDefaults() {
        SettingsRegistry r = registry();
        for (String file : r.files()) {
            String text = DefaultFiles.render(r, file);
            YamlTree tree = YamlTree.parse(text);
            for (ConfigEntry e : r.inFile(file)) {
                if (e instanceof Setting<?> s) {
                    Setting.Parsed<?> parsed = s.parse(tree.get(s.key()));
                    assertNull(parsed.problem(), s.key());
                    assertEquals(s.defaultValue(), parsed.value(), s.key());
                    assertNotNull(tree.get(s.key()), s.key() + " must be written into " + file);
                } else if (e instanceof Section section) {
                    assertEquals(ConfigStore.defaultSection(section), tree.getMap(section.key()), section.key());
                }
                String firstLine = e.description().lines().findFirst().orElseThrow();
                assertTrue(text.contains("# " + firstLine.substring(0, Math.min(40, firstLine.length()))), "comment of " + e.key());
            }
            assertTrue(text.startsWith("# ====="), file);
        }
        String config = DefaultFiles.render(r, "config.yml");
        assertTrue(config.contains("config-version: 2\n"));
        assertTrue(config.contains("  enabled-word-lists:\n    - en\n    - custom\n"));
        String ladder = DefaultFiles.render(r, "moderation/ladder.yml");
        assertTrue(ladder.contains("      # Normal walking speed is 0.1. Lower movement speed heavily."), "comments of the old file are kept");
        assertTrue(DefaultFiles.render(r, "worlds.yml").contains("profiles:\n  default: {}\n"));
    }

    @Test
    void valuesAreCheckedAndRepaired() {
        Setting<Integer> level = CoreSettings.PROTECTION_LEVEL;
        assertEquals(3, level.parse(null).value());
        assertEquals(2, level.parse(2).value());
        assertEquals(2, level.parse("2").value());
        assertEquals(2, level.parse(2.9).value(), "like the old reader: numbers are cut, not rounded");
        Setting.Parsed<Integer> high = level.parse(9);
        assertEquals(3, high.value());
        assertTrue(high.problem().contains("9") && high.problem().contains("from 1 to 3"), high.problem());
        assertEquals(1, level.parse(-4).value());
        Setting.Parsed<Integer> text = level.parse("lots");
        assertEquals(3, text.value());
        assertTrue(text.problem().contains("'lots'"), text.problem());
        assertEquals(2, CoreSettings.REPEAT_THRESHOLD.parse(0).value());
        assertEquals(500, CoreSettings.REPEAT_THRESHOLD.parse(500).value(), "no upper limit that the old plugin did not have");

        assertEquals(false, CoreSettings.SCAN_CHAT.parse(false).value());
        assertEquals(true, CoreSettings.SCAN_CHAT.parse("maybe").value());
        assertNotNull(CoreSettings.SCAN_CHAT.parse("maybe").problem());
        assertEquals(List.of("a", "1", "true"), CoreSettings.WHITELIST.parse(List.of("a", 1, true)).value());
        assertNotNull(CoreSettings.WHITELIST.parse("not a list").problem());
        assertEquals("5", CoreSettings.FINAL_COMMAND.parse(5).value());
        assertNotNull(CoreSettings.FINAL_COMMAND.parse(Map.of()).problem());
        assertThrows(IllegalArgumentException.class, () -> level.require("x"));
        assertThrows(IllegalArgumentException.class, () -> level.require(7));
        assertEquals(1, level.require(1));
    }

    @Test
    void allTypesIncludingEnumDurationDoubleAndSecret() {
        SettingsRegistry r = new SettingsRegistry();
        Setting<String> mode = Setting.choice("x.mode", "block", "block", "censor", "log-only").file("filters/x.yml").category("X")
            .title("Mode").description("What to do.").overridable().register(r);
        Setting<Duration> window = Setting.duration("x.window", Duration.ofSeconds(10)).file("filters/x.yml").category("X")
            .title("Window").description("How long.").range(1, 3600).register(r);
        Setting<Double> ratio = Setting.decimal("x.ratio", 0.8).file("filters/x.yml").category("X").title("Ratio")
            .description("How similar.").range(0, 1).register(r);
        Setting<String> hook = Setting.secret("x.webhook").file("discord.yml").category("Discord").title("Webhook").description("Secret URL.")
            .register(r);

        assertEquals("censor", mode.parse("CENSOR").value());
        assertTrue(mode.parse("explode").problem().contains("one of: block, censor, log-only"));
        assertEquals(Duration.ofMinutes(5), window.parse("5m").value());
        assertEquals(Duration.ofSeconds(90), window.parse(90).value());
        assertEquals(Duration.ofHours(1), window.parse("3d").value());
        assertNotNull(window.parse("3d").problem());
        assertNotNull(window.parse("soon").problem());
        assertEquals(1.0, ratio.parse(7).value());
        assertEquals(0.25, ratio.parse("0.25").value());
        assertEquals(Duration.ofMillis(5_400_000), DurationText.parse("1h30m"));
        assertEquals("1h30m", DurationText.format(Duration.ofMinutes(90)));
        assertEquals("1d2s", DurationText.format(DurationText.parse("1d 2s")));
        assertThrows(IllegalArgumentException.class, () -> DurationText.parse("5 parsecs"));

        assertEquals("", hook.defaultValue());
        assertEquals("(not set)", hook.display(""));
        assertEquals("********", hook.display("https://discord.example/api/webhooks/1/abc"));
        assertFalse(hook.parse(Map.of("a", 1)).problem().contains("{"), "a wrong secret value is never echoed");

        assertEquals(List.of("filters/x.yml", "discord.yml"), r.files());
        String text = DefaultFiles.render(r, "filters/x.yml");
        assertEquals("""
            x:
              # What to do.
              # (one of: block, censor, log-only)
              mode: block
              # How long.
              # (time such as 30s, 10m, 2h or 1d, at least 1s, at most 1h)
              window: "10s"
              # How similar.
              # (number from 0 to 1)
              ratio: 0.8
            """, text);
        assertThrows(IllegalArgumentException.class, () -> Setting.bool("x.mode", true).register(r), "duplicate key");
        assertThrows(IllegalArgumentException.class, () -> Setting.bool("x.mode.sub", true).file("filters/x.yml").register(r), "key under a value");
    }
}
