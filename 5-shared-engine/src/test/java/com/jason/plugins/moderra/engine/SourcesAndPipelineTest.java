package com.jason.plugins.moderra.engine;

import com.jason.plugins.moderra.engine.command.Chat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.config.ConfigProblem;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.YamlTree;
import com.jason.plugins.moderra.engine.filters.EvasionSettings;
import com.jason.plugins.moderra.engine.filters.LinkSettings;
import com.jason.plugins.moderra.engine.filters.SpamSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The new text sources (join names, command blocks, sign back side), the check order, and /moderra check and info. */
class SourcesAndPipelineTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    final List<ModerationEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
        engine.events().subscribe(events::add);
    }

    private <T> void set(Setting<T> setting, T value) {
        try {
            engine.set(setting, value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        assertTrue(engine.problems().isEmpty(), engine.problems().toString());
    }

    private List<String> run(String line) {
        List<String> out = new ArrayList<>();
        engine.commands().execute(new CommandSender() {
            @Override
            public String name() {
                return "CONSOLE";
            }

            @Override
            public UUID id() {
                return null;
            }

            @Override
            public boolean has(String permission) {
                return true;
            }

            @Override
            public void send(String message) {
                out.add(message);
            }
        }, "moderra", line.isEmpty() ? new String[0] : line.split(" "));
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Join names
    // ------------------------------------------------------------------------------------------

    @Test
    void joinNameCheckIsOffByDefaultThenLogsWarnsOrKicks() {
        UUID staff = f.platform.join("Mod", Engine.PERMISSION_NOTIFY);
        UUID bad = f.platform.join("xXfuckXx");
        assertFalse(engine.onJoin(bad, "xXfuckXx").hasHits(), "off by default");
        assertTrue(events.isEmpty() && f.platform.kicks.isEmpty());

        set(CoreSettings.SCAN_JOIN, true);
        f.platform.infos.clear();
        Verdict v = engine.onJoin(bad, "xXfuckXx");
        assertTrue(v.blocked());
        assertEquals("fuck", v.primary().detail());
        assertTrue(f.platform.infos.contains("xXfuckXx joined with a player name that is not allowed (badword: 'fuck'), action: log"));
        assertTrue(f.platform.kicks.isEmpty() && f.platform.sentTo(bad).isEmpty() && f.platform.sentTo(staff).isEmpty(), "log = console only");
        assertEquals(1, events.size());
        assertEquals(ModerationEvent.VIOLATION, events.get(0).kind());
        assertEquals(Message.JOIN, events.get(0).source());
        assertEquals("log", events.get(0).data().get("action"));
        assertEquals("xXfuckXx", events.get(0).data().get("text"));
        assertEquals(0, engine.strikes().get(bad), "a name never gives strikes");

        set(CoreSettings.JOIN_ACTION, "warn");
        engine.onJoin(bad, "xXfuckXx");
        assertEquals(List.of("§8[§6§lModerra§8]§r §e§lWarning. §7Your player name contains a word that is not permitted. Change your name."), f.platform.sentTo(bad));
        assertEquals(List.of("§8[§6§lModerra§8]§r §6§lStaff notice. §7The name of §fxXfuckXx§7 contains the word §c§ofuck§7."), f.platform.sentTo(staff));
        assertTrue(f.platform.kicks.isEmpty());

        set(CoreSettings.JOIN_ACTION, "kick");
        set(CoreSettings.JOIN_KICK_MESSAGE, "&cName not allowed: {word}");
        engine.onJoin(bad, "xXfuckXx");
        assertEquals(List.of("xXfuckXx: §cName not allowed: fuck"), f.platform.kicks);
        assertEquals("kick", events.get(events.size() - 1).data().get("action"));

        // names that are fine, tricks in names, whitelist, bypass
        for (String fine : List.of("Steve", "Alex_2012", "Dickens99", "Assassin", "xX_Grass_Xx", "TheCocktailBar", "xPeacockx")) {
            assertFalse(engine.onJoin(f.platform.join(fine), fine).hasHits(), fine);
        }
        UUID town = f.platform.join("Scunthorpe");
        assertTrue(engine.onJoin(town, "Scunthorpe").blocked(), "the classic false positive of looking inside names ...");
        run("whitelist add scunthorpe");
        assertFalse(engine.onJoin(town, "Scunthorpe").hasHits(), "... is solved with the whitelist");
        assertFalse(engine.onJoin(f.platform.join("Scunthorpe_FC"), "Scunthorpe_FC").hasHits());
        for (String name : List.of("Fuck", "sh1t_head", "FuuuuckYou", "Mr_FUCK_2000", "xFuсkx")) {
            assertTrue(engine.onJoin(f.platform.join(name), name).blocked(), name);
        }
        assertFalse(engine.onJoin(f.platform.join("OpFuck", Engine.PERMISSION_BYPASS), "OpFuck").hasHits(), "bypass permission");

        set(CoreSettings.JOIN_MATCH_INSIDE, false);
        assertFalse(engine.onJoin(bad, "xXfuckXx").hasHits(), "only whole words now");
        assertTrue(engine.onJoin(f.platform.join("Mr_fuck_2"), "Mr_fuck_2").blocked());
        set(CoreSettings.JOIN_MATCH_INSIDE, true);
        set(CoreSettings.JOIN_MIN_LENGTH, 5);
        assertFalse(engine.onJoin(bad, "xXfuckXx").hasHits(), "a 4-letter word is no longer searched inside names");
        assertTrue(engine.onJoin(f.platform.join("fuck"), "fuck").blocked(), "but the whole name still counts");

        assertFalse(f.say(bad, Message.CHAT, "xXfuckXx said hi").hasHits(), "chat does not use the inside-names matching");
        assertEquals("§7Scanning: §fchat, commands, signs, books, anvil, player names on join (kick)", run("info").get(2));
    }

    // ------------------------------------------------------------------------------------------
    // Command blocks and the sign back side
    // ------------------------------------------------------------------------------------------

    @Test
    void commandBlocksAreScannedWhenSwitchedOnAndReportedOncePerInterval() {
        assertFalse(engine.blockCommandBlock("world", "world 1,64,1", "say you fuck"), "off by default");
        set(CoreSettings.SCAN_COMMAND_BLOCKS, true);
        assertTrue(engine.blockCommandBlock("world", "world 1,64,1", "say you fuck"));
        assertTrue(engine.blockCommandBlock("world", "world 1,64,1", "/minecraft:tellraw @a {\"text\":\"what the fuck\"}"));
        assertFalse(engine.blockCommandBlock("world", "world 1,64,1", "say hello"));
        assertFalse(engine.blockCommandBlock("world", "world 1,64,1", "give @p diamond 1"), "not a scanned command");
        assertFalse(engine.blockCommandBlock("world", "world 1,64,1", "moderra addword fuck"), "ignored commands stay usable");
        assertEquals(2, events.size());
        assertNull(events.get(0).playerId(), "nobody is blamed");
        assertEquals("CommandBlock(world 1,64,1)", events.get(0).playerName());
        assertEquals(Message.COMMAND_BLOCK, events.get(0).source());
        assertTrue(f.platform.infos.contains("CommandBlock(world 1,64,1) triggered badword (fuck) in command-block: blocked"));
        assertTrue(f.platform.broadcasts.isEmpty(), "no warnings, no strikes, no punishment");

        // a repeating command block: stopped every time, reported once per minute
        for (int i = 0; i < 100; i++) {
            assertTrue(engine.blockCommandBlock("world", "world 1,64,1", "say you fuck"));
            f.now.addAndGet(50);
        }
        assertEquals(2, events.size());
        f.now.addAndGet(60_000);
        assertTrue(engine.blockCommandBlock("world", "world 1,64,1", "say you fuck"));
        assertEquals(3, events.size());
        assertTrue(engine.blockCommandBlock("world", "world 9,9,9", "say you fuck"));
        assertEquals(4, events.size(), "another block is another report");
        set(CoreSettings.COMMAND_BLOCK_REPORT_INTERVAL, Duration.ZERO);
        engine.blockCommandBlock("world", "world 9,9,9", "say you fuck");
        engine.blockCommandBlock("world", "world 9,9,9", "say you fuck");
        assertEquals(6, events.size());
        assertTrue(run("info").get(2).endsWith("anvil, command blocks"));
    }

    @Test
    void signBackSideHasItsOwnSwitchUnderTheSignSwitch() {
        UUID bob = f.platform.join("Bob");
        assertTrue(f.say(bob, Message.SIGN_BACK, "fuck").blocked(), "on by default, like before (the old plugin saw both sides)");
        set(CoreSettings.SCAN_SIGN_BACK, false);
        assertTrue(f.say(bob, Message.SIGN_BACK, "fuck").allowed());
        assertTrue(f.say(bob, Message.SIGN, "fuck").blocked());
        assertEquals("§7Scanning: §fchat, commands, signs (front side only), books, anvil", run("info").get(2));
        set(CoreSettings.SCAN_SIGN_BACK, true);
        set(CoreSettings.SCAN_SIGNS, false);
        assertTrue(f.say(bob, Message.SIGN_BACK, "fuck").allowed(), "signs off = both sides off");
        assertTrue(engine.scans(Message.CHAT, engine.settings()) && !engine.scans(Message.SIGN_BACK, engine.settings()));
        assertEquals("sign", Message.baseSource(Message.SIGN_BACK));
    }

    @Test
    void newInstallsScanMoreCommandsAndMigratedInstallsKeepTheirList() {
        UUID bob = f.platform.join("Bob");
        for (String command : List.of("/team add fuck", "/title @a title \"fuck\"", "/tellraw @a \"fuck\"", "/bossbar add x fuck",
            "/scoreboard objectives add fuck dummy", "/rename fuck", "/msg Amy fuck", "/nick fuck")) {
            assertTrue(f.say(bob, Message.COMMAND, command).blocked(), command);
        }
        Fixture old = new Fixture(dir.resolve("old"));
        TestFiles.write(old.file("config.yml"), TestFiles.legacyDefaultConfig());
        Engine migrated = old.start();
        assertTrue(migrated.problems().isEmpty(), migrated.problems().toString());
        assertEquals(CoreSettings.LEGACY_SCANNED_COMMANDS, migrated.value(CoreSettings.SCANNED_COMMANDS));
        assertEquals(CoreSettings.LEGACY_SCANNED_COMMANDS,
            YamlTree.parse(TestFiles.read(old.file("config.yml"))).getStringList("filters.scanned-commands"), "written into the new file");
        UUID amy = old.platform.join("Amy");
        assertTrue(old.say(amy, Message.COMMAND, "/team add fuck").allowed(), "an updated server scans exactly the commands it scanned before");
        assertTrue(old.say(amy, Message.COMMAND, "/msg Bob fuck").blocked());
        // an old file without the key: the old default, not the new one
        Fixture bare = new Fixture(dir.resolve("bare"));
        TestFiles.write(bare.file("config.yml"), "filters:\n  protection-level: 3\nword-lists:\n  english:\n    level-3: [fuck]\n");
        assertEquals(CoreSettings.LEGACY_SCANNED_COMMANDS, bare.start().value(CoreSettings.SCANNED_COMMANDS));

        // and every new feature is off or silent on the updated server
        assertFalse(migrated.settings().get(SpamSettings.ENABLED));
        assertEquals("off", migrated.settings().get(LinkSettings.MODE));
        assertEquals("log-only", migrated.settings().get(EvasionSettings.DETECT_ACTION));
        assertFalse(migrated.settings().get(CoreSettings.SCAN_JOIN));
        assertFalse(migrated.settings().get(CoreSettings.SCAN_COMMAND_BLOCKS));
        for (String file : List.of("filters/spam.yml", "filters/links.yml", "filters/evasion.yml", "filters/confusables.yml")) {
            assertTrue(java.nio.file.Files.isRegularFile(old.file(file)), file);
        }
        for (String text : List.of("HELLO EVERYBODY THIS IS LOUD", "visit example.com", "aaaaaaaaaaaaaaaaaaa", "same", "same", "same", "same")) {
            Verdict v = old.say(amy, Message.CHAT, text);
            assertTrue(v.allowed() && !v.hasHits(), text);
        }
        assertTrue(old.platform.sentTo(amy).size() == 1 && old.platform.kicks.isEmpty(), "only the one warning for /msg Bob ...");
    }

    // ------------------------------------------------------------------------------------------
    // Check order
    // ------------------------------------------------------------------------------------------

    @Test
    void checkOrderIsConfigurableWithGroupsAndUnknownNamesAreReported() {
        UUID bob = f.platform.join("Bob");
        f.platform.rewritable.add(Message.CHAT);
        set(LinkSettings.MODE, "block-all");
        set(SpamSettings.ENABLED, true);
        assertEquals(List.of("evasion", "badword", "link", "spam-commands", "spam-rate", "spam-repeat", "spam-caps", "spam-flood", "spam-words"),
            engine.checks(engine.settings()).stream().map(TextCheck::id).toList());
        assertEquals("badword", f.say(bob, Message.CHAT, "fuck example.com").primary().check());

        set(CoreSettings.CHECK_ORDER, List.of("Link", "spam-caps", "badword"));
        assertEquals(List.of("link", "spam-caps", "badword", "evasion", "spam-commands", "spam-rate", "spam-repeat", "spam-flood", "spam-words"),
            engine.checks(engine.settings()).stream().map(TextCheck::id).toList(), "listed ones first, the rest in their built-in order");
        assertEquals("link", f.say(bob, Message.CHAT, "fuck example.com").primary().check());

        // a cleaning check hands its text on: caps first, so the later checks see lower case
        Verdict v = f.say(bob, Message.CHAT, "THIS IS A LOUD MESSAGE WITH NOTHING ELSE");
        assertEquals("this is a loud message with nothing else", v.text());

        set(CoreSettings.CHECK_ORDER, List.of("spam", "badword"));
        assertEquals(List.of("spam-commands", "spam-rate", "spam-repeat", "spam-caps", "spam-flood", "spam-words", "badword", "evasion", "link"),
            engine.checks(engine.settings()).stream().map(TextCheck::id).toList());

        TestFiles.edit(f.file("config.yml"), "    - spam\n    - badword\n", "    - spam\n    - badwords\n    - nonsense\n");
        List<ConfigProblem> problems = engine.reload();
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).toString().contains("filters.check-order") && problems.get(0).toString().contains("'badwords' is not the name of a check"),
            problems.toString());
        assertTrue(problems.get(0).toString().contains("Known: evasion, badword, link, spam-commands"));
        assertTrue(f.say(bob, Message.CHAT, "fuck").blocked(), "checks that are not listed still run");

        // per profile
        TestFiles.write(f.file("worlds.yml"), "profiles:\n  default: {}\n  links-first:\n    filters.check-order: [link]\nworlds:\n  shop: links-first\n");
        TestFiles.edit(f.file("config.yml"), "    - spam\n    - badwords\n    - nonsense\n", "    - badword\n");
        assertTrue(engine.reload().isEmpty(), engine.problems().toString());
        assertEquals("badword", f.say(bob, Message.CHAT, "fuck example.com").primary().check());
        f.platform.worlds.put(bob, "shop");
        assertEquals("link", f.say(bob, Message.CHAT, "fuck example.com").primary().check());
    }

    @Test
    void aCleanedTextIsBlockedWhereTheServerCannotRewrite() {
        UUID bob = f.platform.join("Bob");
        set(SpamSettings.ENABLED, true);
        Verdict v = f.say(bob, Message.CHAT, "THIS SERVER CANNOT REWRITE CHAT");
        assertTrue(v.blocked(), "like Fabric and Forge: censor is applied as block");
        assertNull(v.text());
        assertEquals("spam-caps", v.primary().check());
        assertEquals(List.of("§8[§6§lModerra§8]§r §e§lNotice. §7Your message contains too many capital letters. §7§oUse lower-case letters."), f.platform.sentTo(bob), "and the player is told why");
        f.platform.rewritable.add(Message.CHAT);
        assertTrue(f.say(bob, Message.CHAT, "THIS SERVER CAN REWRITE CHAT").censored());
        // a command is never rewritten, on any server
        set(SpamSettings.CAPS.sources(), List.of("chat", "command"));
        assertTrue(f.say(bob, Message.COMMAND, "/msg Amy THIS IS A LOUD PRIVATE MESSAGE").blocked());
    }

    // ------------------------------------------------------------------------------------------
    // /moderra check and /moderra info
    // ------------------------------------------------------------------------------------------

    @Test
    void checkCommandNamesTheCheckThatFiredAndWhy() {
        assertEquals(List.of(Chat.ok("Clean: no blocked word found.")), run("check hello world"));
        assertEquals(List.of(Chat.PREFIX + "§c§lBlocked. §7The text contains the blocked word §ffuck§7.", "§7Check: §fbadword§7 (word list entry §ffuck§7, level 3)"), run("check what the f.u.c.k"));
        assertEquals(List.of(Chat.warn("The check §fevasion§7 records the text but does not stop it: look-alike letters."), Chat.PREFIX + "§c§lBlocked. §7The text contains the blocked word §ffuck§7.",
            "§7Check: §fbadword§7 (word list entry §ffuck§7, level 3)"), run("check fuсk"));
        assertEquals(List.of(Chat.ok("Clean: no blocked word found.")), run("check visit example.com NOW PLEASE EVERYBODY"), "filters are off");

        set(LinkSettings.MODE, "block-all");
        set(SpamSettings.ENABLED, true);
        assertEquals(List.of(Chat.PREFIX + "§c§lBlocked. §7The check §flink§7 blocks the text: link example.com.",
            "§7§oThese checks are not tested here. They count real messages over time: spam-commands, spam-rate, spam-repeat."), run("check visit example dot com"));
        assertEquals(List.of(Chat.warn("The check §fspam-caps§7 cleans the text: too many capital letters."), "§7Text after cleaning: §fhello everybody out there",
            "§7§oThese checks are not tested here. They count real messages over time: spam-commands, spam-rate, spam-repeat."),
            run("check HELLO EVERYBODY OUT THERE"));
        List<String> two = run("check HEEEEEEEEEEELLO EVERYBODY");
        assertTrue(two.get(0).contains("spam-caps") && two.get(2).contains("spam-flood") && two.get(3).equals("§7Text after cleaning: §fheeello everybody"),
            two.toString());
        // testing does not count as a message
        for (int i = 0; i < 20; i++) {
            run("check same text again");
        }
        assertEquals(0, engine.spamState().size());
        assertTrue(events.stream().allMatch(e -> e.kind().equals(ModerationEvent.ADMIN_COMMAND)), "a test is not a violation");
        // also works when chat scanning is switched off
        set(CoreSettings.SCAN_CHAT, false);
        assertEquals(Chat.PREFIX + "§c§lBlocked. §7The text contains the blocked word §ffuck§7.", run("check fuck").get(0));

        List<String> info = run("info");
        assertEquals("§7Scanning: §fcommands, signs, books, anvil", info.get(2));
        assertEquals("§7Filters: spam §fon (commands, rate, repeat, caps, flood, words)§7 | links §fblock-all§7 | bypass tricks §flog-only", info.get(3));
        assertEquals("§7Check order: §fevasion, badword, link, spam-commands, spam-rate, spam-repeat, spam-caps, spam-flood, spam-words", info.get(4));
    }
}
