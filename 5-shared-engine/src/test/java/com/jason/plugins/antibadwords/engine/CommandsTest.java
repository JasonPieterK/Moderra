package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandContext;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.command.SubCommand;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.YamlTree;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code /abw}: the old sub-commands (same answers as before), {@code lang}, {@code whitelist}, and adding commands. */
class CommandsTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;

    static final class Sender implements CommandSender {
        final Set<String> permissions;
        final List<String> out = new ArrayList<>();

        Sender(String... permissions) {
            this.permissions = Set.of(permissions);
        }

        @Override
        public String name() {
            return "Admin";
        }

        @Override
        public UUID id() {
            return null;
        }

        @Override
        public boolean has(String permission) {
            return permissions.contains(permission);
        }

        @Override
        public void send(String message) {
            out.add(message);
        }
    }

    final Sender admin = new Sender(Engine.PERMISSION_ADMIN);

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
    }

    private List<String> run(Sender sender, String line) {
        sender.out.clear();
        engine.commands().execute(sender, "abw", line.isEmpty() ? new String[0] : line.split(" "));
        return new ArrayList<>(sender.out);
    }

    private List<String> run(String line) {
        return run(admin, line);
    }

    private List<String> complete(String line) {
        return engine.commands().complete(admin, line.split(" ", -1));
    }

    @Test
    void nonAdminGetsNoPermissionEverywhere() {
        Sender user = new Sender();
        assertEquals(List.of("§cNo permission."), run(user, ""));
        assertEquals(List.of("§cNo permission."), run(user, "listwords"));
        assertEquals(List.of("§cNo permission."), run(user, "reload"));
        assertEquals(List.of("§cNo permission."), run(user, "whitelist add x"));
        assertEquals(List.of("§cNo permission."), run(user, "bogus"));
        assertEquals(List.of(), engine.commands().complete(user, new String[] {""}));
        assertEquals(List.of(), engine.commands().complete(user, new String[] {"whitelist", ""}));
    }

    @Test
    void existingSubcommandsAnswerAsBefore() {
        assertEquals(List.of("§eUsage: /abw <reload|addword|removeword|listwords|check|violations|resetviolations|info|lang|whitelist>"), run(""));
        assertEquals(List.of("§cBlocked: matches 'fuck'."), run("check what the fuck"));
        assertEquals(List.of("§aClean: no blocked word found."), run("check hello"));
        assertEquals(List.of("§aAdded custom blocked word."), run("addword zorblax two"));
        assertEquals(List.of("§cWord already exists."), run("addword zorblax two"));
        assertEquals(List.of("§cThat is not a valid word (use at least 2 letters or digits)."), run("addword x"));
        assertEquals(List.of("§cBlocked: matches 'zorblax two'."), run("check a zorblax two b"));
        assertEquals(List.of("zorblax two"), complete("removeword zo"));
        assertEquals(List.of("§aRemoved custom blocked word."), run("removeword zorblax two"));
        assertEquals(List.of("§cWord not found in the custom list."), run("removeword nothing"));
        assertEquals(List.of("§eUnknown subcommand."), run("bogus"));
        assertEquals(List.of("§eUsage: /abw addword <word or phrase>"), run("addword"));
        assertEquals(List.of("§eUsage: /abw check <text>"), run("check"));
        assertTrue(run("reload").get(0).matches("§aAntiBadWords config reloaded \\(\\d+ blocked words active\\)\\."));

        UUID bob = f.platform.join("Bob");
        f.platform.join("Alice");
        engine.strikes().add(bob, 1);
        assertEquals(List.of("§eBob violations: 1/2"), run("violations Bob"));
        assertEquals(List.of("§aViolations reset for Bob."), run("resetviolations Bob"));
        assertEquals(0, engine.strikes().get(bob));
        assertEquals(List.of("§cPlayer not online."), run("violations Nobody"));
        assertEquals(List.of("§eUsage: /abw violations <player>"), run("violations"));

        List<String> info = run("info");
        assertEquals("§6AntiBadWords 1", info.get(0));
        assertTrue(info.get(1).startsWith("§7Blocked words: §f"));
        assertEquals("§7Scanning: §fchat, commands, signs, books, anvil", info.get(2));
        assertTrue(run("list en").get(0).startsWith("§6EN words ("));
        assertTrue(run("listwords english").get(0).startsWith("§6ENGLISH words ("), "old language names still work");
        assertTrue(run("listwords").get(0).startsWith("§6ACTIVE words ("));
        assertEquals(List.of("§cNo words found for list: nope"), run("listwords nope"));

        assertEquals(List.of("reload", "removeword", "resetviolations"), complete("re"));
        assertEquals(List.of("listwords", "lang"), complete("l"));
        assertTrue(complete("listwords d").containsAll(List.of("da", "de")));
        assertTrue(complete("list ").containsAll(List.of("custom", "active")));
        assertEquals(List.of("Alice"), complete("violations al"));
        assertEquals(List.of(), complete("check x"));
    }

    @Test
    void reloadShowsConfigProblems() {
        TestFiles.edit(f.file("config.yml"), "protection-level: 3", "protection-level: banana");
        List<String> out = run("reload");
        assertEquals(3, out.size(), out.toString());
        assertEquals("§e1 config problem(s) were found (full list in the console):", out.get(1));
        assertTrue(out.get(2).startsWith("§c- config.yml -> filters.protection-level: 'banana' is not valid"), out.get(2));
    }

    @Test
    void langListEnableDisable() {
        List<String> list = run("lang list");
        assertEquals("§6Languages (2 of 38 on, green = on):", list.get(0));
        assertTrue(list.get(1).contains("§aen") && list.get(1).contains("§7de") && list.get(1).contains("§acustom"), list.get(1));
        assertEquals(list, run("lang"));
        assertNull(engine.find("arschloch"));

        String before = TestFiles.read(f.file("config.yml"));
        List<String> out = run("lang enable DE");
        assertTrue(out.get(0).matches("§aLanguage de \\(German\\) switched on\\. \\d+ blocked words active\\."), out.toString());
        assertEquals("arschloch", engine.find("arschloch"), "works at once");
        assertEquals(before.replace("    - en\n    - custom\n", "    - en\n    - custom\n    - de\n"), TestFiles.read(f.file("config.yml")),
            "only one line of config.yml changed; every comment is still there");
        assertEquals(List.of("en", "de", "custom"), engine.settings().enabledLanguages());
        assertEquals(List.of("§eLanguage de is already on."), run("lang enable german"));
        assertEquals(List.of("§cUnknown language: klingon. See /abw lang list."), run("lang enable klingon"));
        assertEquals(List.of("§eThe custom list is always on."), run("lang disable custom"));
        assertEquals(List.of("§eUsage: /abw lang enable <language code>"), run("lang enable"));
        assertEquals(List.of("§eUsage: /abw lang <list|enable|disable> [language code]"), run("lang dance"));
        assertEquals("§6Languages (3 of 38 on, green = on):", run("lang list").get(0));

        assertEquals(List.of("list", "enable", "disable"), complete("lang "));
        assertEquals(List.of("de"), complete("lang disable d"));
        assertEquals(List.of("da"), complete("lang enable d"));
        assertFalse(complete("lang enable ").contains("custom"));

        assertTrue(run("lang disable de").get(0).startsWith("§aLanguage de switched off."));
        assertNull(engine.find("arschloch"));
        assertEquals(before, TestFiles.read(f.file("config.yml")));
        assertEquals(List.of("§eLanguage de is not on."), run("lang disable de"));
        assertTrue(new Fixture(dir).start().settings().enabledLanguages().equals(List.of("en", "custom")), "saved for the next start");

        // an old name written by hand is switched off under its new code
        TestFiles.edit(f.file("config.yml"), "    - en\n", "    - English\n    - german\n");
        engine.reload();
        assertTrue(run("lang disable de").get(0).startsWith("§aLanguage de switched off."));
        assertEquals(List.of("English", "custom"), YamlTree.parse(TestFiles.read(f.file("config.yml"))).getStringList("filters.enabled-word-lists"));
    }

    @Test
    void whitelistAddRemoveListWithPages() {
        TestFiles.edit(f.file("config.yml"), "partial-match: false", "partial-match: true");
        engine.reload();
        assertEquals("shit", engine.find("mishits"));
        String before = TestFiles.read(f.file("config.yml"));

        assertEquals(List.of("§aAdded to the whitelist: mishits"), run("whitelist add MisHits"));
        assertNull(engine.find("some Mishits happen"), "applies at once, whatever the capital letters");
        assertEquals(before.replace("    - therapist\n", "    - therapist\n    - mishits\n"), TestFiles.read(f.file("config.yml")));
        assertEquals(List.of("§cAlready on the whitelist: mishits"), run("whitelist add MISHITS"));
        assertEquals(List.of("§aAdded to the whitelist: two words"), run("whitelist add Two   Words"));
        assertEquals(List.of("§cThat is not a valid word (use at least 2 letters or digits)."), run("whitelist add !"));
        assertEquals(List.of("§eUsage: /abw whitelist add <word or phrase>"), run("whitelist add"));
        assertEquals(List.of("§eUsage: /abw whitelist <add|remove|list> [word or page]"), run("whitelist"));

        // 22 words, 10 per page (commands.list-page-size)
        List<String> page1 = run("whitelist list");
        assertEquals("§6Whitelist (22 words) - page 1/3:", page1.get(0));
        assertEquals("§7class, classic, assassin, assume, assist, bass, glass, grass, pass, mass", page1.get(1));
        assertEquals("§7Next page: /abw whitelist list 2", page1.get(2));
        List<String> page3 = run("whitelist list 3");
        assertEquals(List.of("§6Whitelist (22 words) - page 3/3:", "§7mishits, two words"), page3);
        assertEquals(page3, run("whitelist list 99"));
        assertEquals(page1, run("whitelist list 0"));
        assertEquals(List.of("§eUsage: /abw whitelist list [page number]"), run("whitelist list abc"));
        TestFiles.edit(f.file("config.yml"), "list-page-size: 10", "list-page-size: 50");
        engine.reload();
        assertEquals(2, run("whitelist list").size(), "the page size is a setting");

        assertEquals(List.of("add", "remove", "list"), complete("whitelist "));
        assertEquals(List.of("mishits", "mass"), complete("whitelist remove m").stream().sorted((a, b) -> b.compareTo(a)).toList());
        assertEquals(List.of("§aRemoved from the whitelist: mishits"), run("whitelist remove MISHITS"));
        assertEquals("shit", engine.find("mishits"));
        assertEquals(List.of("§cNot on the whitelist: mishits"), run("whitelist remove mishits"));
        assertEquals(List.of("§aRemoved from the whitelist: two words"), run("whitelist remove two words"));
        assertEquals(before, TestFiles.read(f.file("config.yml")).replace("list-page-size: 50", "list-page-size: 10"));
        assertFalse(engine.value(CoreSettings.WHITELIST).contains("mishits"));

        TestFiles.edit(f.file("config.yml"), "  whitelist:\n", "  whitelist: []\n  old-whitelist:\n");
        engine.reload();
        assertEquals(List.of("§eThe whitelist is empty."), run("whitelist list"));
        assertEquals(List.of("§aAdded to the whitelist: first"), run("whitelist add first"));
        assertEquals(List.of("first"), engine.value(CoreSettings.WHITELIST));
    }

    @Test
    void changingCommandsPublishAdminEvents() {
        List<String> seen = new ArrayList<>();
        engine.events().subscribe(e -> {
            if (e.kind().equals("admin-command")) {
                seen.add(e.data().get("sender") + ": " + e.data().get("command"));
                assertNull(e.playerId());
            }
        });
        run("check fuck");
        run("info");
        run("lang list");
        run("whitelist list");
        run("addword zorblax");
        run("addword zorblax");
        run("whitelist add okword");
        run("lang enable de");
        run("reload");
        assertEquals(List.of("Admin: abw addword zorblax", "Admin: abw whitelist add okword", "Admin: abw lang enable de", "Admin: abw reload"), seen);
    }

    /** What another session does to add a command: one class, one register call. */
    static final class HelloCommand implements SubCommand {
        @Override
        public String name() {
            return "hello";
        }

        @Override
        public List<String> aliases() {
            return List.of("hi");
        }

        @Override
        public String permission() {
            return "antibadwords.hello";
        }

        @Override
        public void execute(CommandContext context) {
            if (context.args().length == 0) {
                context.usage("<name>");
            } else if (context.args()[0].equals("boom")) {
                throw new IllegalStateException("kaput");
            } else {
                context.send("Hello " + context.joined(0) + " from " + context.sender().name());
            }
        }

        @Override
        public List<String> complete(CommandContext context) {
            return List.of("world");
        }
    }

    @Test
    void addingASubCommandIsOneClass() {
        engine.commands().register(new HelloCommand());
        assertNotNull(engine.commands().find("HI"));
        Sender greeter = new Sender("antibadwords.hello");
        assertEquals(List.of("§eUsage: /abw <hello>"), run(greeter, ""), "the usage line only shows what the sender may run");
        assertEquals(List.of("Hello big world from Admin"), run(greeter, "hi big world"));
        assertEquals(List.of("§eUsage: /abw hello <name>"), run(greeter, "hello"));
        assertEquals(List.of("§cNo permission."), run(greeter, "reload"));
        assertEquals(List.of("§cNo permission."), run(admin, "hello x"), "its own permission, not the admin one");
        assertEquals(List.of("hello"), engine.commands().complete(greeter, new String[] {""}));
        assertEquals(List.of("world"), engine.commands().complete(greeter, new String[] {"hello", ""}));
        assertEquals(List.of("§cThe command failed: kaput"), run(greeter, "hello boom"));
        assertTrue(engine.commands().names().contains("hello"));
    }
}
