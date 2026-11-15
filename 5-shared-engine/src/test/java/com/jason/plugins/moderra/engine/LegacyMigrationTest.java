package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.PunishmentAction;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.MessageDefaults;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import com.jason.plugins.moderra.engine.config.YamlTree;
import com.jason.plugins.moderra.engine.legacy.LegacyModCore;
import com.jason.plugins.moderra.mod.Names;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration from the old single config.yml. The proof that behaviour does not change is a before/after comparison:
 * the untouched version-1 code ({@link LegacyModCore}) reads the old file, the engine reads the migrated folder, and
 * both must decide the same for every word of the old lists (plus bypass spellings and clean text), build the same
 * settings and send the same messages.
 */
class LegacyMigrationTest {
    @TempDir
    Path dir;

    /** An old config an admin has worked on: other values everywhere, own lists, own actions, a typo, a bad number. */
    static String editedLegacyConfig() {
        String text = TestFiles.legacyDefaultConfig();
        text = replace(text, "    - english\n    - indonesian\n    - portuguese_brazil\n    - russian\n    - uk_english\n    - german\n    - chinese\n    - custom\n",
            "    - German\n    - klingon\n    - english\n    - spanish\n    - chinese\n");
        text = replace(text, "protection-level: 3", "protection-level: 2");
        text = replace(text, "    signs: true", "    signs: false");
        text = replace(text, "    - msg\n    - tell\n", "    - msg\n    - Shout\n");
        text = replace(text, "    - abw\n", "    - abw\n    - mail\n");
        text = replace(text, "repeated-letter-threshold: 3", "repeated-letter-threshold: 1");
        text = replace(text, "join-adjacent-words: false", "join-adjacent-words: true");
        text = replace(text, "partial-match: false", "partial-match: true");
        text = replace(text, "partial-min-length: 4", "partial-min-length: 5\n  typo-key: 1");
        text = replace(text, "    - class\n    - classic\n", "    - class\n    - Scunthorpe\n    - \"no\"\n");
        text = replace(text, "broadcast-warnings: true", "broadcast-warnings: false");
        text = replace(text, "warning-player: \"&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.\"",
            "warning-player: '&6Hey {player}: \"{word}\" is not ok # really ({count}/{limit})'");
        text = replace(text, "violations-before-punish: 2", "violations-before-punish: 3");
        text = replace(text, "violations-expire-minutes: 0", "violations-expire-minutes: 10");
        text = replace(text, "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        text = replace(text, "  final-command:\n    enabled: false", "  final-command:\n    enabled: true");
        text = replace(text, "    command: \"\"", "    command: \"/tempmute {player} 30s {word}\"");
        text = replace(text, "    blindness:\n      enabled: false", "    blindness:\n      enabled: true");
        text = replace(text, "    anvil_land:\n      enabled: false", "    anvil_land:\n      enabled: true");
        text = replace(text, "    mute:\n      enabled: false\n      command: \"mute {player} 5m Bad language\"",
            "    mute:\n      enabled: true\n      command: \"mute {player} 5m Bad language\"\n    old_style:\n      enabled: true\n      format: \"say {player} was punished\"");
        text = replace(text, "  effects:\n", "  effects:\n    legacy_line:\n      enabled: true\n      format: \"minecraft:slowness-1-5\"\n");
        text = replace(text, "word-lists:\n  english:\n    level-1:\n      - stupid\n", "word-lists:\n  english:\n    level-1:\n      - stupid\n      - Frobnicate\n");
        text = replace(text, "  custom: []", "  spanish:\n    - tonto\n    - cabrón\n  custom:\n    - zorblax\n    - Foo  Bar\n");
        return text;
    }

    private static String replace(String text, String from, String to) {
        assertTrue(text.contains(from), "the legacy default config no longer contains: " + from);
        return text.replace(from, to);
    }

    private record Sides(LegacyModCore before, Fixture after, List<String> oracleWarnings, String oldText) {
    }

    private Sides migrate(String oldConfig) throws IOException {
        Path oldDir = dir.resolve("old");
        // The old code is the oracle for decisions, strike counts, commands and actions. Its message TEXTS are the old
        // defaults, which the new version deliberately replaced (MessageDefaults); so the oracle reads the same file with
        // the old default texts swapped for the new defaults. Customised messages stay as they are.
        String oracleConfig = withNewMessageDefaults(oldConfig);
        TestFiles.write(oldDir.resolve("config.yml"), oracleConfig);
        List<String> oracleWarnings = new ArrayList<>();
        Fixture f = new Fixture(dir.resolve("new"));
        LegacyModCore before = new LegacyModCore(oldDir, "1", new LegacyModCore.Log() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
                oracleWarnings.add(message);
            }
        }, f.now::get);
        assertEquals(oracleConfig, TestFiles.read(oldDir.resolve("config.yml")), "the oracle must not change its file");

        TestFiles.write(f.file("config.yml"), oldConfig);
        f.start();
        return new Sides(before, f, oracleWarnings, oldConfig);
    }

    /** The old config with every message that still has an old default text switched to the new default text. */
    private static String withNewMessageDefaults(String oldConfig) {
        SettingsRegistry registry = new SettingsRegistry();
        Engine.registerSettings(registry);
        String text = oldConfig;
        for (var entry : MessageDefaults.OLD_DEFAULTS.entrySet()) {
            if (!entry.getKey().startsWith("messages.")) {
                continue;
            }
            for (String old : entry.getValue()) {
                text = text.replace("\"" + old + "\"", "\"" + registry.get(entry.getKey()).defaultValue() + "\"");
            }
        }
        return text;
    }

    private static List<Path> backups(Path folder) throws IOException {
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> p.getFileName().toString().startsWith("config.yml.bak-")).toList();
        }
    }

    /** Every word of the old config in many spellings, plus text that must stay clean. */
    private static Set<String> corpus(String oldConfig) {
        Set<String> out = new LinkedHashSet<>();
        YamlTree old = YamlTree.parse(oldConfig);
        List<String> words = new ArrayList<>();
        for (String list : old.keys("word-lists")) {
            words.addAll(old.getStringList("word-lists." + list));
            for (int tier = 1; tier <= 3; tier++) {
                words.addAll(old.getStringList("word-lists." + list + ".level-" + tier));
            }
        }
        words.addAll(old.getStringList("filters.whitelist"));
        for (String w : words) {
            out.add(w);
            out.add(w.toUpperCase());
            out.add("well " + w + " then");
            out.add(String.join(" ", w.split("")));
            out.add(String.join(".", w.split("")));
            out.add(w.replace('a', '@').replace('i', '1').replace('s', '$').replace('o', '0'));
            out.add(w + w.charAt(w.length() - 1) + w.charAt(w.length() - 1) + w.charAt(w.length() - 1));
            out.add(w.charAt(0) + "*" + w.substring(Math.min(2, w.length())));
            out.add("xx" + w + "xx");
            out.add(w + "s");
            out.add(w.substring(0, w.length() / 2) + " " + w.substring(w.length() / 2));
        }
        out.addAll(List.of("hello world", "nice glass house", "pass the class", "I assume the assassin is a classic", "scunthorpe united",
            "pen is mightier", "", "   ", "12345", "the quick brown fox", "zorblax", "foo bar", "FOO   BAR", "frobnicate", "tonto", "cabron",
            "cabrón", "klingon", "no", "what the f.u.c.k", "sh1t happens", "fuuuuuck", "a s s", "Привет мир", "你好世界", "操你妈的"));
        return out;
    }

    private static String withoutIgnored(String settings) {
        return settings.replaceAll("ignoredCommands=\\[[^\\]]*\\]", "ignoredCommands=*");
    }

    private static void assertSameDecisions(Sides sides) {
        Engine engine = sides.after().engine;
        LegacyModCore before = sides.before();
        int blocked = 0;
        Set<String> corpus = corpus(sides.oldText());
        for (String text : corpus) {
            String expected = before.find(text);
            assertEquals(expected, engine.find(text), "decision changed for: '" + text + "'");
            blocked += expected == null ? 0 : 1;
        }
        assertTrue(blocked > 200 && blocked < corpus.size() - 20, "the corpus must contain blocked and clean texts: " + blocked + "/" + corpus.size());
        assertEquals(before.matcher().size(), engine.settings().matcher().size(), "same number of blocked words");
        // the list of ignored commands is the one thing that changed on purpose (the command is now /moderra, see MessageDefaults.OLD_LIST_DEFAULTS)
        assertEquals(withoutIgnored(before.settings().toString()), withoutIgnored(engine.settings().legacy().toString()), "every old setting has the same value");
        assertEquals(new LinkedHashSet<>(before.listWords("active")), new LinkedHashSet<>(engine.listWords("active")));
        assertEquals(before.listWords("custom"), engine.listWords("custom"));
        for (String line : List.of("/msg Bob fuck", "/Minecraft:MSG x y", "/abw addword fuck", "/gamemode creative", "/home", "/shout hi all",
            "/mail send x", "/tell x y")) {
            assertEquals(before.commandTextToScan(line), Engine.commandTextToScan(engine.settings(), line), line);
        }
    }

    /** Lets the same player swear {@code times} times on both sides and compares everything that is said and done. */
    private static void assertSameReactions(Sides sides, int times) {
        Fixture f = sides.after();
        LegacyModCore before = sides.before();
        UUID bob = f.platform.join("Bob");
        UUID staff = f.platform.join("Mod", Engine.PERMISSION_NOTIFY);
        for (int i = 0; i < times; i++) {
            f.platform.broadcasts.clear();
            f.platform.consoleCommands.clear();
            f.platform.infos.clear();
            f.platform.sentTo(bob).clear();
            f.platform.sentTo(staff).clear();
            String word = before.find("you fuck");
            LegacyModCore.Outcome expected = before.handleViolation(bob, "Bob", word, "chat");
            f.say(bob, Message.CHAT, "you fuck");

            String step = "violation " + (i + 1);
            assertEquals(expected.staffMessage() == null ? List.of() : List.of(expected.staffMessage()), f.platform.sentTo(staff), step);
            assertEquals(expected.logLine() == null ? List.of() : List.of(expected.logLine()), f.platform.infos, step);
            assertEquals(before.getViolation(bob), f.engine.strikes().get(bob), step);
            if (!expected.punish()) {
                assertEquals(expected.broadcast() == null ? List.of() : List.of(expected.broadcast()), f.platform.broadcasts, step);
                assertEquals(List.of(expected.playerMessage()), f.platform.sentTo(bob), step);
                assertTrue(f.platform.consoleCommands.isEmpty(), step);
                continue;
            }
            List<String> applied = new ArrayList<>();
            List<String> commands = new ArrayList<>();
            for (PunishmentAction action : expected.actions()) {
                switch (action.type()) {
                    case "sound" -> applied.add(Names.soundDescription(action.args().get(0)));
                    case "command" -> {
                        applied.add("server command");
                        commands.add(action.args().get(0).replace("{player}", "Bob"));
                    }
                    default -> applied.add(Names.prettyName(action.args().get(0)));
                }
            }
            LegacyModCore.Punished done = before.finishPunishment(expected, applied);
            if (done.finalCommand() != null) {
                commands.add(done.finalCommand());
            }
            assertEquals(List.of(done.broadcast()), f.platform.broadcasts, step);
            assertEquals(List.of(done.playerMessage()), f.platform.sentTo(bob), step);
            assertEquals(commands, f.platform.consoleCommands, step);
        }
    }

    @Test
    void realOldDefaultConfigMigratesAndDecidesTheSame() throws IOException {
        Sides sides = migrate(TestFiles.legacyDefaultConfig());
        Fixture f = sides.after();
        assertTrue(sides.oracleWarnings().isEmpty());
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());
        assertTrue(f.engine.problems().isEmpty(), f.engine.problems().toString());

        // the old file is kept as a backup, byte for byte, and config.yml is now the new layout
        List<Path> backups = backups(f.dir);
        assertEquals(1, backups.size());
        assertEquals(sides.oldText(), TestFiles.read(backups.get(0)));
        YamlTree config = YamlTree.parse(TestFiles.read(f.file("config.yml")));
        assertEquals(2, config.get("config-version"));
        assertNull(config.get("word-lists"), "word lists no longer live in config.yml");
        assertNull(config.get("punishment"), "punishments moved to moderation/ladder.yml");
        assertTrue(TestFiles.read(f.file("config.yml")).contains("# How strict the filter is."), "the new file is commented");
        assertTrue(TestFiles.read(f.file("moderation/ladder.yml")).contains("# Normal walking speed is 0.1."),
            "unchanged punishment sections keep their comments");

        // exactly the same languages are on, under their new codes, with exactly the old words
        assertEquals(List.of("en", "id", "pt-br", "ru", "en-gb", "de", "zh", "custom"), f.engine.settings().enabledLanguages());
        assertEquals(List.of("en", "id", "pt-br", "ru", "en-gb", "de", "zh", "custom"), config.getStringList("filters.enabled-word-lists"));
        YamlTree old = YamlTree.parse(sides.oldText());
        assertEquals(old.getStringList("word-lists.german.level-3"), YamlTree.parse(TestFiles.read(f.file("wordlists/de.yml"))).getStringList("level-3"));
        assertEquals(old.getStringList("word-lists.chinese.level-1"), YamlTree.parse(TestFiles.read(f.file("wordlists/zh.yml"))).getStringList("level-1"));
        assertEquals(55, f.engine.wordLists().get("en").size(), "the old english list, not the bigger bundled one");
        assertTrue(f.engine.wordLists().get("fr").size() > 50, "languages the old config did not have come from the jar (switched off)");
        assertNull(f.engine.find("merde alors"), "and stay off");

        List<String> log = f.platform.infos;
        assertTrue(log.contains("Old single-file config.yml found: moving its settings into the new config folder."), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("Word lists written from the old config: english -> wordlists/en.yml (55 words)")), log.toString());
        assertTrue(log.contains("Languages switched on (same as before): en, id, pt-br, ru, en-gb, de, zh, custom."), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("Backup of the old file: config.yml.bak-")), log.toString());
        assertTrue(log.contains("Custom words: 0 -> wordlists/custom.yml. Whitelist entries: 20 (stay in config.yml)."), log.toString());

        assertSameDecisions(sides);
        assertSameReactions(sides, 5);

        // a second start (and a reload) is not a second migration
        String configAfter = TestFiles.read(f.file("config.yml"));
        f.platform.infos.clear();
        f.start();
        f.engine.reload();
        assertEquals(1, backups(f.dir).size());
        assertEquals(configAfter, TestFiles.read(f.file("config.yml")));
        assertTrue(f.platform.infos.stream().noneMatch(l -> l.contains("Old single-file")));
        assertSameDecisions(sides);
    }

    @Test
    void editedOldConfigMigratesAndDecidesTheSame() throws IOException {
        Sides sides = migrate(editedLegacyConfig());
        Fixture f = sides.after();
        Engine engine = f.engine;

        // what the admin changed arrived in the right files
        YamlTree config = YamlTree.parse(TestFiles.read(f.file("config.yml")));
        YamlTree ladder = YamlTree.parse(TestFiles.read(f.file("moderation/ladder.yml")));
        assertEquals(2, config.get("filters.protection-level"));
        assertEquals(false, config.get("filters.scan.signs"));
        assertEquals(List.of("class", "Scunthorpe", "no", "assassin"), config.getStringList("filters.whitelist").subList(0, 4));
        assertEquals("&6Hey {player}: \"{word}\" is not ok # really ({count}/{limit})", config.get("messages.warning-player"));
        assertEquals(3, ladder.get("punishment.violations-before-punish"));
        assertEquals("/tempmute {player} 30s {word}", ladder.get("punishment.final-command.command"));
        assertEquals("minecraft:slowness-1-5", ladder.get("punishment.effects.legacy_line.format"));
        assertEquals(List.of("legacy_line", "blindness"), new ArrayList<>(ladder.keys("punishment.effects")).subList(0, 2), "the admin's order is kept");
        assertTrue(TestFiles.read(f.file("config.yml")).contains("# Words that must never be blocked"), "comments stay around migrated values");

        // languages: same ones on (order of the old word-lists section), the undefined one is still reported, the own list is kept
        assertEquals(List.of("en", "de", "zh", "spanish", "klingon", "custom"), config.getStringList("filters.enabled-word-lists"));
        assertEquals(List.of("tonto", "cabrón"), engine.wordLists().get("spanish").tiers().get(2), "old flat lists count at every level");
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("'klingon'")), f.platform.warnings.toString());
        assertTrue(sides.oracleWarnings().stream().anyMatch(w -> w.contains("'klingon'")), "the old code warned about it too");
        assertEquals(List.of("zorblax", "foo bar"), engine.customWords());
        assertTrue(TestFiles.read(f.file("wordlists/custom.yml")).startsWith("# Your own words."), "custom.yml keeps its explanation");

        // things that could not be carried over are named
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("not known and were not copied") && w.contains("filters.typo-key")),
            f.platform.warnings.toString());
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("filters.matching.repeated-letter-threshold") && w.contains("using 2")),
            f.platform.warnings.toString());
        assertEquals(2, engine.settings().get(CoreSettings.REPEAT_THRESHOLD), "the old plugin also used 2 for a 1");
        assertEquals(sides.oldText(), TestFiles.read(backups(f.dir).get(0)));

        assertSameDecisions(sides);
        assertEquals("cabrón", engine.find("eres un CABRON"));
        assertNull(engine.find("un petit mot: sign"));
        assertSameReactions(sides, 7);

        // level-1 words stay off at protection level 2, exactly as before
        assertNull(engine.find("stupid"));
        assertEquals(sides.before().find("stupid"), engine.find("stupid"));
    }

    @Test
    void oldConfigWithoutSectionsOrLists() throws IOException {
        // no enabled list (= all lists on), no punishment actions, a flat list, and nothing else
        String old = "filters:\n  protection-level: 1\nword-lists:\n  english:\n    level-1: [mild]\n    level-3: [severeword]\n  mine: [flatword]\n"
            + "punishment:\n  sounds:\n    ding:\n      enabled: true\n      sound-name: block.note_block.pling\n      volume: 1\n      pitch: 2\n";
        Sides sides = migrate(old);
        Engine engine = sides.after().engine;
        assertEquals(List.of("en", "mine", "custom"), engine.settings().enabledLanguages());
        assertEquals("severeword", engine.find("a severeword"));
        assertEquals("flatword", engine.find("a flatword"));
        assertNull(engine.find("mild"));
        assertEquals(List.of(new PunishmentAction(true, "sound", "ding", List.of("block.note_block.pling", "1", "2"))),
            engine.settings().legacy().punishmentActions(), "sections missing in the old file mean 'no such actions', as before");
        assertEquals(sides.before().settings().punishmentActions(), engine.settings().legacy().punishmentActions());
        assertEquals(sides.before().find("a flatword"), engine.find("a flatword"));
        assertEquals(sides.before().matcher().size(), engine.settings().matcher().size());
    }

    @Test
    void unreadableOldConfigIsLeftAloneUntilItIsFixed() throws IOException {
        Fixture f = new Fixture(dir.resolve("x"));
        String broken = TestFiles.legacyDefaultConfig().replace("filters:\n", "filters: [\n");
        TestFiles.write(f.file("config.yml"), broken);
        f.start();
        assertEquals(broken, TestFiles.read(f.file("config.yml")));
        assertTrue(backups(f.dir).isEmpty());
        assertEquals(1, f.engine.problems().size());
        assertFalse(Files.exists(f.file("wordlists/id.yml")) && f.engine.settings().enabledLanguages().contains("id"));

        TestFiles.write(f.file("config.yml"), TestFiles.legacyDefaultConfig());
        assertTrue(f.engine.reload().isEmpty());
        assertEquals(1, backups(f.dir).size());
        assertEquals(List.of("en", "id", "pt-br", "ru", "en-gb", "de", "zh", "custom"), f.engine.settings().enabledLanguages());
        assertEquals(55, f.engine.wordLists().get("en").size());
    }

    @Test
    void migrationLogForTheDocs() throws IOException {
        Sides sides = migrate(TestFiles.legacyDefaultConfig());
        List<String> lines = sides.after().platform.infos;
        int start = lines.indexOf("Old single-file config.yml found: moving its settings into the new config folder.");
        assertTrue(start >= 0);
        lines.subList(start, lines.size()).forEach(l -> System.out.println("[migration log] " + l));
    }
}
