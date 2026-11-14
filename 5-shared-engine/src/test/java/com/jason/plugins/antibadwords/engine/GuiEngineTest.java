package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
import com.jason.plugins.antibadwords.engine.gui.ClickType;
import com.jason.plugins.antibadwords.engine.gui.Menu;
import com.jason.plugins.antibadwords.engine.gui.MenuAction;
import com.jason.plugins.antibadwords.engine.gui.MenuState;
import com.jason.plugins.antibadwords.engine.gui.Slot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The menu on a real engine: every category gets a page, Done writes the file (comments stay) and reloads. */
class GuiEngineTest {
    @TempDir
    Path dir;

    private static final CommandSender ADMIN = new CommandSender() {
        public String name() { return "Admin"; }
        public UUID id() { return UUID.randomUUID(); }
        public boolean has(String p) { return p.equals(Menu.PERMISSION) || p.startsWith(Menu.EDIT_PREFIX); }
        public void send(String m) { }
    };

    @Test
    @SuppressWarnings("unchecked")
    void everyCategoryOpensAndDoneWritesTheFile() throws Exception {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        MenuState state = Menu.open(engine);
        List<String> categories = engine.registry().categories().stream()
            .filter(c -> engine.registry().inCategory(c).stream().anyMatch(e -> e instanceof Setting<?>)).toList();
        List<Slot> main = Menu.render(state);
        assertEquals(categories, main.stream().filter(s -> s.index() < 45 && s.icon().equals("category")).map(Slot::name).toList());

        // every category opens and all its pages render
        for (int i = 0; i < categories.size(); i++) {
            assertEquals(MenuAction.Kind.REFRESH, Menu.click(state, i, ClickType.LEFT, ADMIN).kind());
            for (int page = 0; page < 20 && Menu.render(state).stream().anyMatch(s -> s.index() == 52); page++) {
                Menu.click(state, 52, ClickType.LEFT, ADMIN);
            }
            Menu.click(state, 53, ClickType.LEFT, ADMIN);
        }

        Setting<Boolean> target = null;
        int categoryIndex = -1;
        for (int i = 0; i < categories.size() && target == null; i++) {
            for (var e : engine.registry().inCategory(categories.get(i))) {
                if (e instanceof Setting<?> s && s.type() == SettingType.BOOLEAN) {
                    target = (Setting<Boolean>) s;
                    categoryIndex = i;
                    break;
                }
            }
        }
        boolean before = engine.value(target);
        Menu.click(state, categoryIndex, ClickType.LEFT, ADMIN);
        List<Object> inCategory = engine.registry().inCategory(categories.get(categoryIndex)).stream()
            .filter(e -> e instanceof Setting<?>).map(e -> (Object) e).toList();
        int slot = inCategory.indexOf(target);
        assertTrue(slot >= 0 && slot < 45, "the boolean is on the first page");
        Menu.click(state, slot, ClickType.LEFT, ADMIN);
        assertEquals(before, engine.value(target), "nothing written before Done");
        String file = Files.readString(dir.resolve(target.file()));
        assertEquals(MenuAction.Kind.SAVED, Menu.click(state, 50, ClickType.LEFT, ADMIN).kind());
        assertEquals(!before, engine.value(target));
        String after = Files.readString(dir.resolve(target.file()));
        assertFalse(file.equals(after));
        assertTrue(after.contains("#"), "comments are kept");
    }
}
