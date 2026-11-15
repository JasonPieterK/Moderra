package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.config.ConfigEntry;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.MessageDefaults;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import com.jason.plugins.moderra.engine.config.YamlTree;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The message texts: one style (ASD-STE100 rules, one palette) and the upgrade of old default texts. */
class MessageStyleTest {
    @TempDir
    Path dir;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z]+}");
    private static final Pattern CODES = Pattern.compile("&[0-9a-fk-or]");

    private static SettingsRegistry registry() {
        SettingsRegistry r = new SettingsRegistry();
        Engine.registerSettings(r);
        return r;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new HashSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group().toLowerCase());
        }
        return found;
    }

    /** Old defaults that are names, not message texts. */
    private static final Set<String> NOT_TEXTS = Set.of("discord.webhook.username", "discord.commands.prefix");

    @Test
    void everyOldDefaultIsRecordedAndEveryPlaceholderSurvives() {
        SettingsRegistry r = registry();
        for (Map.Entry<String, List<String>> e : MessageDefaults.OLD_DEFAULTS.entrySet()) {
            Setting<?> setting = r.get(e.getKey());
            assertNotNull(setting, e.getKey());
            String now = (String) setting.defaultValue();
            for (String old : e.getValue()) {
                assertFalse(old.equals(now), e.getKey() + ": the old default must differ from the new one");
                assertTrue(placeholders(now).containsAll(placeholders(old)), e.getKey() + " lost a placeholder: " + old + " -> " + now);
            }
        }
    }

    @Test
    void messageDefaultsFollowTheStyleRules() {
        SettingsRegistry r = registry();
        for (String key : MessageDefaults.OLD_DEFAULTS.keySet()) {
            if (key.equals("ladder.default-reason") || NOT_TEXTS.contains(key)) {
                continue;
            }
            String text = (String) r.get(key).defaultValue();
            if (key.startsWith("discord.")) {
                assertDiscordText(key, text);
                continue;
            }
            assertTrue(text.startsWith("&8[&6&lModerra&8]&r "), key + " starts with the prefix");
            String plain = CODES.matcher(PLACEHOLDER.matcher(text.replace("\\n", " ")).replaceAll("x")).replaceAll("");
            assertFalse(Pattern.compile("\\b[A-Z]{3,}\\b").matcher(plain.replace("Moderra", "")).find(), key + " has shouting: " + plain);
            for (String sentence : plain.split("(?<=[.:!?])\\s+")) {
                assertTrue(sentence.trim().split("\\s+").length <= 20, key + ": sentence longer than 20 words: " + sentence);
            }
            assertFalse(text.toLowerCase().contains("please"), key);
        }
    }

    /** Discord text: plain, no Minecraft colour codes, formal sentences. */
    private static void assertDiscordText(String key, String text) {
        assertFalse(text.contains("&") || text.contains("\u00a7"), key + " has no colour codes");
        String plain = PLACEHOLDER.matcher(text.replace("**", "")).replaceAll("x");
        assertFalse(Pattern.compile("\\b[A-Z]{3,}\\b").matcher(plain.replace("Moderra", "")).find(), key + " has shouting: " + plain);
        for (String sentence : plain.split("(?<=[.:!?])\\s+")) {
            assertTrue(sentence.trim().split("\\s+").length <= 20, key + ": sentence longer than 20 words: " + sentence);
        }
        assertFalse(text.toLowerCase().contains("please"), key);
        assertTrue(text.endsWith("."), key + " ends with a full stop");
    }

    @Test
    void menuResultsCarryThePrefixAndTheKeyWord() {
        assertTrue(com.jason.plugins.moderra.engine.gui.MenuAction.error("x").message().startsWith(com.jason.plugins.moderra.engine.command.Chat.PREFIX));
        assertTrue(com.jason.plugins.moderra.engine.gui.MenuAction.error("x").message().contains("Error."));
        assertTrue(com.jason.plugins.moderra.engine.gui.MenuAction.saved("x").message().contains("Done."));
        assertTrue(com.jason.plugins.moderra.engine.gui.MenuAction.close("x").message().contains("Info."));
        assertEquals(null, com.jason.plugins.moderra.engine.gui.MenuAction.close(null).message());
        assertTrue(com.jason.plugins.moderra.engine.gui.MenuAction.denied().message().contains("Error."));
    }

    @Test
    void everyDiscordWebhookTemplateKeepsItsPlaceholdersAndIsNewlyWorded() {
        SettingsRegistry r = registry();
        for (String kind : List.of("violation", "punishment", "mute", "unmute", "spam", "link", "evasion", "admin-command")) {
            String key = "discord.templates." + kind;
            assertTrue(MessageDefaults.OLD_DEFAULTS.containsKey(key), key);
            assertTrue(((String) r.get(key).defaultValue()).contains("{player}"), key);
        }
        assertTrue(((String) r.get("discord.inbound.notify-text").defaultValue()).startsWith("[Moderra] "));
    }

    @Test
    void anOldDefaultBecomesTheNewTextAndACustomisedMessageStays() {
        Fixture f = new Fixture(dir);
        f.start();
        String oldPlayer = MessageDefaults.OLD_DEFAULTS.get(CoreSettings.WARNING_PLAYER.key()).get(0);
        String oldCaps = MessageDefaults.OLD_DEFAULTS.get("filters.spam.caps.message").get(0);
        // an install from before the redesign: two messages with the old default, one customised
        String newPlayer = CoreSettings.WARNING_PLAYER.defaultValue();
        TestFiles.edit(f.file("config.yml"), "\"" + newPlayer + "\"", "\"" + oldPlayer + "\"");
        TestFiles.edit(f.file("filters/spam.yml"), "\"" + (String) registry().get("filters.spam.caps.message").defaultValue() + "\"",
            "\"" + oldCaps + "\"");
        TestFiles.edit(f.file("config.yml"), "\"" + CoreSettings.PUNISHMENT_PLAYER.defaultValue() + "\"", "\"&6My own text for {punishments}\"");
        f.platform.infos.clear();

        f.engine.reload();

        assertEquals(newPlayer, f.engine.value(CoreSettings.WARNING_PLAYER));
        assertEquals(registry().get("filters.spam.caps.message").defaultValue(), YamlTree.parse(TestFiles.read(f.file("filters/spam.yml")))
            .get("filters.spam.caps.message"));
        assertEquals("&6My own text for {punishments}", f.engine.value(CoreSettings.PUNISHMENT_PLAYER), "a customised message is never touched");
        assertTrue(f.platform.infos.stream().anyMatch(l -> l.contains("messages.warning-player") && l.contains("filters.spam.caps.message")),
            f.platform.infos.toString());
        assertFalse(TestFiles.read(f.file("config.yml")).contains(oldPlayer), "the old text is gone from the file");
        assertTrue(TestFiles.read(f.file("config.yml")).contains("# "), "comments stay");

        // a second reload changes nothing and says nothing
        f.platform.infos.clear();
        String before = TestFiles.read(f.file("config.yml"));
        f.engine.reload();
        assertEquals(before, TestFiles.read(f.file("config.yml")));
        assertTrue(f.platform.infos.stream().noneMatch(l -> l.contains("old default")), f.platform.infos.toString());
    }

    @Test
    void anOldInstallWithEveryMessageAtTheOldDefaultGetsEveryNewText() {
        Fixture f = new Fixture(dir);
        f.start();
        SettingsRegistry r = registry();
        Set<String> files = new HashSet<>();
        for (Map.Entry<String, List<String>> e : MessageDefaults.OLD_DEFAULTS.entrySet()) {
            if (NOT_TEXTS.contains(e.getKey())) {
                continue; // plain values, checked in RenameUpgradeTest
            }
            ConfigEntry setting = r.entry(e.getKey());
            String current = (String) ((Setting<?>) setting).defaultValue();
            TestFiles.edit(f.file(setting.file()), yamlText(f, setting, current),
                yamlOld(e.getValue().get(0)));
            files.add(setting.file());
        }
        f.engine.reload();
        for (String key : MessageDefaults.OLD_DEFAULTS.keySet()) {
            Setting<?> setting = r.get(key);
            assertEquals(setting.defaultValue(), YamlTree.parse(TestFiles.read(f.file(setting.file()))).get(key), key);
        }
    }

    /** The text exactly as it stands in the file. */
    private static String yamlText(Fixture f, ConfigEntry setting, String value) {
        String quoted = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        String file = TestFiles.read(f.file(setting.file()));
        String single = "'" + value.replace("'", "''") + "'";
        return file.contains(quoted) ? quoted : file.contains(single) ? single : value;
    }

    private static String yamlOld(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
