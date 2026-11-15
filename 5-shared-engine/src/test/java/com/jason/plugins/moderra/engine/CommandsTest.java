package com.jason.plugins.moderra.engine;

import com.jason.plugins.moderra.engine.command.Chat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.command.CommandContext;
import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.command.SubCommand;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.YamlTree;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code /moderra}: the old sub-commands (same answers as before), {@code lang}, {@code whitelist}, and adding commands. */
class CommandsTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;

    static class Sender implements CommandSender {
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
        engine.commands().execute(sender, "moderra", line.isEmpty() ? new String[0] : line.split(" "));
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
        assertEquals(List.of(Chat.error("No permission.")), run(user, ""));
        assertEquals(List.of(Chat.error("No permission.")), run(user, "listwords"));
        assertEquals(List.of(Chat.error("No permission.")), run(user, "reload"));
        assertEquals(List.of(Chat.error("No permission.")), run(user, "whitelist add x"));
        assertEquals(List.of(Chat.error("No permission.")), run(user, "bogus"));
        assertEquals(List.of(), engine.commands().complete(user, new String[] {""}));
        assertEquals(List.of(), engine.commands().complete(user, new String[] {"whitelist", ""}));
    }

    @Test
    void existingSubcommandsAnswerAsBefore() {
        assertEquals(List.of(Chat.usage("/moderra <reload|addword|removeword|listwords|check|violations|resetviolations|info|lang|whitelist|ladder|strikes|network>")), run(""));
        assertEquals(List.of(Chat.PREFIX + "§c§lBlocked. §7The text contains the blocked word §ffuck§7.", "§7Check: §fbadword§7 (word list entry §ffuck§7, level 3)"), run("check what the fuck"));
        assertEquals(List.of(Chat.ok("Clean: no blocked word found.")), run("check hello"));
        assertEquals(List.of(Chat.ok("Added custom blocked word.")), run("addword zorblax two"));
        assertEquals(List.of(Chat.error("Word already exists.")), run("addword zorblax two"));
        assertEquals(List.of(Chat.error("That is not a valid word (use at least 2 letters or digits).")), run("addword x"));
        assertEquals(List.of(Chat.PREFIX + "§c§lBlocked. §7The text contains the blocked word §fzorblax two§7.", "§7Check: §fbadword§7 (word list entry §fzorblax two§7, level 3)"), run("check a zorblax two b"));
        assertEquals(List.of("zorblax two"), complete("removeword zo"));
        assertEquals(List.of(Chat.ok("The custom blocked word is removed.")), run("removeword zorblax two"));
        assertEquals(List.of(Chat.error("The custom list does not contain this word.")), run("removeword nothing"));
        assertEquals(List.of(Chat.warn("Unknown subcommand.")), run("bogus"));
        assertEquals(List.of(Chat.usage("/moderra addword <word or phrase>")), run("addword"));
        assertEquals(List.of(Chat.usage("/moderra check <text>")), run("check"));
        assertTrue(run("reload").get(0).matches(java.util.regex.Pattern.quote(Chat.ok("The configuration is reloaded. Blocked words: ")) + "§f\\d+§7\\."));

        UUID bob = f.platform.join("Bob");
        f.platform.join("Alice");
        engine.strikes().add(bob, 1);
        assertEquals(List.of(Chat.info("Violations of §fBob§7: §f1§7 of §f2§7")), run("violations Bob"));
        assertEquals(List.of(Chat.ok("The violations of §fBob§7 are reset.")), run("resetviolations Bob"));
        assertEquals(0, engine.strikes().get(bob));
        assertEquals(List.of(Chat.error("Player not found (not online and never seen on this server).")), run("violations Nobody"));
        assertEquals(List.of(Chat.usage("/moderra violations <player>")), run("violations"));

        List<String> info = run("info");
        assertEquals(Chat.PREFIX + "§6§lModerra 1", info.get(0));
        assertTrue(info.get(1).startsWith("§7Blocked words: §f"));
        assertEquals("§7Scanning: §fchat, commands, signs, books, anvil", info.get(2));
        assertTrue(run("list en").get(0).startsWith(Chat.PREFIX + "§6§lEN words"));
        assertTrue(run("listwords english").get(0).startsWith(Chat.PREFIX + "§6§lENGLISH words"), "old language names still work");
        assertTrue(run("listwords").get(0).startsWith(Chat.PREFIX + "§6§lACTIVE words"));
        assertEquals(List.of(Chat.error("No words exist for this list: §fnope§7.")), run("listwords nope"));

        assertEquals(List.of("reload", "removeword", "resetviolations"), complete("re"));
        assertEquals(List.of("listwords", "lang", "ladder"), complete("l"));
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
        assertEquals(Chat.warn("The plugin found §f1§7 configuration problem(s). The console shows the full list:"), out.get(1));
        assertTrue(out.get(2).startsWith("§c- §7config.yml -> filters.protection-level: 'banana' is not valid"), out.get(2));
    }

    @Test
    void langListEnableDisable() {
        List<String> list = run("lang list");
        assertEquals(Chat.PREFIX + "§6§lLanguages§7 (§f2§7 of §f38§7 on; green = on):", list.get(0));
        assertTrue(list.get(1).contains("§aen") && list.get(1).contains("§7de") && list.get(1).contains("§acustom"), list.get(1));
        assertEquals(list, run("lang"));
        assertNull(engine.find("arschloch"));

        String before = TestFiles.read(f.file("config.yml"));
        List<String> out = run("lang enable DE");
        assertTrue(out.get(0).matches(java.util.regex.Pattern.quote(Chat.ok("The language §fde§7 (German) is on. Blocked words: ")) + "§f\\d+§7\\."), out.toString());
        assertEquals("arschloch", engine.find("arschloch"), "works at once");
        assertEquals(before.replace("    - en\n    - custom\n", "    - en\n    - custom\n    - de\n"), TestFiles.read(f.file("config.yml")),
            "only one line of config.yml changed; every comment is still there");
        assertEquals(List.of("en", "de", "custom"), engine.settings().enabledLanguages());
        assertEquals(List.of(Chat.warn("The language §fde§7 is already on.")), run("lang enable german"));
        assertEquals(List.of(Chat.error("This language is not known: §fklingon§7. Use /moderra lang list.")), run("lang enable klingon"));
        assertEquals(List.of(Chat.warn("The custom list is always on.")), run("lang disable custom"));
        assertEquals(List.of(Chat.usage("/moderra lang enable <language code>")), run("lang enable"));
        assertEquals(List.of(Chat.usage("/moderra lang <list|enable|disable> [language code]")), run("lang dance"));
        assertEquals(Chat.PREFIX + "§6§lLanguages§7 (§f3§7 of §f38§7 on; green = on):", run("lang list").get(0));

        assertEquals(List.of("list", "enable", "disable"), complete("lang "));
        assertEquals(List.of("de"), complete("lang disable d"));
        assertEquals(List.of("da"), complete("lang enable d"));
        assertFalse(complete("lang enable ").contains("custom"));

        assertTrue(run("lang disable de").get(0).startsWith(Chat.ok("The language §fde§7 is off.")));
        assertNull(engine.find("arschloch"));
        assertEquals(before, TestFiles.read(f.file("config.yml")));
        assertEquals(List.of(Chat.warn("The language §fde§7 is not on.")), run("lang disable de"));
        assertTrue(new Fixture(dir).start().settings().enabledLanguages().equals(List.of("en", "custom")), "saved for the next start");

        // an old name written by hand is switched off under its new code
        TestFiles.edit(f.file("config.yml"), "    - en\n", "    - English\n    - german\n");
        engine.reload();
        assertTrue(run("lang disable de").get(0).startsWith(Chat.ok("The language §fde§7 is off.")));
        assertEquals(List.of("English", "custom"), YamlTree.parse(TestFiles.read(f.file("config.yml"))).getStringList("filters.enabled-word-lists"));
    }

    @Test
    void whitelistAddRemoveListWithPages() {
        TestFiles.edit(f.file("config.yml"), "partial-match: false", "partial-match: true");
        engine.reload();
        assertEquals("shit", engine.find("mishits"));
        String before = TestFiles.read(f.file("config.yml"));

        assertEquals(List.of(Chat.ok("The word is added to the whitelist: §fmishits§7.")), run("whitelist add MisHits"));
        assertNull(engine.find("some Mishits happen"), "applies at once, whatever the capital letters");
        assertEquals(before.replace("    - \"我日記\"\n", "    - \"我日記\"\n    - mishits\n"), TestFiles.read(f.file("config.yml")));
        assertEquals(List.of(Chat.error("This word is already on the whitelist: §fmishits§7.")), run("whitelist add MISHITS"));
        assertEquals(List.of(Chat.ok("The word is added to the whitelist: §ftwo words§7.")), run("whitelist add Two   Words"));
        assertEquals(List.of(Chat.error("That is not a valid word (use at least 2 letters or digits).")), run("whitelist add !"));
        assertEquals(List.of(Chat.usage("/moderra whitelist add <word or phrase>")), run("whitelist add"));
        assertEquals(List.of(Chat.usage("/moderra whitelist <add|remove|list> [word or page]")), run("whitelist"));

        // 43 words (v1 list, 5 added in task 25, Han entries), 10 per page (commands.list-page-size)
        List<String> page1 = run("whitelist list");
        assertEquals(Chat.PREFIX + "§6§lWhitelist§7 (§f43§7 words), page §f1§7 of §f5§7:", page1.get(0));
        assertEquals("§7class, classic, assassin, assume, assist, bass, glass, grass, pass, mass", page1.get(1));
        assertEquals("§7§oNext page: /moderra whitelist list 2", page1.get(2));
        List<String> page3 = run("whitelist list 5");
        assertEquals(List.of(Chat.PREFIX + "§6§lWhitelist§7 (§f43§7 words), page §f5§7 of §f5§7:", "§7我日記, mishits, two words"), page3);
        assertEquals(page3, run("whitelist list 99"));
        assertEquals(page1, run("whitelist list 0"));
        assertEquals(List.of(Chat.usage("/moderra whitelist list [page number]")), run("whitelist list abc"));
        TestFiles.edit(f.file("config.yml"), "list-page-size: 10", "list-page-size: 50");
        engine.reload();
        assertEquals(2, run("whitelist list").size(), "the page size is a setting");

        assertEquals(List.of("add", "remove", "list"), complete("whitelist "));
        assertEquals(List.of("mishits", "mass"), complete("whitelist remove m").stream().sorted((a, b) -> b.compareTo(a)).toList());
        assertEquals(List.of(Chat.ok("The word is removed from the whitelist: §fmishits§7.")), run("whitelist remove MISHITS"));
        assertEquals("shit", engine.find("mishits"));
        assertEquals(List.of(Chat.error("This word is not on the whitelist: §fmishits§7.")), run("whitelist remove mishits"));
        assertEquals(List.of(Chat.ok("The word is removed from the whitelist: §ftwo words§7.")), run("whitelist remove two words"));
        assertEquals(before, TestFiles.read(f.file("config.yml")).replace("list-page-size: 50", "list-page-size: 10"));
        assertFalse(engine.value(CoreSettings.WHITELIST).contains("mishits"));

        TestFiles.edit(f.file("config.yml"), "  whitelist:\n", "  whitelist: []\n  old-whitelist:\n");
        engine.reload();
        assertEquals(List.of(Chat.warn("The whitelist is empty.")), run("whitelist list"));
        assertEquals(List.of(Chat.ok("The word is added to the whitelist: §ffirst§7.")), run("whitelist add first"));
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
        assertEquals(List.of("Admin: moderra addword zorblax", "Admin: moderra whitelist add okword", "Admin: moderra lang enable de", "Admin: moderra reload"), seen);
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
            return "moderra.hello";
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
        Sender greeter = new Sender("moderra.hello");
        assertEquals(List.of(Chat.usage("/moderra <hello>")), run(greeter, ""), "the usage line only shows what the sender may run");
        assertEquals(List.of("Hello big world from Admin"), run(greeter, "hi big world"));
        assertEquals(List.of(Chat.usage("/moderra hello <name>")), run(greeter, "hello"));
        assertEquals(List.of(Chat.error("No permission.")), run(greeter, "reload"));
        assertEquals(List.of(Chat.error("No permission.")), run(admin, "hello x"), "its own permission, not the admin one");
        assertEquals(List.of("hello"), engine.commands().complete(greeter, new String[] {""}));
        assertEquals(List.of("world"), engine.commands().complete(greeter, new String[] {"hello", ""}));
        assertEquals(List.of(Chat.error("The command failed: kaput")), run(greeter, "hello boom"));
        assertTrue(engine.commands().names().contains("hello"));
    }
}
