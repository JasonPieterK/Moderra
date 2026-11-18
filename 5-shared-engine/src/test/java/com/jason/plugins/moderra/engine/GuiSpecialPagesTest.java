package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingType;
import com.jason.plugins.moderra.engine.discord.DiscordSettings;
import com.jason.plugins.moderra.engine.gui.ClickType;
import com.jason.plugins.moderra.engine.gui.Menu;
import com.jason.plugins.moderra.engine.gui.MenuAction;
import com.jason.plugins.moderra.engine.gui.MenuPrompts;
import com.jason.plugins.moderra.engine.gui.MenuState;
import com.jason.plugins.moderra.engine.gui.Page;
import com.jason.plugins.moderra.engine.gui.Slot;
import com.jason.plugins.moderra.engine.gui.TextRequest;
import com.jason.plugins.moderra.engine.gui.prompt.PromptKind;
import com.jason.plugins.moderra.engine.moderation.DurationParser;
import com.jason.plugins.moderra.engine.moderation.MuteService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The special pages (status, languages, word browser, ladder, mutes, profiles, search) and prompts, by simulated clicks. */
class GuiSpecialPagesTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    UUID adminId;
    final Set<String> perms = new HashSet<>(Set.of(Menu.PERMISSION));
    final Set<String> denied = new HashSet<>();
    final List<MenuAction> results = new ArrayList<>();
    MenuState state;

    final CommandSender admin = new CommandSender() {
        public String name() { return "Admin"; }
        public UUID id() { return adminId; }
        public boolean has(String p) { return !denied.contains(p) && (perms.contains(p) || p.startsWith(Menu.EDIT_PREFIX)); }
        public void send(String m) { }
    };

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
        adminId = f.platform.join("Admin");
        state = Menu.open(engine);
    }

    private MenuAction click(int slot, ClickType type) {
        return Menu.click(state, slot, type, admin);
    }

    private Slot slotNamed(String prefix) {
        return Menu.render(state).stream().filter(s -> s.name().startsWith(prefix)).findFirst().orElse(null);
    }

    /** The slot with that exact name, turning pages until it shows up (null when no page has it). */
    private Slot slotWithPaging(String name) {
        for (int guard = 0; guard < 20; guard++) {
            Slot s = Menu.render(state).stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
            if (s != null || Menu.render(state).stream().noneMatch(x -> x.index() == Page.NEXT)) {
                return s;
            }
            click(Page.NEXT, ClickType.LEFT);
        }
        return null;
    }

    private void open(String name) {
        Slot s = slotNamed(name);
        for (int guard = 0; s == null && guard < 10 && Menu.render(state).stream().anyMatch(x -> x.index() == Page.NEXT); guard++) {
            click(Page.NEXT, ClickType.LEFT); // the entry may be on a later page
            s = slotNamed(name);
        }
        assertNotNull(s, "no slot named " + name + " in " + Menu.render(state).stream().map(Slot::name).toList());
        assertEquals(MenuAction.Kind.REFRESH, click(s.index(), ClickType.LEFT).kind());
    }

    /** Clicks the request-producing slot, then answers it through the engine's chat prompt. */
    private void ask(int slot, String answer) {
        MenuAction action = click(slot, ClickType.LEFT);
        assertEquals(MenuAction.Kind.TEXT_INPUT, action.kind());
        assertEquals(PromptKind.CHAT, MenuPrompts.start(engine, state, admin, action.request(), results::add));
        assertTrue(f.say(adminId, Message.CHAT, answer).captured());
    }

    /** Slot of the i-th content entry (content is centred, see Page.contentSlot). */
    private int at(int i) {
        return content().get(i).index();
    }

    private List<Slot> content() {
        return Menu.render(state).stream().filter(s -> s.index() < 45).toList();
    }

    // ---- main menu ----------------------------------------------------------------------------

    @Test
    void mainMenuShowsStatusAndAButtonPerSpecialPage() {
        List<String> names = Menu.render(state).stream().map(Slot::name).toList();
        for (String wanted : List.of("Language lists", "Word browser", "Punishment ladder", "Muted players", "World profiles",
            "Protection level: 3", "Scan sources", "Active profile: default", "Search settings")) {
            assertTrue(names.contains(wanted), wanted + " in " + names);
        }
        assertTrue(slotNamed("Language lists").lore().get(0).contains("en"));
        assertTrue(slotNamed("Scan sources").lore().stream().anyMatch(l -> l.startsWith("on") && l.contains("chat")));
        assertEquals(47, slotNamed("Search settings").index());
    }

    @Test
    void statusFollowsStagedValues() {
        open("Language lists");
        click(slotNamed("de").index(), ClickType.LEFT);
        click(Page.HOME, ClickType.LEFT);
        assertTrue(slotNamed("Language lists").lore().get(0).contains("de"));
    }

    // ---- languages ----------------------------------------------------------------------------

    @Test
    void languageTogglesAreStagedAndSavedWithDone() {
        open("Language lists");
        assertEquals("Languages", Menu.title(state));
        Slot de = slotNamed("de");
        assertNotNull(de);
        assertTrue(de.lore().stream().anyMatch(l -> l.startsWith("Words: ")), de.lore().toString());
        assertTrue(de.lore().stream().anyMatch(l -> l.startsWith("Review: ")), de.lore().toString());
        assertTrue(de.lore().contains("Status: off"));
        assertEquals(MenuAction.Kind.REFRESH, click(de.index(), ClickType.LEFT).kind());
        assertTrue(slotNamed("de").lore().contains("Status: ON"));
        assertTrue(slotNamed("de").glowing());
        assertFalse(engine.value(CoreSettings.ENABLED_LANGUAGES).contains("de"), "nothing written yet");
        click(slotNamed("de").index(), ClickType.LEFT);
        assertFalse(state.hasUnsavedChanges(), "toggling back removes the change");
        click(slotNamed("de").index(), ClickType.LEFT);
        assertEquals(MenuAction.Kind.SAVED, click(Page.DONE, ClickType.LEFT).kind());
        assertTrue(engine.value(CoreSettings.ENABLED_LANGUAGES).contains("de"));
        state = Menu.open(engine);
        open("Language lists");
        click(slotNamed("de").index(), ClickType.LEFT);
        click(Page.DONE, ClickType.LEFT);
        assertFalse(engine.value(CoreSettings.ENABLED_LANGUAGES).contains("de"));
    }

    @Test
    void customListIsAlwaysOnAndRightClickBrowses() {
        open("Language lists");
        Slot custom = slotNamed("custom");
        assertTrue(custom.lore().contains("The custom list is always on"));
        assertEquals(MenuAction.Kind.NONE, click(custom.index(), ClickType.LEFT).kind());
        assertFalse(state.hasUnsavedChanges());
        click(slotNamed("en").index(), ClickType.RIGHT);
        assertEquals("Words: en", Menu.title(state));
        assertTrue(content().size() > 10);
    }

    // ---- word browser ---------------------------------------------------------------------------

    @Test
    void bundledWordsArePagedAndReadOnly() {
        open("Word browser");
        open("en");
        assertEquals(Page.CONTENT_SLOTS, content().size());
        assertNotNull(slotNamed("Next page"));
        assertEquals(MenuAction.Kind.NONE, click(at(0), ClickType.LEFT).kind());
        assertEquals(MenuAction.Kind.NONE, click(at(0), ClickType.SHIFT_LEFT).kind());
        assertNull(slotNamed("Add word"));
        click(Page.NEXT, ClickType.LEFT);
        assertNotNull(slotNamed("Previous page"));
    }

    @Test
    void customWordsAddAndShiftClickRemoveAtOnce() {
        open("Word browser");
        open("custom");
        assertEquals(List.of(), engine.customWords());
        ask(Page.ADD, "flibbertigibbet");
        assertEquals(List.of("flibbertigibbet"), engine.customWords());
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());
        assertEquals("flibbertigibbet", content().get(0).name());
        assertEquals(MenuAction.Kind.NONE, click(at(0), ClickType.LEFT).kind(), "a plain click does not remove");
        assertEquals(1, engine.customWords().size());
        assertEquals(MenuAction.Kind.REFRESH, click(at(0), ClickType.SHIFT_LEFT).kind());
        assertEquals(List.of(), engine.customWords());
        assertFalse(state.hasUnsavedChanges(), "custom words do not use staging");
    }

    @Test
    void invalidCustomWordKeepsTheQuestionOpen() {
        open("Word browser");
        open("custom");
        MenuAction action = click(Page.ADD, ClickType.LEFT);
        MenuPrompts.start(engine, state, admin, action.request(), results::add);
        f.say(adminId, Message.CHAT, "x");
        assertTrue(engine.prompts().isActive(adminId), "invalid answer: asked again");
        assertEquals(List.of(), engine.customWords());
        f.say(adminId, Message.CHAT, "cancel");
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());
        assertNull(state.pendingText());
    }

    @Test
    void whitelistIsStagedThroughTheListView() {
        open("Word browser");
        open("Whitelist");
        int before = engine.value(CoreSettings.WHITELIST).size();
        ask(Page.ADD, "zorblax");
        assertEquals(before, engine.value(CoreSettings.WHITELIST).size(), "staged, not written");
        assertTrue(state.hasUnsavedChanges());
        assertNotNull(slotWithPaging("zorblax"), "the new word is listed (on a later page when the list is long)");
        assertEquals(MenuAction.Kind.SAVED, click(Page.DONE, ClickType.LEFT).kind());
        assertTrue(engine.value(CoreSettings.WHITELIST).contains("zorblax"));
        state = Menu.open(engine);
        open("Word browser");
        open("Whitelist");
        click(slotWithPaging("zorblax").index(), ClickType.LEFT);
        click(Page.DONE, ClickType.LEFT);
        assertFalse(engine.value(CoreSettings.WHITELIST).contains("zorblax"));
    }

    // ---- ladder -----------------------------------------------------------------------------------

    @Test
    void ladderIsListedWithItsSteps() {
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
        open("Punishment ladder");
        List<Slot> steps = content();
        assertEquals(List.of("warn-first", "big-mute"), steps.stream().map(Slot::name).toList());
        assertTrue(steps.get(0).lore().get(0).startsWith("At 2 strikes"));
        assertTrue(steps.get(1).lore().get(0).contains("again every 3"));
        assertTrue(steps.get(1).lore().stream().anyMatch(l -> l.contains("mute 10m")));
        assertFalse(state.hasUnsavedChanges(), "looking changes nothing");
        assertEquals(MenuAction.Kind.REFRESH, click(at(1), ClickType.LEFT).kind(), "a click opens the step editor (see GuiEditorTest)");
        assertEquals("Step: big-mute", Menu.title(state));
    }

    @Test
    void emptyLadderExplainsTheClassicPunishment() {
        open("Punishment ladder");
        assertEquals("No ladder steps", content().get(0).name());
    }

    // ---- mutes ----------------------------------------------------------------------------------------

    @Test
    void muteListUnmutesOnClickWithPermission() {
        UUID bob = f.platform.join("Bob");
        UUID carl = f.platform.join("Carl");
        engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "spam", "system");
        engine.mutes().mute(carl, "Carl", 600_000, "", "system");
        open("Muted players");
        List<Slot> mutes = content();
        assertEquals(List.of("Bob", "Carl"), mutes.stream().map(Slot::name).toList());
        assertEquals("Permanent", mutes.get(0).lore().get(0));
        assertTrue(mutes.get(1).lore().get(0).startsWith("Remaining: "));

        MenuAction denied = click(at(0), ClickType.LEFT);
        assertEquals(MenuAction.Kind.ERROR, denied.kind());
        assertTrue(denied.message().contains(MuteService.PERMISSION));
        assertTrue(engine.mutes().isMuted(bob));

        perms.add(MuteService.PERMISSION);
        assertEquals(MenuAction.Kind.REFRESH, click(at(0), ClickType.LEFT).kind());
        assertFalse(engine.mutes().isMuted(bob));
        assertTrue(engine.mutes().isMuted(carl));
        assertEquals(List.of("Carl"), content().stream().map(Slot::name).toList());
        click(at(0), ClickType.RIGHT);
        assertEquals("Nobody is muted", content().get(0).name());
    }

    // ---- profiles ---------------------------------------------------------------------------------------

    @Test
    void profilesAreListedWithTheirDifferences() {
        TestFiles.write(f.file("worlds.yml"), """
            default-profile: default
            use-permission-profiles: true
            profiles:
              default: {}
              relaxed:
                filters.protection-level: 1
                filters.scan.signs: false
            """);
        engine = f.start();
        state = Menu.open(engine);
        open("World profiles");
        List<Slot> profiles = content();
        assertEquals(List.of("default", "relaxed"), profiles.stream().map(Slot::name).toList());
        assertTrue(profiles.get(0).glowing());
        assertTrue(profiles.get(1).lore().stream().anyMatch(l -> l.contains("2 settings differ")), profiles.get(1).lore().toString());
        assertEquals(MenuAction.Kind.REFRESH, click(at(1), ClickType.LEFT).kind(), "a click opens the profile editor (see GuiEditorTest)");
        assertTrue(Menu.render(state).stream().anyMatch(sl -> sl.name().equals("filters.protection-level")));
    }

    // ---- search -----------------------------------------------------------------------------------------

    @Test
    void searchJumpsToTheOnlyMatchingSetting() {
        ask(47, "filters.scan.join-names");
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());
        assertEquals("Moderra: Scanning", Menu.title(state));
        Slot hit = slotNamed("Scan player names on join");
        assertNotNull(hit);
        click(hit.index(), ClickType.LEFT);
        assertTrue(state.hasUnsavedChanges());
        click(Page.DONE, ClickType.LEFT);
        assertTrue(engine.value(CoreSettings.SCAN_JOIN));
    }

    @Test
    void searchWithSeveralMatchesListsEditableSettings() {
        ask(47, "scan");
        assertTrue(Menu.title(state).startsWith("Search: scan"));
        Slot chat = content().stream().filter(s -> s.name().equals("Scan chat")).findFirst().orElseThrow();
        click(chat.index(), ClickType.LEFT);
        assertEquals(false, state.value(CoreSettings.SCAN_CHAT));
        click(Page.BACK, ClickType.LEFT);
        assertEquals("Moderra *", Menu.title(state));
    }

    @Test
    void searchWithoutMatchAsksAgain() {
        MenuAction action = click(47, ClickType.LEFT);
        MenuPrompts.start(engine, state, admin, action.request(), results::add);
        f.say(adminId, Message.CHAT, "qqqqzzzz");
        assertTrue(engine.prompts().isActive(adminId), "no match: asked again");
        assertTrue(results.isEmpty());
        f.say(adminId, Message.CHAT, "cancel");
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());
        assertEquals("Moderra", Menu.title(state));
    }

    // ---- prompts for settings -----------------------------------------------------------------------------

    @Test
    void stringAndSecretAnswersFlowIntoStagedState() {
        Setting<String> name = DiscordSettings.USERNAME;
        Setting<String> secret = DiscordSettings.ALERTS_URL;
        assertEquals(SettingType.SECRET, secret.type());
        click(categoryIndex(name), ClickType.LEFT);
        ask(slotOf(name), "NewName");
        assertEquals("NewName", state.value(name));
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());

        String url = "https://discord.com/api/webhooks/123456/abcdef-secret-token";
        ask(slotOf(secret), url);
        assertEquals(url, state.value(secret));
        assertTrue(Menu.render(state).stream().noneMatch(s -> (s.name() + s.lore()).contains("abcdef-secret-token")), "secret never shown");
        assertTrue(f.platform.sentTo(adminId).stream().noneMatch(m -> m.contains("abcdef-secret-token")), "secret never echoed");
        assertEquals(MenuAction.Kind.SAVED, click(Page.DONE, ClickType.LEFT).kind());
        assertEquals("NewName", engine.value(name));
        assertEquals(url, engine.value(secret));
    }

    @Test
    void cancelledPromptChangesNothing() {
        Setting<String> name = DiscordSettings.USERNAME;
        click(categoryIndex(name), ClickType.LEFT);
        MenuAction action = click(slotOf(name), ClickType.LEFT);
        MenuPrompts.start(engine, state, admin, action.request(), results::add);
        f.say(adminId, Message.CHAT, "cancel");
        assertFalse(state.hasUnsavedChanges());
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());
    }

    @Test
    void consoleViewerCannotPrompt() {
        CommandSender console = new CommandSender() {
            public String name() { return "CONSOLE"; }
            public UUID id() { return null; }
            public boolean has(String p) { return true; }
            public void send(String m) { }
        };
        TextRequest request = new TextRequest("x", TextRequest.Purpose.SEARCH, "q", "h", false);
        assertNull(MenuPrompts.start(engine, state, console, request, results::add));
        assertEquals(MenuAction.Kind.REFRESH, results.get(0).kind());
    }

    // ---- permissions ------------------------------------------------------------------------------------------

    @Test
    void everySpecialPageRechecksThePermission() {
        open("Language lists");
        perms.clear();
        assertEquals(MenuAction.Kind.DENIED, click(at(0), ClickType.LEFT).kind());
        assertFalse(state.hasUnsavedChanges());
        assertEquals(MenuAction.Kind.DENIED, Menu.submitText(state, "x", admin).kind());
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private int categoryIndex(Setting<?> setting) {
        return Menu.render(state).stream().filter(s -> s.icon().equals("category") && s.name().equals(setting.category()))
            .findFirst().orElseThrow().index();
    }

    private int slotOf(Setting<?> setting) {
        return Menu.render(state).stream().filter(s -> s.name().equals(setting.title())).findFirst().orElseThrow().index();
    }
}
