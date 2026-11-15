package com.jason.plugins.moderra.engine;

import com.jason.plugins.moderra.engine.command.Chat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.command.CommandSender;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code /moderra} and {@code /moderra gui}: who gets the menu, and that nothing else changes. */
class GuiCommandTest {
    @TempDir
    Path dir;

    static final class Who implements CommandSender {
        final UUID id;
        final Set<String> nodes;
        final List<String> out = new ArrayList<>();

        Who(UUID id, String... nodes) {
            this.id = id;
            this.nodes = Set.of(nodes);
        }

        @Override
        public String name() {
            return "Who";
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean has(String permission) {
            return id == null || nodes.contains(permission);
        }

        @Override
        public void send(String message) {
            out.add(message);
        }
    }

    private static void run(Engine engine, Who who, String... args) {
        engine.commands().execute(who, "moderra", args);
    }

    @Test
    void permittedPlayerOpensMenuWithAndWithoutGui() {
        Fixture f = new Fixture(dir);
        f.platform.menus = true;
        Engine engine = f.start();
        UUID id = UUID.randomUUID();
        Who player = new Who(id, "moderra.gui");
        run(engine, player);
        run(engine, player, "gui");
        run(engine, player, "GUI");
        assertEquals(List.of(id, id, id), f.platform.menusOpened);
        assertTrue(player.out.isEmpty());
    }

    @Test
    void consoleAndUnpermittedKeepTodaysBehaviour() {
        Fixture f = new Fixture(dir);
        f.platform.menus = true;
        Engine engine = f.start();
        Who console = new Who(null);
        run(engine, console);
        assertTrue(console.out.get(0).startsWith(Chat.usage("/moderra <reload|")));
        run(engine, console, "gui");
        assertEquals(Chat.error("The menu can only be opened by a player."), console.out.get(1));
        Who admin = new Who(UUID.randomUUID(), "moderra.admin");
        run(engine, admin);
        assertTrue(admin.out.get(0).startsWith(Chat.usage("/moderra <reload|")));
        run(engine, admin, "gui");
        assertEquals(Chat.error("No permission."), admin.out.get(1));
        assertEquals(List.of(), f.platform.menusOpened);
    }

    @Test
    void platformWithoutMenuSaysSo() {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        Who player = new Who(UUID.randomUUID(), "moderra.gui");
        run(engine, player, "gui");
        assertTrue(player.out.get(0).contains("not available"));
        run(engine, player);
        assertTrue(player.out.get(1).startsWith(Chat.usage("/moderra <gui")));
    }
}
