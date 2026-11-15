package com.jason.plugins.moderra.engine.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The menu model driven by simulated click sequences (no engine, no Minecraft). */
class MenuTest {
    static final class Viewer implements CommandSender {
        boolean allowed = true;
        final java.util.Set<String> noEdit = new java.util.HashSet<>();

        @Override
        public String name() {
            return "Alex";
        }

        @Override
        public UUID id() {
            return UUID.nameUUIDFromBytes("alex".getBytes());
        }

        @Override
        public boolean has(String permission) {
            return allowed && (permission.equals(Menu.PERMISSION) || (permission.startsWith(Menu.EDIT_PREFIX) && !noEdit.contains(permission)));
        }

        @Override
        public void send(String message) {
        }
    }

    static final class FakeAccess implements ConfigAccess {
        final SettingsRegistry registry = new SettingsRegistry();
        final Map<String, Object> values = new HashMap<>();
        int saves;
        boolean failSave;
        Map<Setting<?>, Object> lastSave;

        @Override
        public SettingsRegistry registry() {
            return registry;
        }

        @Override
        public Object current(Setting<?> s) {
            return values.getOrDefault(s.key(), s.defaultValue());
        }

        @Override
        public void save(Map<Setting<?>, Object> changes) throws IOException {
            if (failSave) {
                throw new IOException("disk full");
            }
            saves++;
            lastSave = new LinkedHashMap<>(changes);
            changes.forEach((k, v) -> values.put(k.key(), v));
        }
    }

    FakeAccess access;
    MenuState state;
    Viewer viewer;

    // main menu slot of "Alpha" is 0, "Big" is 1
    @BeforeEach
    void setUp() {
        access = new FakeAccess();
        SettingsRegistry r = access.registry;
        Setting.bool("a.on", false).category("Alpha").title("Switch").description("Turns it on.").register(r);
        Setting.integer("a.count", 5).range(1, 10).category("Alpha").title("Count").register(r);
        Setting.decimal("a.ratio", 0.5).range(0, 1).category("Alpha").title("Ratio").register(r);
        Setting.duration("a.wait", Duration.ofSeconds(10)).range(1, 100).category("Alpha").title("Wait").register(r);
        Setting.choice("a.mode", "block", "block", "censor", "log-only").category("Alpha").title("Mode").register(r);
        Setting.string("a.name", "Bob").category("Alpha").title("Name").register(r);
        Setting.secret("a.token").category("Alpha").title("Token").register(r);
        Setting.list("a.words", List.of("one", "two")).category("Alpha").title("Words").register(r);
        for (int i = 0; i < 46; i++) {
            Setting.bool(String.format("b.s%02d", i), false).category("Big").title("S" + i).register(r);
        }
        state = Menu.open(access);
        viewer = new Viewer();
        click(0, ClickType.LEFT); // open Alpha: slots 0..7 in registration order
    }

    private MenuAction click(int slot, ClickType type) {
        return Menu.click(state, slot, type, viewer);
    }

    private Slot slot(int index) {
        return Menu.render(state).stream().filter(s -> s.index() == index).findFirst().orElse(null);
    }

    private static final int ON = 0, COUNT = 1, RATIO = 2, WAIT = 3, MODE = 4, NAME = 5, TOKEN = 6, WORDS = 7;

    private Object value(String key) {
        return state.value(access.registry.get(key));
    }

    @Test
    void mainMenuHasAButtonPerCategory() {
        MenuState main = Menu.open(access);
        List<Slot> slots = Menu.render(main);
        assertEquals("category", slots.get(0).icon());
        assertEquals("Alpha", slots.get(0).name());
        assertEquals("Big", slots.get(1).name());
        assertEquals(2, slots.stream().filter(s -> s.icon().equals(Icons.CATEGORY)).count());
        assertEquals("Moderra", Menu.title(main));
    }

    @Test
    void toggleStagesAndNothingIsWritten() {
        assertEquals(Icons.TOGGLE_OFF, slot(ON).icon());
        assertEquals(MenuAction.Kind.REFRESH, click(ON, ClickType.LEFT).kind());
        assertEquals(Icons.TOGGLE_ON, slot(ON).icon());
        assertTrue(slot(ON).glowing());
        assertTrue(state.hasUnsavedChanges());
        assertEquals(0, access.saves);
        assertEquals(false, access.current(access.registry.get("a.on")));
        click(ON, ClickType.RIGHT);
        assertFalse(state.hasUnsavedChanges(), "toggling back removes the staged change");
    }

    @Test
    void stepButtonsAndShiftAndClamp() {
        click(COUNT, ClickType.LEFT);
        assertEquals(6, value("a.count"));
        click(COUNT, ClickType.RIGHT);
        click(COUNT, ClickType.RIGHT);
        assertEquals(4, value("a.count"));
        click(COUNT, ClickType.SHIFT_LEFT);
        assertEquals(10, value("a.count"), "x10 is clamped to max");
        assertEquals(MenuAction.Kind.NONE, click(COUNT, ClickType.LEFT).kind(), "already at max");
        click(COUNT, ClickType.SHIFT_RIGHT);
        assertEquals(1, value("a.count"), "clamped to min");
        click(RATIO, ClickType.LEFT);
        assertEquals(0.51, (double) value("a.ratio"), 1e-9);
        click(RATIO, ClickType.SHIFT_LEFT);
        click(RATIO, ClickType.SHIFT_LEFT);
        click(RATIO, ClickType.SHIFT_LEFT);
        click(RATIO, ClickType.SHIFT_LEFT);
        click(RATIO, ClickType.SHIFT_LEFT);
        assertEquals(1.0, (double) value("a.ratio"), 1e-9);
        click(WAIT, ClickType.LEFT);
        assertEquals(Duration.ofSeconds(11), value("a.wait"));
        click(WAIT, ClickType.SHIFT_RIGHT);
        click(WAIT, ClickType.SHIFT_RIGHT);
        assertEquals(Duration.ofSeconds(1), value("a.wait"), "duration min is 1s");
    }

    @Test
    void cycleGoesBothWaysAndWraps() {
        click(MODE, ClickType.LEFT);
        assertEquals("censor", value("a.mode"));
        click(MODE, ClickType.LEFT);
        click(MODE, ClickType.LEFT);
        assertEquals("block", value("a.mode"), "wrapped around");
        click(MODE, ClickType.RIGHT);
        assertEquals("log-only", value("a.mode"));
    }

    @Test
    void resetToDefault() {
        click(COUNT, ClickType.SHIFT_LEFT);
        assertTrue(state.hasUnsavedChanges());
        click(COUNT, ClickType.MIDDLE);
        assertEquals(5, value("a.count"));
        assertFalse(state.hasUnsavedChanges());
        click(MODE, ClickType.LEFT);
        click(MODE, ClickType.DROP);
        assertFalse(state.hasUnsavedChanges());
    }

    @Test
    void stringInputRequestAndValidation() {
        MenuAction ask = click(NAME, ClickType.LEFT);
        assertEquals(MenuAction.Kind.TEXT_INPUT, ask.kind());
        assertEquals("a.name", ask.request().key());
        assertFalse(ask.request().masked());
        assertEquals(MenuAction.Kind.REFRESH, Menu.submitText(state, "Carol", viewer).kind());
        assertEquals("Carol", value("a.name"));
        assertNull(state.pendingText());
        // cancelled prompt changes nothing
        click(NAME, ClickType.LEFT);
        Menu.cancelText(state, viewer);
        assertEquals("Carol", value("a.name"));
        assertNull(state.pendingText());
    }

    @Test
    void secretIsAlwaysMasked() {
        MenuAction ask = click(TOKEN, ClickType.LEFT);
        assertTrue(ask.request().masked());
        assertFalse(ask.request().prompt().contains("hunter2"));
        assertEquals(MenuAction.Kind.ERROR, Menu.submitText(state, "   ", viewer).kind(), "empty secret refused");
        assertEquals(MenuAction.Kind.REFRESH, Menu.submitText(state, "hunter2", viewer).kind());
        for (Slot s : Menu.render(state)) {
            assertFalse(s.name().contains("hunter2"));
            assertFalse(String.join("|", s.lore()).contains("hunter2"), s.lore().toString());
        }
        assertTrue(String.join("|", slot(TOKEN).lore()).contains("********"));
        assertTrue(String.join("|", slot(TOKEN).lore()).contains("value hidden"));
        click(TOKEN, ClickType.MIDDLE);
        assertEquals("", value("a.token"));
        assertTrue(String.join("|", slot(TOKEN).lore()).contains("(not set)"));
    }

    @Test
    void listEditAddRemovePaginated() {
        click(WORDS, ClickType.LEFT);
        assertEquals("List: Words", Menu.title(state));
        assertEquals("one", slot(0).name());
        assertEquals("two", slot(1).name());
        click(0, ClickType.LEFT); // remove "one"
        assertEquals(List.of("two"), value("a.words"));
        MenuAction ask = click(Page.ADD, ClickType.LEFT);
        assertEquals(TextRequest.Purpose.ADD_ITEM, ask.request().purpose());
        assertEquals(MenuAction.Kind.ERROR, Menu.submitText(state, "  ", viewer).kind());
        Menu.submitText(state, " three ", viewer);
        assertEquals(List.of("two", "three"), value("a.words"));
        assertEquals(0, access.saves);
        click(Page.RESET, ClickType.LEFT);
        assertEquals(List.of("one", "two"), value("a.words"));
        assertFalse(state.hasUnsavedChanges());
        // 46 items: two pages, remove from page two shrinks back to one page
        for (int i = 0; i < 44; i++) {
            click(Page.ADD, ClickType.LEFT);
            Menu.submitText(state, "w" + i, viewer);
        }
        assertEquals(46, ((List<?>) value("a.words")).size());
        assertNull(slot(Page.PREV));
        assertEquals(MenuAction.Kind.REFRESH, click(Page.NEXT, ClickType.LEFT).kind());
        assertEquals("w43", slot(0).name());
        assertEquals(1, Menu.render(state).stream().filter(s -> s.index() < 45).count());
        click(0, ClickType.LEFT);
        assertNull(slot(Page.NEXT), "clamped back to the only page once page 2 is empty");
        assertEquals(0, state.current().page());
        click(Page.BACK, ClickType.LEFT);
        assertEquals("Moderra: Alpha", Menu.title(state).replace(" *", ""));
    }

    @Test
    void doneSavesEverythingOnceAndCancelDiscards() {
        click(ON, ClickType.LEFT);
        click(COUNT, ClickType.LEFT);
        assertTrue(Menu.title(state).endsWith("*"));
        MenuAction done = click(Page.DONE, ClickType.LEFT);
        assertEquals(MenuAction.Kind.SAVED, done.kind());
        assertEquals(1, access.saves);
        assertEquals(2, access.lastSave.size());
        assertEquals(true, access.values.get("a.on"));
        assertEquals(6, access.values.get("a.count"));
        assertFalse(state.hasUnsavedChanges());

        click(ON, ClickType.LEFT);
        MenuAction cancel = click(Page.CANCEL, ClickType.LEFT);
        assertEquals(MenuAction.Kind.CLOSE, cancel.kind());
        assertFalse(state.hasUnsavedChanges());
        assertEquals(1, access.saves);
        assertEquals(true, access.values.get("a.on"));
    }

    @Test
    void doneWithNothingStagedJustCloses() {
        assertEquals(MenuAction.Kind.CLOSE, click(Page.DONE, ClickType.LEFT).kind());
        assertEquals(0, access.saves);
    }

    @Test
    void failedSaveKeepsTheChanges() {
        click(ON, ClickType.LEFT);
        access.failSave = true;
        MenuAction done = click(Page.DONE, ClickType.LEFT);
        assertEquals(MenuAction.Kind.ERROR, done.kind());
        assertTrue(done.message().contains("disk full"));
        assertTrue(state.hasUnsavedChanges());
    }

    @Test
    void closingWithUnsavedChangesAsksFirst() {
        click(ON, ClickType.LEFT);
        assertEquals(MenuAction.Kind.REFRESH, Menu.closeRequested(state, viewer).kind());
        assertEquals("Unsaved changes", Menu.title(state));
        // keep editing
        click(Page.BACK, ClickType.LEFT);
        assertEquals("Moderra: Alpha *", Menu.title(state));
        Menu.closeRequested(state, viewer);
        MenuAction discard = click(24, ClickType.LEFT);
        assertEquals(MenuAction.Kind.CLOSE, discard.kind());
        assertEquals(0, access.saves);
        // save from the confirm page (a closed menu is dropped; open a fresh one)
        state = Menu.open(access);
        click(0, ClickType.LEFT);
        click(ON, ClickType.LEFT);
        Menu.closeRequested(state, viewer);
        assertEquals(MenuAction.Kind.SAVED, click(20, ClickType.LEFT).kind());
        assertEquals(1, access.saves);
        // clean close
        assertEquals(MenuAction.Kind.CLOSE, Menu.closeRequested(state, viewer).kind());
    }

    @Test
    void paginationEdges() {
        click(Page.BACK, ClickType.LEFT);
        click(1, ClickType.LEFT); // Big: 46 entries
        assertEquals("Moderra: Big", Menu.title(state));
        assertEquals(45, Menu.render(state).stream().filter(s -> s.index() < 45).count());
        assertNull(slot(Page.PREV), "no previous page on the first page");
        assertEquals("Page 1 of 2", slot(Page.INFO).name());
        assertEquals(MenuAction.Kind.NONE, click(Page.PREV, ClickType.LEFT).kind());
        click(Page.NEXT, ClickType.LEFT);
        assertEquals("S45", slot(0).name());
        assertNull(slot(1));
        assertNull(slot(Page.NEXT), "no next page on the last page");
        assertEquals("Page 2 of 2", slot(Page.INFO).name());
        click(0, ClickType.LEFT); // toggle on the second page
        assertEquals(Icons.TOGGLE_ON, slot(0).icon());
        click(Page.PREV, ClickType.LEFT);
        assertEquals("S0", slot(0).name());
        click(Page.HOME, ClickType.LEFT);
        assertEquals("Moderra", Menu.title(state).replace(" *", ""));
        assertNull(slot(Page.BACK), "main menu has no back button");
        // an empty slot and a click type that does nothing
        assertEquals(MenuAction.Kind.NONE, click(30, ClickType.LEFT).kind());
        click(Page.BACK, ClickType.LEFT);
        assertEquals(MenuAction.Kind.NONE, click(0, ClickType.OTHER).kind());
    }

    @Test
    void exactly45EntriesIsOnePage() {
        FakeAccess a = new FakeAccess();
        for (int i = 0; i < 45; i++) {
            Setting.bool("c.s" + i, false).category("C").register(a.registry);
        }
        MenuState s = Menu.open(a);
        Menu.click(s, 0, ClickType.LEFT, viewer);
        assertNull(Menu.render(s).stream().filter(x -> x.index() == Page.NEXT).findFirst().orElse(null));
        assertEquals("Page 1 of 1", Menu.render(s).stream().filter(x -> x.index() == Page.INFO).findFirst().get().name());
    }

    @Test
    void permissionIsCheckedOnEveryClick() {
        click(ON, ClickType.LEFT);
        viewer.allowed = false;
        MenuAction denied = click(ON, ClickType.LEFT);
        assertEquals(MenuAction.Kind.DENIED, denied.kind());
        assertTrue(denied.closesMenu());
        assertFalse(state.hasUnsavedChanges(), "a denied viewer loses the staged changes");
        assertEquals(0, access.saves);
        viewer.allowed = true;
        click(ON, ClickType.LEFT);
        viewer.allowed = false;
        assertEquals(MenuAction.Kind.DENIED, click(Page.DONE, ClickType.LEFT).kind());
        assertEquals(0, access.saves);
        assertEquals(MenuAction.Kind.DENIED, Menu.submitText(state, "x", viewer).kind());
        assertEquals(MenuAction.Kind.DENIED, Menu.closeRequested(state, viewer).kind());
        assertEquals(MenuAction.Kind.DENIED, Menu.click(state, 0, ClickType.LEFT, null).kind(), "no viewer fails closed");
    }

    @Test
    void permissionLostDuringATextPromptBlocksTheAnswer() {
        click(NAME, ClickType.LEFT);
        viewer.allowed = false;
        assertEquals(MenuAction.Kind.DENIED, Menu.submitText(state, "Eve", viewer).kind());
        viewer.allowed = true;
        assertEquals("Bob", value("a.name"));
    }
}
