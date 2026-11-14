package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.gui.ClickType;
import com.jason.plugins.antibadwords.engine.gui.EngineConfigAccess;
import com.jason.plugins.antibadwords.engine.gui.Menu;
import com.jason.plugins.antibadwords.engine.gui.MenuAction;
import com.jason.plugins.antibadwords.engine.gui.MenuState;
import com.jason.plugins.antibadwords.engine.gui.Page;
import com.jason.plugins.antibadwords.engine.gui.Slot;
import com.jason.plugins.antibadwords.engine.gui.TextRequest;
import com.jason.plugins.antibadwords.engine.moderation.Ladder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ladder editor, the world-profile editor, per-category edit rights and the {@code /abw gui <page>} argument. */
class GuiEditorTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    MenuState state;
    final Set<String> denied = new HashSet<>();

    final CommandSender admin = new CommandSender() {
        public String name() { return "Admin"; }
        public UUID id() { return UUID.nameUUIDFromBytes("admin".getBytes()); }
        public boolean has(String p) { return !denied.contains(p) && (p.equals(Menu.PERMISSION) || p.startsWith(Menu.EDIT_PREFIX)); }
        public void send(String m) { }
    };

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.platform.menus = true;
        engine = f.start();
        state = Menu.open(engine);
    }

    private MenuAction click(int slot, ClickType type) {
        return Menu.click(state, slot, type, admin);
    }

    private Slot slotNamed(String prefix) {
        return Menu.render(state).stream().filter(s -> s.name().startsWith(prefix)).findFirst().orElse(null);
    }

    private MenuAction clickNamed(String prefix, ClickType type) {
        Slot s = slotNamed(prefix);
        assertNotNull(s, prefix + " in " + Menu.render(state).stream().map(Slot::name).toList());
        return click(s.index(), type);
    }

    private void open(String name) {
        assertEquals(MenuAction.Kind.REFRESH, clickNamed(name, ClickType.LEFT).kind());
    }

    /** Clicks a slot that asks for text and answers it like the prompt does: validate, then submit. */
    private MenuAction ask(int slot, String answer) {
        MenuAction action = click(slot, ClickType.LEFT);
        assertEquals(MenuAction.Kind.TEXT_INPUT, action.kind(), String.valueOf(action.message()));
        TextRequest request = action.request();
        String problem = Menu.validateText(state, answer);
        if (problem != null) {
            return MenuAction.error(problem);
        }
        return Menu.submitText(state, answer, admin);
    }

    private MenuAction done() {
        return click(Page.DONE, ClickType.LEFT);
    }

    private Ladder reloadedLadder() {
        return engine.settings().ladder();
    }

    private void withLadder() {
        TestFiles.edit(f.file("moderation/ladder.yml"), "steps: {}", """
            steps:
                warn-first:
                  at: 2
                  repeat: once
                  message: "careful"
                  actions: [warn]
                big-mute:
                  at: 5
                  repeat: 3
                  actions:
                    - mute 10m No swearing
            """.stripTrailing());
        engine = f.start();
        state = Menu.open(engine);
    }

    // ---- ladder ---------------------------------------------------------------------------------------

    @Test
    void stepFieldsAreEditedStagedAndSavedWithTheFileCommentsKept() {
        withLadder();
        String before = TestFiles.read(f.file("moderation/ladder.yml"));
        assertTrue(before.contains("# "), "the file has comments");
        open("Punishment ladder");
        open("big-mute");
        assertEquals("Step: big-mute", Menu.title(state));

        click(slotNamed("At: 5").index(), ClickType.LEFT);
        click(slotNamed("At: 6").index(), ClickType.SHIFT_LEFT);
        assertNotNull(slotNamed("At: 16"), Menu.render(state).stream().map(Slot::name).toList().toString());
        click(slotNamed("Repeat: every 3").index(), ClickType.RIGHT);
        assertNotNull(slotNamed("Repeat: every 2"));
        click(slotNamed("Repeat: every 2").index(), ClickType.MIDDLE);
        assertNotNull(slotNamed("Repeat: once"));
        assertTrue(state.hasUnsavedChanges());
        assertEquals(5, engine.settings().ladder().steps().get(1).threshold(), "nothing written yet");

        // message, text with a colon and quotes
        assertEquals(MenuAction.Kind.REFRESH, ask(slotNamed("Message to the player").index(), "&cYou are muted: \"calm down\"").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(slotNamed("Broadcast").index(), "{player} was muted").kind());
        // actions: add, change, move, remove
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "sound minecraft:entity.villager.no 1 1").kind());
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "explode now").kind(), "unknown action is refused by the prompt");
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "mute").kind(), "mute needs a time");
        assertEquals(MenuAction.Kind.REFRESH, ask(slotNamed("1. mute").index(), "mute 1h Rude").kind());
        click(slotNamed("2. sound").index(), ClickType.SHIFT_LEFT);
        assertNotNull(slotNamed("1. sound"));
        assertNotNull(slotNamed("2. mute 1h Rude"));
        click(slotNamed("1. sound").index(), ClickType.SHIFT_RIGHT);
        assertNull(slotNamed("1. sound"));

        assertEquals(MenuAction.Kind.SAVED, done().kind());
        Ladder ladder = reloadedLadder();
        assertEquals(2, ladder.steps().size());
        Ladder.Step step = ladder.steps().stream().filter(s -> s.name().equals("big-mute")).findFirst().orElseThrow();
        assertEquals(16, step.threshold());
        assertFalse(step.repeats());
        assertEquals("&cYou are muted: \"calm down\"", step.message());
        assertEquals("{player} was muted", step.broadcast());
        assertEquals(List.of("mute 1h Rude"), step.actions().stream().map(Ladder.Action::text).toList());
        String after = TestFiles.read(f.file("moderation/ladder.yml"));
        assertTrue(after.contains("when-several-steps-are-reached"), "the rest of the file is still there");
        for (String line : before.split("\n")) {
            if (line.startsWith("#")) {
                assertTrue(after.contains(line), "comment kept: " + line);
            }
        }
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());
    }

    @Test
    void addAndDeleteSteps() {
        open("Punishment ladder");
        assertEquals("No ladder steps", Menu.render(state).get(0).name());
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "bad name!").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "first-warning").kind());
        assertEquals("Step: first-warning *", Menu.title(state), "the new step opens for editing");
        click(slotNamed("At: 1").index(), ClickType.LEFT);
        click(Page.BACK, ClickType.LEFT);
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "kick-step").kind());
        click(Page.BACK, ClickType.LEFT);
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "KICK-STEP").kind(), "names are unique");
        assertEquals(MenuAction.Kind.SAVED, done().kind());
        assertEquals(List.of("first-warning", "kick-step"), reloadedLadder().steps().stream().map(Ladder.Step::name).toList());
        assertEquals(2, reloadedLadder().steps().get(0).threshold());
        assertEquals(3, reloadedLadder().steps().get(1).threshold(), "a new step starts one above the highest");

        state = Menu.open(engine);
        open("Punishment ladder");
        assertEquals(MenuAction.Kind.REFRESH, clickNamed("kick-step", ClickType.SHIFT_RIGHT).kind());
        assertNull(slotNamed("kick-step"));
        assertEquals(MenuAction.Kind.SAVED, done().kind());
        assertEquals(1, reloadedLadder().steps().size());
        // cancel discards a staged deletion
        state = Menu.open(engine);
        open("Punishment ladder");
        clickNamed("first-warning", ClickType.SHIFT_RIGHT);
        assertEquals(MenuAction.Kind.CLOSE, click(Page.CANCEL, ClickType.LEFT).kind());
        assertEquals(1, reloadedLadder().steps().size());
    }

    @Test
    void aStepWithNothingLeftIsNotSaved() {
        TestFiles.edit(f.file("moderation/ladder.yml"), "steps: {}", "steps:\n    only:\n      at: 2\n      actions: [warn]");
        engine = f.start();
        state = Menu.open(engine);
        open("Punishment ladder");
        open("only");
        click(slotNamed("1. warn").index(), ClickType.MIDDLE);
        MenuAction result = done();
        assertEquals(MenuAction.Kind.ERROR, result.kind());
        assertTrue(result.message().contains("only"), result.message());
        assertTrue(state.hasUnsavedChanges(), "the changes are kept");
        assertEquals(1, reloadedLadder().steps().size());
    }

    // ---- world profiles and rules ----------------------------------------------------------------------

    @Test
    void profileEditorAddsOverridesAndSaves() {
        open("World profiles");
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "Not Valid").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "relaxed").kind());
        assertEquals("Profile: relaxed *", Menu.title(state));
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "nope.setting 1").kind(), "unknown key");
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "config-version 3").kind(), "not per world");
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "filters.protection-level 9").kind(), "out of range");
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "filters.protection-level 1").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "punishment.enabled false").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "filters.enabled-word-lists en, de").kind());
        // number step and boolean toggle
        click(slotNamed("filters.protection-level").index(), ClickType.RIGHT);
        click(slotNamed("filters.protection-level").index(), ClickType.LEFT);
        click(slotNamed("punishment.enabled").index(), ClickType.LEFT);
        assertEquals(MenuAction.Kind.SAVED, done().kind());

        Map<String, Object> overrides = engine.profileOverrides("relaxed");
        assertEquals(2, overrides.get("filters.protection-level"));
        assertEquals(true, overrides.get("punishment.enabled"));
        assertEquals(List.of("en", "de"), overrides.get("filters.enabled-word-lists"));
        assertTrue(engine.profileNames().contains("relaxed"));
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());
        assertTrue(engine.profile("relaxed").get(CoreSettings.PROTECTION_LEVEL) == 2);

        // remove one override, delete the profile
        state = Menu.open(engine);
        open("World profiles");
        open("relaxed");
        click(slotNamed("punishment.enabled").index(), ClickType.DROP);
        assertNull(slotNamed("punishment.enabled"));
        click(Page.BACK, ClickType.LEFT);
        assertEquals(MenuAction.Kind.REFRESH, clickNamed("relaxed", ClickType.SHIFT_RIGHT).kind());
        assertNull(slotNamed("relaxed"));
        assertEquals(MenuAction.Kind.ERROR, clickNamed("default", ClickType.SHIFT_RIGHT).kind(), "default cannot go");
        assertEquals(MenuAction.Kind.SAVED, done().kind());
        assertFalse(engine.profileNames().contains("relaxed"));
    }

    @Test
    void worldRulesAreAddedOrderedCycledAndRemoved() {
        open("World profiles");
        ask(Page.ADD, "relaxed");
        click(Page.HOME, ClickType.LEFT);
        open("World profiles");
        ask(Page.ADD, "strict");
        click(Page.HOME, ClickType.LEFT);
        open("World rules");
        assertEquals("World rules *", Menu.title(state), "the two new profiles are still unsaved");
        assertEquals(MenuAction.Kind.ERROR, ask(Page.ADD, "creative_* nosuchprofile").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "creative_* relaxed").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "minecraft:the_nether strict").kind());
        assertEquals(MenuAction.Kind.REFRESH, ask(Page.ADD, "my world strict").kind(), "a world name with a space");
        // order: the nether rule goes first
        clickNamed("minecraft:the_nether", ClickType.SHIFT_LEFT);
        assertEquals(List.of("minecraft:the_nether", "creative_*", "my world"), Menu.render(state).stream().filter(s -> s.index() < 45).map(Slot::name).toList());
        // cycle: default -> relaxed -> strict
        clickNamed("creative_*", ClickType.RIGHT);
        assertTrue(slotNamed("creative_*").lore().get(0).contains("default"));
        clickNamed("creative_*", ClickType.LEFT);
        clickNamed("creative_*", ClickType.LEFT);
        assertTrue(slotNamed("creative_*").lore().get(0).contains("strict"), slotNamed("creative_*").lore().toString());
        clickNamed("my world", ClickType.MIDDLE);
        assertNull(slotNamed("my world"));
        assertEquals(MenuAction.Kind.SAVED, done().kind());

        assertEquals("strict", engine.settings("creative_1", "", p -> false).profile());
        assertEquals("strict", engine.settings("world_x", "minecraft:the_nether", p -> false).profile());
        assertEquals("default", engine.settings("my world", "", p -> false).profile());
        // order survived the file round trip
        assertEquals("strict", engine.settings("creative_nether", "minecraft:the_nether", p -> false).profile());
        assertTrue(TestFiles.read(f.file("worlds.yml")).indexOf("the_nether") < TestFiles.read(f.file("worlds.yml")).indexOf("creative_"));
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());

        // a profile in use cannot be deleted
        state = Menu.open(engine);
        open("World profiles");
        assertEquals(MenuAction.Kind.ERROR, clickNamed("strict", ClickType.SHIFT_RIGHT).kind());
    }

    // ---- edit rights ------------------------------------------------------------------------------------

    @Test
    void editRightsAreCheckedPerCategoryOnEveryClick() {
        denied.add(Menu.editNode("Spam"));
        assertEquals("antibadwords.gui.edit.spam", Menu.editNode("Spam"));
        open("Spam");
        MenuAction action = click(0, ClickType.LEFT);
        assertEquals(MenuAction.Kind.ERROR, action.kind());
        assertTrue(action.message().contains("antibadwords.gui.edit.spam"), action.message());
        assertFalse(state.hasUnsavedChanges());
        // viewing still works, the lore says why
        assertTrue(Menu.render(state).get(0).lore().stream().anyMatch(l -> l.contains("You may not change this")));
        // another category is fine
        click(Page.HOME, ClickType.LEFT);
        open("Scanning");
        assertEquals(MenuAction.Kind.REFRESH, click(0, ClickType.LEFT).kind());
        assertTrue(state.hasUnsavedChanges());
    }

    @Test
    void rightsRemovedWhileTheMenuIsOpenStopTheNextClickAndTheSave() {
        open("Scanning");
        assertEquals(MenuAction.Kind.REFRESH, click(0, ClickType.LEFT).kind());
        denied.add(Menu.editNode("Scanning"));
        assertEquals(MenuAction.Kind.ERROR, click(0, ClickType.LEFT).kind(), "the next click is refused");
        assertEquals(MenuAction.Kind.ERROR, done().kind(), "the staged change is not written without the right");
        assertTrue(state.hasUnsavedChanges());
    }

    @Test
    void editorsFollowTheLadderAndWorldsRights() {
        denied.add(Menu.editNode("Ladder"));
        denied.add(Menu.editNode("Worlds"));
        open("Punishment ladder");
        assertEquals(MenuAction.Kind.ERROR, click(Page.ADD, ClickType.LEFT).kind());
        click(Page.HOME, ClickType.LEFT);
        open("World profiles");
        assertEquals(MenuAction.Kind.ERROR, click(Page.ADD, ClickType.LEFT).kind());
        assertEquals(MenuAction.Kind.ERROR, clickNamed("default", ClickType.SHIFT_RIGHT).kind());
        assertFalse(state.hasUnsavedChanges());
        // a text answer that arrives after the right was taken away is refused too
        denied.clear();
        MenuAction ask = click(Page.ADD, ClickType.LEFT);
        assertEquals(MenuAction.Kind.TEXT_INPUT, ask.kind());
        denied.add(Menu.editNode("Worlds"));
        assertEquals(MenuAction.Kind.ERROR, Menu.submitText(state, "relaxed", admin).kind());
        assertFalse(state.hasUnsavedChanges());
    }

    @Test
    void editNodesListOneNodePerCategory() {
        List<String> nodes = Menu.editNodes(new EngineConfigAccess(engine));
        assertTrue(nodes.contains("antibadwords.gui.edit.ladder"), nodes.toString());
        assertTrue(nodes.contains("antibadwords.gui.edit.worlds"));
        assertEquals(nodes.size(), new HashSet<>(nodes).size(), "no duplicates");
        nodes.forEach(n -> assertTrue(n.matches("antibadwords\\.gui\\.edit\\.[a-z0-9-]+"), n));
    }

    // ---- /abw gui <page> -------------------------------------------------------------------------------

    @Test
    void pageArgumentOpensCategoriesSpecialPagesKeysAndNumbers() {
        assertEquals("AntiBadWords", Menu.title(Menu.open(engine, "")));
        assertEquals("AntiBadWords: Spam", Menu.title(Menu.open(engine, "spam")));
        assertEquals("AntiBadWords: Spam", Menu.title(Menu.open(engine, "SPAM")));
        assertEquals("Punishment ladder", Menu.title(Menu.open(engine, "ladder")));
        assertEquals("World profiles", Menu.title(Menu.open(engine, "profiles")));
        assertEquals("World rules", Menu.title(Menu.open(engine, "rules")));
        assertEquals("Languages", Menu.title(Menu.open(engine, "languages")));
        assertEquals("Muted players", Menu.title(Menu.open(engine, "mutes")));
        assertEquals("Word browser", Menu.title(Menu.open(engine, "words")));
        assertTrue(Menu.title(Menu.open(engine, "search protection")).startsWith("Search: protection")
            || Menu.title(Menu.open(engine, "search protection")).startsWith("AntiBadWords: "));
        // a setting key lands on its category
        MenuState byKey = Menu.open(engine, "filters.protection-level");
        assertTrue(Menu.render(byKey).stream().anyMatch(s -> s.name().equals("Protection level")), Menu.render(byKey).stream().map(Slot::name).toList().toString());
        // numbers: pages of the main menu (clamped), and a page of a category
        assertEquals("AntiBadWords", Menu.title(Menu.open(engine, "1")));
        assertEquals("AntiBadWords", Menu.title(Menu.open(engine, "99")));
        assertEquals("AntiBadWords: Spam", Menu.title(Menu.open(engine, "spam 1")));
        // unknown pages give the main menu and are reported by opensPage
        EngineConfigAccess access = new EngineConfigAccess(engine);
        assertFalse(Menu.opensPage(access, "no-such-page"));
        assertTrue(Menu.opensPage(access, "spam"));
        assertTrue(Menu.opensPage(access, ""));
        assertEquals("AntiBadWords", Menu.title(Menu.open(engine, "no-such-page")));
    }

    @Test
    void guiCommandPassesThePageToThePlatformAndTellsAboutUnknownOnes() {
        UUID id = f.platform.join("Alex");
        java.util.List<String> said = new java.util.ArrayList<>();
        CommandSender alex = new CommandSender() {
            public String name() { return "Alex"; }
            public UUID id() { return id; }
            public boolean has(String p) { return p.equals(Menu.PERMISSION); }
            public void send(String m) { said.add(m); }
        };
        engine.commands().execute(alex, "abw", new String[] {"gui", "spam"});
        engine.commands().execute(alex, "abw", new String[] {"gui"});
        engine.commands().execute(alex, "abw", new String[] {"gui", "no", "such", "thing"});
        assertEquals(List.of("spam", "", "no such thing"), f.platform.menuPages);
        assertEquals(1, said.stream().filter(m -> m.contains("Unknown menu page")).count(), said.toString());
        assertTrue(engine.commands().complete(alex, new String[] {"gui", "lad"}).contains("ladder"));
        assertTrue(Files.exists(f.file("config.yml")));
    }
}
