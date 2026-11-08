package com.jason.plugins.antibadwords.mod;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLogicTest {
    @TempDir
    Path dir;

    static final class Fake implements CommandLogic.Actor {
        final boolean admin;
        final List<String> out = new ArrayList<>();
        final CommandLogic.Player bob = new CommandLogic.Player(UUID.randomUUID(), "Bob");

        Fake(boolean admin) {
            this.admin = admin;
        }

        public boolean isAdmin() {
            return admin;
        }

        public void send(String m) {
            out.add(m);
        }

        public CommandLogic.Player findOnlinePlayer(String n) {
            return n.equals("Bob") ? bob : null;
        }

        public List<String> onlinePlayerNames() {
            return List.of("Bob", "Alice");
        }
    }

    CommandLogic logic(ModCore[] holder) {
        ModCore core = new ModCore(dir, "1", new ModCore.Log() {
            public void info(String m) {
            }

            public void warn(String m) {
            }
        });
        holder[0] = core;
        return new CommandLogic(core);
    }

    @Test
    void nonAdminGetsNoPermissionEverywhere() {
        CommandLogic logic = logic(new ModCore[1]);
        Fake user = new Fake(false);
        logic.execute(user, "abw", new String[0]);
        logic.execute(user, "abw", new String[] {"listwords"});
        logic.execute(user, "abw", new String[] {"reload"});
        assertEquals(List.of("§cNo permission.", "§cNo permission.", "§cNo permission."), user.out);
        assertEquals(List.of(), logic.complete(user, new String[] {""}));
    }

    @Test
    void adminSubcommands() {
        ModCore[] h = new ModCore[1];
        CommandLogic logic = logic(h);
        Fake admin = new Fake(true);
        logic.execute(admin, "abw", new String[0]);
        assertEquals("§eUsage: /abw <reload|addword|removeword|listwords|check|violations|resetviolations|info>", admin.out.get(0));
        admin.out.clear();

        logic.execute(admin, "abw", new String[] {"check", "what", "the", "fuck"});
        logic.execute(admin, "abw", new String[] {"check", "hello"});
        logic.execute(admin, "abw", new String[] {"addword", "zorblax", "two"});
        logic.execute(admin, "abw", new String[] {"addword", "zorblax", "two"});
        logic.execute(admin, "abw", new String[] {"addword", "x"});
        logic.execute(admin, "abw", new String[] {"removeword", "zorblax", "two"});
        logic.execute(admin, "abw", new String[] {"removeword", "nothing"});
        logic.execute(admin, "abw", new String[] {"bogus"});
        assertEquals(List.of("§cBlocked: matches 'fuck'.", "§aClean: no blocked word found.", "§aAdded custom blocked word.",
            "§cWord already exists.", "§cThat is not a valid word (use at least 2 letters or digits).",
            "§aRemoved custom blocked word.", "§cWord not found in the custom list.", "§eUnknown subcommand."), admin.out);

        admin.out.clear();
        h[0].addViolation(admin.bob.id());
        logic.execute(admin, "abw", new String[] {"violations", "Bob"});
        logic.execute(admin, "abw", new String[] {"resetviolations", "Bob"});
        logic.execute(admin, "abw", new String[] {"violations", "Nobody"});
        logic.execute(admin, "abw", new String[] {"violations"});
        assertEquals(List.of("§eBob violations: 1/2", "§aViolations reset for Bob.", "§cPlayer not online.",
            "§eUsage: /abw violations <player>"), admin.out);
    }

    @Test
    void infoListAliasAndCompletion() {
        CommandLogic logic = logic(new ModCore[1]);
        Fake admin = new Fake(true);
        logic.execute(admin, "abw", new String[] {"info"});
        assertEquals("§6AntiBadWords 1", admin.out.get(0));
        assertEquals("§7Scanning: §fchat, commands, signs, books, anvil", admin.out.get(2));
        admin.out.clear();
        logic.execute(admin, "abw", new String[] {"list", "english"});
        assertTrue(admin.out.get(0).startsWith("§6ENGLISH words ("));
        assertEquals(List.of("reload", "removeword", "resetviolations"), logic.complete(admin, new String[] {"re"}));
        assertTrue(logic.complete(admin, new String[] {"listwords", "g"}).contains("german"));
        assertEquals(List.of("Alice"), logic.complete(admin, new String[] {"violations", "al"}));
    }
}
