package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.ModerraSettings;
import com.jason.plugins.moderra.PunishmentAction;
import com.jason.plugins.moderra.engine.config.ConfigProblem;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {
    @TempDir
    Path dir;
    Fixture f;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
    }

    // ------------------------------------------------------------------------------------------
    // First start and reading the folder
    // ------------------------------------------------------------------------------------------

    @Test
    void firstStartCreatesTheWholeCommentedFolder() {
        Engine engine = f.start();
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());
        assertTrue(engine.problems().isEmpty());
        for (String file : List.of("config.yml", "moderation/ladder.yml", "worlds.yml", "wordlists/en.yml", "wordlists/custom.yml",
            "wordlists/no.yml", "wordlists/zh-tw.yml")) {
            assertTrue(Files.isRegularFile(f.file(file)), file);
        }
        assertTrue(Files.isDirectory(f.file("data")));
        assertTrue(TestFiles.read(f.file("config.yml")).contains("# How strict the filter is."));
        assertTrue(TestFiles.read(f.file("config.yml")).contains("config-version: 2"));
        assertTrue(f.platform.infos.stream().anyMatch(l -> l.startsWith("Created config files: config.yml, moderation/ladder.yml, worlds.yml")));
        assertTrue(f.platform.infos.stream().noneMatch(l -> l.contains("Old single-file")), "a fresh install is not a migration");

        // new installs: English plus the custom list
        assertEquals(List.of("en", "custom"), engine.settings().enabledLanguages());
        assertTrue(engine.settings().matcher().size() > 100);
        ModerraSettings s = engine.settings().legacy();
        assertEquals(2, s.violationsBeforePunish());
        assertTrue(s.scanChat() && s.scanCommands() && s.scanSigns() && s.scanBooks() && s.scanAnvil());
        assertTrue(s.scannedCommands().containsAll(List.of("msg", "tell", "say", "me")));
        assertTrue(s.ignoredCommands().containsAll(List.of("moderra")));
        // 6 effects + 11 attributes + 2 sounds + 2 commands, exactly one enabled by default (villager_no).
        assertEquals(21, s.punishmentActions().size());
        List<PunishmentAction> enabled = s.punishmentActions().stream().filter(PunishmentAction::enabled).toList();
        assertEquals(1, enabled.size());
        assertEquals(List.of("entity.villager.no", "1.0", "1.0"), enabled.get(0).args());
        assertEquals(List.of("minecraft:blindness", "0", "3"), s.punishmentActions().get(0).args());
        assertEquals("§8[§6§lModerra§8]§r §c§lMessage blocked. §f{player}§7 used a word that is not permitted: §c§o{word}§7.", s.warningBroadcastMessage());

        // a second start changes nothing on disk
        String before = TestFiles.read(f.file("config.yml"));
        f.start();
        assertEquals(before, TestFiles.read(f.file("config.yml")));
    }

    @Test
    void matchingAndProtectionLevels() {
        Engine engine = f.start();
        assertEquals("fuck", engine.find("what the f.u.c.k"));
        assertNull(engine.find("nice glass house"));
        assertNotNull(engine.find("you idiot"));
        TestFiles.edit(f.file("config.yml"), "protection-level: 3", "protection-level: 1");
        assertTrue(engine.reload().isEmpty());
        assertNull(engine.find("you idiot"), "level-1 words are ignored on LOW");
        assertNull(engine.find("you bitch"), "level-2 words are ignored on LOW");
        assertNotNull(engine.find("fuck"));
        TestFiles.edit(f.file("config.yml"), "protection-level: 1", "protection-level: 2");
        engine.reload();
        assertNull(engine.find("you idiot"));
        assertNotNull(engine.find("you bitch"));
    }

    @Test
    void reloadPicksUpEditsAndWrongValuesNameFileAndKey() {
        Engine engine = f.start();
        TestFiles.edit(f.file("config.yml"), "protection-level: 3", "protection-level: 12");
        TestFiles.edit(f.file("config.yml"), "chat: true", "chat: maybe\n    chatt: true");
        TestFiles.edit(f.file("moderation/ladder.yml"), "violations-before-punish: 2", "violations-before-punish: 5\n  surprise: 1");
        List<ConfigProblem> problems = engine.reload();
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.file().equals("config.yml") && "filters.protection-level".equals(p.key())
            && p.message().contains("12")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> "filters.scan.chat".equals(p.key()) && p.message().contains("true or false")));
        assertTrue(problems.stream().anyMatch(p -> "filters.scan.chatt".equals(p.key()) && p.message().contains("unknown key")));
        assertTrue(problems.stream().anyMatch(p -> p.file().equals("moderation/ladder.yml") && "punishment.surprise".equals(p.key())));
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("config.yml -> filters.protection-level")));
        // the rest still works: clamped / default values, and the good edit is active
        assertEquals(3, engine.settings().get(CoreSettings.PROTECTION_LEVEL));
        assertTrue(engine.settings().get(CoreSettings.SCAN_CHAT));
        assertEquals(5, engine.settings().legacy().violationsBeforePunish());
    }

    @Test
    void brokenFileKeepsThePreviousGoodSettingsAndDoesNotCrash() {
        Engine engine = f.start();
        TestFiles.edit(f.file("config.yml"), "protection-level: 3", "protection-level: 1");
        engine.reload();
        int words = engine.settings().matcher().size();
        String good = TestFiles.read(f.file("config.yml"));

        TestFiles.write(f.file("config.yml"), "filters: [unclosed");
        TestFiles.edit(f.file("moderation/ladder.yml"), "violations-before-punish: 2", "violations-before-punish: 7");
        List<ConfigProblem> problems = engine.reload();
        assertEquals(1, problems.size(), problems.toString());
        assertEquals("config.yml", problems.get(0).file());
        assertTrue(problems.get(0).message().contains("keeping the previous settings"), problems.get(0).message());
        assertEquals(1, engine.settings().get(CoreSettings.PROTECTION_LEVEL), "previous value, not the default 3");
        assertEquals(words, engine.settings().matcher().size());
        assertEquals(7, engine.settings().legacy().violationsBeforePunish(), "the other, healthy file is still read");
        assertEquals("filters: [unclosed", TestFiles.read(f.file("config.yml")), "a broken file is never overwritten or 'migrated'");
        assertFalse(Files.exists(f.file("wordlists/english.yml")));

        TestFiles.write(f.file("wordlists/de.yml"), "level-1: [oops");
        problems = engine.reload();
        assertTrue(problems.stream().anyMatch(p -> p.file().equals("wordlists/de.yml")), problems.toString());

        TestFiles.write(f.file("config.yml"), good);
        Files.exists(f.file("wordlists/de.yml"));
        TestFiles.write(f.file("wordlists/de.yml"), "language: de\nlevel-3: [\"testwort\"]\n");
        assertTrue(engine.reload().isEmpty());

        // a broken file at the very first start: built-in defaults
        Fixture fresh = new Fixture(dir.resolve("other"));
        TestFiles.write(fresh.file("config.yml"), "- a list\n- not settings\n");
        Engine other = fresh.start();
        assertEquals(1, other.problems().size());
        assertTrue(other.problems().get(0).message().contains("built-in defaults"));
        assertEquals("fuck", other.find("fuck"));
    }

    // ------------------------------------------------------------------------------------------
    // inspect -> verdict -> follow-up
    // ------------------------------------------------------------------------------------------

    @Test
    void commandScanningRules() {
        Engine engine = f.start();
        EffectiveSettings s = engine.settings();
        assertEquals("Bob fuck", Engine.commandTextToScan(s, "/msg Bob fuck"));
        assertEquals("x y", Engine.commandTextToScan(s, "/Minecraft:MSG x y"));
        assertEquals("x y", Engine.commandTextToScan(s, "msg x y"));
        assertNull(Engine.commandTextToScan(s, "/moderra addword fuck"), "ignored command");
        assertNull(Engine.commandTextToScan(s, "/gamemode creative"), "not in scanned-commands");
        assertNull(Engine.commandTextToScan(s, "/home"), "no arguments");

        UUID bob = f.platform.join("Bob");
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.COMMAND, "/msg Al you fuck")).blocked());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.COMMAND, "/moderra addword fuck")).allowed());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.COMMAND, "/gamemode fuck")).allowed());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.COMMAND, "/fuck")).allowed());
        TestFiles.edit(f.file("config.yml"), "    - msg\n", "    - \"*\"\n");
        engine.reload();
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.COMMAND, "/gamemode fuck")).blocked());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.COMMAND, "/moderra addword fuck")).allowed());
    }

    @Test
    void verdictsBypassAndScanSwitches() {
        Engine engine = f.start();
        UUID bob = f.platform.join("Bob");
        UUID vip = f.platform.join("Vip", Engine.PERMISSION_BYPASS);

        Verdict clean = engine.inspect(engine.message(bob, "Bob", Message.CHAT, "hello there"));
        assertTrue(clean.allowed());
        assertFalse(clean.hasHits());
        assertNull(clean.primary());

        Verdict bad = engine.inspect(engine.message(bob, "Bob", Message.CHAT, "what the FUUUUCK"));
        assertTrue(bad.blocked());
        assertEquals(Verdict.Type.BLOCK, bad.type());
        Hit hit = bad.primary();
        assertEquals("badword", hit.check());
        assertEquals("fuck", hit.detail());
        assertEquals(3, hit.severity());
        assertEquals(Hit.Action.BLOCK, hit.action());
        assertEquals(1, hit.strikeWeight());
        assertEquals(1, engine.inspect(engine.message(bob, "Bob", Message.CHAT, "idiot")).primary().severity());
        assertEquals(0, engine.strikes().get(bob), "inspect alone has no side effects");

        assertTrue(engine.inspect(engine.message(vip, "Vip", Message.CHAT, "fuck")).allowed(), "bypass permission");

        for (String[] pair : new String[][] {{"chat", Message.CHAT}, {"signs", Message.SIGN}, {"books", Message.BOOK}, {"anvil", Message.ANVIL},
            {"commands", Message.COMMAND}}) {
            String text = pair[1].equals(Message.COMMAND) ? "/msg x fuck" : "fuck";
            assertTrue(engine.inspect(engine.message(bob, "Bob", pair[1], text)).blocked(), pair[1]);
            TestFiles.edit(f.file("config.yml"), "    " + pair[0] + ": true", "    " + pair[0] + ": false");
            engine.reload();
            assertTrue(engine.inspect(engine.message(bob, "Bob", pair[1], text)).allowed(), pair[1] + " switched off");
        }
        assertTrue(engine.inspect(engine.message(bob, "Bob", "some-new-source", "fuck")).blocked(), "a source without a switch is scanned");

        // several texts that belong together: first hit wins
        Engine again = new Fixture(dir.resolve("b")).start();
        Message sign = Message.of(bob, "Bob", "world", Message.SIGN, "", n -> false);
        Verdict v = again.inspectFirst(sign, java.util.Arrays.asList("Welcome", null, "you idiot", "fuck"));
        assertEquals("idiot", v.primary().detail());
        assertTrue(again.inspectFirst(sign, List.of("Welcome", "to my", "base")).allowed());
        assertTrue(again.inspectFirst(sign, List.of()).allowed());
    }

    @Test
    void warnThenPunishThenReset() {
        Engine engine = f.start();
        UUID bob = f.platform.join("Bob");
        UUID staff = f.platform.join("Mod", Engine.PERMISSION_NOTIFY);
        UUID other = f.platform.join("Alice");
        List<ModerationEvent> events = new ArrayList<>();
        engine.events().subscribe(events::add);

        assertTrue(f.say(bob, Message.CHAT, "fuck this").blocked());
        assertEquals(1, engine.strikes().get(bob));
        assertEquals(List.of("§8[§6§lModerra§8]§r §c§lMessage blocked. §fBob§7 used a word that is not permitted: §c§ofuck§7."), f.platform.broadcasts);
        assertEquals(List.of("§8[§6§lModerra§8]§r §e§lWarning. §7Your message contains a word that is not permitted. After §f1§7 more violations, a penalty is applied."), f.platform.sentTo(bob));
        assertEquals(List.of("§8[§6§lModerra§8]§r §6§lStaff notice. §7Blocked §c§ofuck§7 from §fBob§7 in §fchat§7. Violations: §f1§7 of §f2§7."), f.platform.sentTo(staff));
        assertTrue(f.platform.sentTo(other).isEmpty());
        assertTrue(f.platform.infos.contains("Bob said blocked word 'fuck' in chat (violation 1/2)"));
        assertEquals(1, events.size());
        ModerationEvent first = events.get(0);
        assertEquals("violation", first.kind());
        assertEquals(bob, first.playerId());
        assertEquals("Bob", first.playerName());
        assertEquals("world", first.world());
        assertEquals("chat", first.source());
        assertEquals(f.now.get(), first.time());
        assertEquals("badword", first.data().get("check"));
        assertEquals("fuck", first.data().get("word"));
        assertEquals("1", first.data().get("count"));
        assertEquals("2", first.data().get("limit"));
        assertEquals("block", first.data().get("action"));
        assertEquals("default", first.data().get("profile"));
        assertEquals("fuck this", first.data().get("text"));

        f.platform.broadcasts.clear();
        f.platform.sentTo(bob).clear();
        assertTrue(f.say(bob, Message.SIGN, "shit").blocked());
        assertEquals(List.of("sound:villager_no"), f.platform.appliedActions);
        assertEquals(List.of("§8[§6§lModerra§8]§r §c§lPenalty. §fBob§7 used a word that is not permitted: §c§oshit§7. The penalty is: §fvillager no sound§7."),
            f.platform.broadcasts);
        assertEquals(List.of("§8[§6§lModerra§8]§r §c§lPenalty. §7You used too many words that are not permitted. The penalty is: §fvillager no sound§7."), f.platform.sentTo(bob));
        assertEquals(0, engine.strikes().get(bob), "strikes start again after a punishment");
        assertTrue(f.platform.consoleCommands.isEmpty());
        assertEquals(List.of("violation", "violation", "punishment"), events.stream().map(ModerationEvent::kind).toList());
        assertEquals("villager no sound", events.get(2).data().get("punishments"));
        assertEquals("sign", events.get(2).source());
    }

    @Test
    void punishmentDisabledOnlyBlocksAndQuietModes() {
        f.start();
        TestFiles.edit(f.file("moderation/ladder.yml"), "  enabled: true\n  # How many violations", "  enabled: false\n  # How many violations");
        TestFiles.edit(f.file("config.yml"), "broadcast-warnings: true", "broadcast-warnings: false");
        TestFiles.edit(f.file("config.yml"), "notify-staff: true", "notify-staff: false");
        TestFiles.edit(f.file("config.yml"), "console: true", "console: false");
        Engine engine = f.start();
        UUID bob = f.platform.join("Bob");
        UUID staff = f.platform.join("Mod", Engine.PERMISSION_NOTIFY);
        f.platform.infos.clear();
        f.say(bob, Message.CHAT, "fuck");
        f.say(bob, Message.CHAT, "fuck");
        assertEquals(2, f.platform.sentTo(bob).size());
        assertTrue(f.platform.sentTo(bob).get(1).contains("No penalty is applied at this time"));
        assertTrue(f.platform.broadcasts.isEmpty());
        assertTrue(f.platform.sentTo(staff).isEmpty());
        assertTrue(f.platform.infos.isEmpty(), f.platform.infos.toString());
        assertTrue(f.platform.appliedActions.isEmpty());
        assertEquals(2, engine.strikes().get(bob));
    }

    @Test
    void violationsExpireAndClearOnQuit() {
        f.start();
        TestFiles.edit(f.file("moderation/ladder.yml"), "violations-expire-minutes: 0", "violations-expire-minutes: 5");
        TestFiles.edit(f.file("moderation/ladder.yml"), "violations-before-punish: 2", "violations-before-punish: 10");
        Engine engine = f.start();
        UUID id = f.platform.join("Bob");
        assertEquals(1, engine.strikes().add(id, 1));
        f.now.addAndGet(4 * 60_000L);
        assertEquals(2, engine.strikes().add(id, 1));
        f.now.addAndGet(6 * 60_000L);
        assertEquals(0, engine.strikes().get(id));
        assertEquals(1, engine.strikes().add(id, 1), "old violations expired first");
        assertEquals(4, engine.strikes().add(id, 3), "weights add up");

        engine.onQuit(id);
        assertEquals(0, engine.strikes().get(id), "clear-violations-on-quit is true by default");
        TestFiles.edit(f.file("moderation/ladder.yml"), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        engine.reload();
        engine.strikes().add(id, 1);
        engine.onQuit(id);
        assertEquals(1, engine.strikes().get(id));
        engine.shutdown();
        assertEquals(0, engine.strikes().get(id));
    }

    @Test
    void finalCommandCommandActionsAndFailingActions() {
        f.start();
        TestFiles.edit(f.file("moderation/ladder.yml"), "final-command:\n    # Run the command below after the punishment actions.\n    enabled: false",
            "final-command:\n    enabled: true");
        TestFiles.edit(f.file("moderation/ladder.yml"), "command: \"\"", "command: \"/kick {player} {word}\"");
        TestFiles.edit(f.file("moderation/ladder.yml"), "kick:\n      enabled: false\n      command: \"kick {player} Please watch your language!\"",
            "kick:\n      enabled: true\n      command: \"/say {PLAYER} was warned\"\n    broken:\n      enabled: true");
        TestFiles.edit(f.file("moderation/ladder.yml"), "blindness:\n      enabled: false", "blindness:\n      enabled: true");
        Engine engine = f.start();
        assertTrue(f.platform.warnings.contains("punishment.commands.broken is enabled but incomplete; it will be ignored."), f.platform.warnings.toString());
        UUID bob = f.platform.join("Bob");
        f.say(bob, Message.CHAT, "fuck");
        f.say(bob, Message.CHAT, "fuck");
        assertEquals(List.of("say Bob was warned", "kick Bob fuck"), f.platform.consoleCommands);
        assertEquals(List.of("effect:blindness", "sound:villager_no"), f.platform.appliedActions);
        assertTrue(f.platform.sentTo(bob).get(1).contains("§fblindness, villager no sound, and server command§7"), f.platform.sentTo(bob).get(1));
        assertEquals(0, engine.strikes().get(bob));
    }

    // ------------------------------------------------------------------------------------------
    // Extension points
    // ------------------------------------------------------------------------------------------

    /** A check that censors "darn" and logs "meh", to prove the pipeline rules. */
    static final class DemoCheck implements TextCheck {
        final List<String> seen = new ArrayList<>();

        @Override
        public String id() {
            return "demo";
        }

        @Override
        public boolean enabled(EffectiveSettings settings) {
            return settings.get(CoreSettings.SCAN_BOOKS); // any real setting works as an on/off switch
        }

        @Override
        public Optional<Hit> check(Message message, EffectiveSettings settings) {
            seen.add(message.text());
            if (message.text().contains("darn")) {
                return Optional.of(Hit.censor("demo", "darn", 1, 0, message.text().replace("darn", "****")));
            }
            if (message.text().contains("meh")) {
                return Optional.of(Hit.logOnly("demo", "meh", 1, 0));
            }
            if (message.text().contains("crash")) {
                throw new IllegalStateException("check bug");
            }
            return Optional.empty();
        }
    }

    @Test
    void checksRunInConfiguredOrderAndCanCensorOrLog() {
        Engine engine = f.start();
        DemoCheck demo = new DemoCheck();
        engine.registerCheck(50, demo);
        assertEquals(List.of("evasion", "badword", "link", "spam-commands", "spam-rate", "spam-repeat", "spam-caps", "spam-flood", "spam-words",
            "demo"), engine.checks(engine.settings()).stream().map(TextCheck::id).toList(),
            "the checks named in filters.check-order run first (spam = all spam rules), the others afterwards");
        UUID bob = f.platform.join("Bob");
        List<ModerationEvent> events = new ArrayList<>();
        List<ModerationEvent> ownKind = new ArrayList<>();
        engine.events().subscribe(e -> (e.kind().equals(ModerationEvent.VIOLATION) ? events : ownKind).add(e));
        f.platform.rewritable.add(Message.CHAT);

        Verdict censored = f.say(bob, Message.CHAT, "oh darn it");
        assertTrue(censored.censored());
        assertEquals("oh **** it", censored.text());
        assertEquals(0, engine.strikes().get(bob), "weight 0: no strike");
        assertTrue(f.platform.sentTo(bob).isEmpty() && f.platform.broadcasts.isEmpty(), "weight 0: no warning");
        assertEquals("censor", events.get(0).data().get("action"));
        assertEquals("demo", events.get(0).data().get("check"));

        Verdict logged = f.say(bob, Message.CHAT, "meh");
        assertTrue(logged.allowed() && logged.hasHits());
        assertEquals(2, events.size());
        assertEquals(List.of("demo", "demo"), ownKind.stream().map(ModerationEvent::kind).toList(), "plus one event of the check's own kind per hit");
        assertEquals("log-only", ownKind.get(1).data().get("action"));

        assertTrue(f.say(bob, Message.CHAT, "darn fuck").blocked(), "badword runs first and blocks");
        assertFalse(demo.seen.contains("darn fuck"), "a block ends the pipeline");

        assertTrue(f.say(bob, Message.CHAT, "crash").allowed(), "a failing check is skipped, not fatal");
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("Check 'demo' failed")));

        // order from config: demo first -> badword sees the censored text
        TestFiles.edit(f.file("config.yml"), "  check-order:\n    - evasion\n    - badword\n    - link\n    - spam\n", "  check-order:\n    - demo\n    - badword\n");
        assertTrue(engine.reload().isEmpty());
        assertEquals(List.of("demo", "badword"), engine.checks(engine.settings()).stream().map(TextCheck::id).toList().subList(0, 2));
        Verdict both = engine.inspect(engine.message(bob, "Bob", Message.CHAT, "darn fuck"));
        assertTrue(both.blocked());
        assertEquals(List.of("demo", "badword"), both.hits().stream().map(Hit::check).toList());
        assertEquals("badword", both.primary().check());

        // without an entry in check-order the registered number decides
        TestFiles.edit(f.file("config.yml"), "  check-order:\n    - demo\n    - badword\n", "  check-order: []\n");
        engine.reload();
        assertEquals(List.of("evasion", "demo", "badword", "link"), engine.checks(engine.settings()).stream().map(TextCheck::id).toList().subList(0, 4),
            "same number: the one registered first; 50 before 100");

        // enabled-state comes from config
        TestFiles.edit(f.file("config.yml"), "books: true", "books: false");
        engine.reload();
        demo.seen.clear();
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.CHAT, "darn")).allowed());
        assertTrue(demo.seen.isEmpty());
    }

    @Test
    void strikeServiceCanBeReplacedAndSubscribersCannotBreakTheEngine() {
        Engine engine = f.start();
        UUID bob = f.platform.join("Bob");
        List<String> calls = new ArrayList<>();
        engine.setStrikeService(new StrikeService() {
            @Override
            public int add(UUID player, int weight) {
                calls.add("add " + weight);
                return 99;
            }

            @Override
            public int get(UUID player) {
                return 99;
            }

            @Override
            public void reset(UUID player) {
                calls.add("reset");
            }

            @Override
            public void clear() {
                calls.add("clear");
            }
        });
        engine.events().subscribe(e -> {
            throw new IllegalStateException("bad subscriber");
        });
        List<String> kinds = new ArrayList<>();
        Runnable unsubscribe = engine.events().subscribe(e -> kinds.add(e.kind()));
        f.say(bob, Message.CHAT, "fuck");
        assertEquals(List.of("add 1", "reset"), calls, "99 >= limit: punished at once, then reset");
        assertEquals(List.of("violation", "punishment"), kinds);
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("event subscriber failed")));
        unsubscribe.run();
        f.say(bob, Message.CHAT, "fuck");
        assertEquals(2, kinds.size());
    }

    @Test
    void anvilCountsOncePerSession() {
        Engine engine = f.start();
        UUID bob = f.platform.join("Bob");
        assertTrue(engine.firstAnvilViolation(bob));
        assertFalse(engine.firstAnvilViolation(bob));
        engine.anvilClosed(bob);
        assertTrue(engine.firstAnvilViolation(bob));
        engine.onQuit(bob);
        assertTrue(engine.firstAnvilViolation(bob));
    }

    // ------------------------------------------------------------------------------------------
    // Programmatic edits
    // ------------------------------------------------------------------------------------------

    @Test
    void customWordsPersistAndKeepComments() {
        Engine engine = f.start();
        String before = TestFiles.read(f.file("wordlists/custom.yml"));
        assertTrue(engine.addCustomWord("Zorblax"));
        assertFalse(engine.addCustomWord("zorblax"));
        assertFalse(engine.addCustomWord("x"));
        assertTrue(engine.addCustomWord("two  Words"));
        assertEquals("zorblax", engine.find("hey zorblax"));
        assertEquals("two words", engine.find("say two words now"));
        assertEquals(List.of("zorblax", "two words"), new Fixture(dir).start().listWords("custom"), "survives a restart");
        String after = TestFiles.read(f.file("wordlists/custom.yml"));
        assertTrue(after.contains("level-3:\n  - zorblax\n  - two words\n"), after);
        assertEquals(before.lines().filter(l -> l.trim().startsWith("#")).toList(), after.lines().filter(l -> l.trim().startsWith("#")).toList(),
            "comments must survive");
        // custom words count at every protection level
        TestFiles.edit(f.file("config.yml"), "protection-level: 3", "protection-level: 1");
        engine.reload();
        assertEquals("zorblax", engine.find("hey zorblax"));
        assertTrue(engine.removeCustomWord("ZORBLAX"));
        assertTrue(engine.removeCustomWord("two words"));
        assertFalse(engine.removeCustomWord("two words"));
        assertNull(engine.find("hey zorblax"));
        assertEquals(before, TestFiles.read(f.file("wordlists/custom.yml")), "back to the original file");
        // a word the admin typed into another tier by hand is found and removed too
        TestFiles.edit(f.file("wordlists/custom.yml"), "level-1: []", "level-1: [\"handmade\"]");
        engine.reload();
        assertTrue(engine.removeCustomWord("handmade"));
        assertEquals(List.of(), engine.customWords());
    }

    @Test
    void settingAValueWritesTheFileKeepsCommentsAndValidates() throws Exception {
        Engine engine = f.start();
        String before = TestFiles.read(f.file("config.yml"));
        engine.set(CoreSettings.PROTECTION_LEVEL, 2);
        engine.set(CoreSettings.WARNING_PLAYER, "&cStop it, {player}!");
        assertEquals(2, engine.value(CoreSettings.PROTECTION_LEVEL));
        assertEquals("§cStop it, {player}!", engine.settings().legacy().warningPlayerMessage());
        String after = TestFiles.read(f.file("config.yml"));
        assertEquals(before.replace("protection-level: 3", "protection-level: 2")
            .replace("\"&8[&6&lModerra&8]&r &e&lWarning. &7Your message contains a word that is not permitted. After &f{remaining}&7 more violations, a penalty is applied.\"", "\"&cStop it, {player}!\""), after);
        try {
            engine.set(CoreSettings.PROTECTION_LEVEL, 9);
            throw new AssertionError("9 must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("filters.protection-level"));
        }
        assertEquals(after, TestFiles.read(f.file("config.yml")));
        Setting<?> found = engine.registry().get("filters.protection-level");
        assertSame(CoreSettings.PROTECTION_LEVEL, found);
    }

    @Test
    void reloadListenersRun() {
        Engine engine = f.start();
        int[] runs = {0};
        engine.addReloadListener(() -> runs[0]++);
        engine.addReloadListener(() -> {
            throw new IllegalStateException("x");
        });
        engine.reload();
        engine.reload();
        assertEquals(2, runs[0]);
    }
}
