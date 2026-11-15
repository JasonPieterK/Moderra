package com.jason.plugins.moderra.engine.gui;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.DurationText;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingType;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The settings menu as a pure model (no Minecraft classes). A platform keeps one {@link MenuState} per open menu and
 * calls:
 * <ul>
 *   <li>{@link #render} to get the slots to show (and {@link #title});</li>
 *   <li>{@link #click} for every click, and acts on the returned {@link MenuAction};</li>
 *   <li>{@link #submitText} / {@link #cancelText} to answer a {@code TEXT_INPUT} action;</li>
 *   <li>{@link #closeRequested} when the viewer closes the inventory (unsaved-changes warning).</li>
 * </ul>
 * Everything is generated from the settings registry. Changes are staged in the state; nothing is written until the
 * viewer clicks Done. Every call re-checks the permission {@value #PERMISSION}; a missing permission changes nothing.
 */
public final class Menu {
    public static final String PERMISSION = "moderra.gui";
    /**
     * Prefix of the per-category edit permissions: {@code moderra.gui.edit.<category>} where the category is the
     * lower-case name with every run of other characters turned into "-" (see {@link #editNode}). Checked on every click
     * that changes something. The platform must answer TRUE for a node nobody configured (Bukkit: the plugin registers
     * them with default true; mods: the fallback is true), so holders of {@value #PERMISSION} may edit everything until
     * an admin takes a node away.
     */
    public static final String EDIT_PREFIX = "moderra.gui.edit.";

    private Menu() {
    }

    // ------------------------------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------------------------------

    public static MenuState open(ConfigAccess access) {
        return new MenuState(access);
    }

    public static MenuState open(Engine engine) {
        return new MenuState(new EngineConfigAccess(engine));
    }

    /** Opens at a page, see {@link #open(ConfigAccess, String)}. */
    public static MenuState open(Engine engine, String page) {
        return open(new EngineConfigAccess(engine), page);
    }

    /**
     * Opens the menu at the page the player asked for with {@code /moderra gui <page>}. {@code page} may be:
     * <ul>
     *   <li>empty: the main menu;</li>
     *   <li>a number: that page of the main menu (1 = first);</li>
     *   <li>a category name ({@code spam}, {@code "Spam filter"}; case and "-" or spaces do not matter; a unique start or
     *       part of the name is enough), optionally followed by a page number;</li>
     *   <li>a special page: {@code languages}, {@code words}, {@code ladder}, {@code mutes}, {@code profiles},
     *       {@code rules} (world rules), {@code search <text>};</li>
     *   <li>a setting key ({@code filters.protection-level}): the page of its category that shows it.</li>
     * </ul>
     * An unknown page gives the main menu (use {@link #opensPage} to tell the player).
     */
    public static MenuState open(ConfigAccess access, String page) {
        MenuState state = new MenuState(access);
        goTo(state, page);
        return state;
    }

    /** Whether {@link #open(ConfigAccess, String)} understands the page (an empty page counts as understood). */
    public static boolean opensPage(ConfigAccess access, String page) {
        return goTo(new MenuState(access), page);
    }

    private static String normalise(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** @return false when a non-empty page was not understood (the state is then the main menu) */
    private static boolean goTo(MenuState state, String page) {
        String text = page == null ? "" : page.trim();
        if (text.isEmpty()) {
            return true;
        }
        String[] words = text.split("\\s+");
        int number = 1;
        int end = words.length;
        if (words[end - 1].matches("\\d{1,6}")) {
            number = Math.max(1, Integer.parseInt(words[end - 1]));
            end--;
        }
        if (end == 0) {
            state.setPage(number - 1);
            return true;
        }
        String first = words[0].toLowerCase(java.util.Locale.ROOT);
        boolean engine = state.access().engine() != null;
        if (first.equals("search") && end > 1) {
            String query = String.join(" ", java.util.Arrays.copyOfRange(words, 1, end));
            if (!SpecialPages.find(state, query).isEmpty()) {
                state.push(new MenuState.View(MenuState.Kind.SEARCH, query, number - 1));
                return true;
            }
            return false;
        }
        String joined = normalise(String.join(" ", java.util.Arrays.copyOfRange(words, 0, end)));
        if (engine) {
            MenuState.Kind special = switch (joined) {
                case "languages", "language", "lang" -> MenuState.Kind.LANGUAGES;
                case "words", "wordbrowser", "sources" -> MenuState.Kind.SOURCES;
                case "ladder", "punishmentladder" -> MenuState.Kind.LADDER;
                case "mutes", "muted" -> MenuState.Kind.MUTES;
                case "profiles", "worldprofiles" -> MenuState.Kind.PROFILES;
                case "rules", "worldrules" -> MenuState.Kind.RULES;
                default -> null;
            };
            if (special != null) {
                state.push(new MenuState.View(special, "", number - 1));
                return true;
            }
        }
        // a setting key
        Setting<?> bySetting = state.access().registry().get(String.join(" ", java.util.Arrays.copyOfRange(words, 0, end)));
        if (bySetting != null && !settingsOf(state, bySetting.category()).isEmpty()) {
            int at = settingsOf(state, bySetting.category()).indexOf(bySetting);
            state.push(new MenuState.View(MenuState.Kind.CATEGORY, bySetting.category(), Math.max(0, at) / Page.CONTENT_SLOTS));
            return true;
        }
        List<String> categories = new ArrayList<>();
        for (String category : state.access().registry().categories()) {
            if (!settingsOf(state, category).isEmpty()) {
                categories.add(category);
            }
        }
        String match = unique(categories, joined);
        if (match == null) {
            return false;
        }
        state.push(new MenuState.View(MenuState.Kind.CATEGORY, match, number - 1));
        return true;
    }

    /** The one name that equals, else starts with, else contains the wanted text (ignoring case and punctuation). */
    private static String unique(List<String> names, String wanted) {
        for (java.util.function.BiPredicate<String, String> test : List.<java.util.function.BiPredicate<String, String>>of(
            String::equals, String::startsWith, String::contains)) {
            List<String> found = names.stream().filter(n -> test.test(normalise(n), wanted)).toList();
            if (found.size() == 1) {
                return found.get(0);
            }
            if (found.size() > 1) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Edit rights
    // ------------------------------------------------------------------------------------------

    /** The permission needed to change settings of a category, for example {@code moderra.gui.edit.spam-filter}. */
    public static String editNode(String category) {
        String slug = category.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return EDIT_PREFIX + (slug.isEmpty() ? "other" : slug);
    }

    /** Whether the viewer of the call being handled may change settings of the category (fails closed without a viewer). */
    static boolean mayEdit(MenuState state, String category) {
        CommandSender viewer = state.viewer();
        return viewer != null && viewer.has(editNode(category));
    }

    /** {@code null} when the viewer may edit the category, else the action that tells them they may not. */
    static MenuAction guard(MenuState state, String category) {
        return mayEdit(state, category) ? null
            : MenuAction.error("You cannot change settings in \"" + category + "\". Permission needed: " + editNode(category) + ".");
    }

    /** All nodes the platform should know (default true): one per category that has anything the menu can change. */
    public static List<String> editNodes(ConfigAccess access) {
        List<String> out = new ArrayList<>();
        for (String category : access.registry().categories()) {
            out.add(editNode(category));
        }
        return out;
    }

    public static String title(MenuState state) {
        return page(state).title();
    }

    /** The slots to show, in slot order. Slots without an item are not in the list. */
    public static List<Slot> render(MenuState state) {
        List<Slot> out = new ArrayList<>();
        page(state).slots().forEach((index, entry) -> out.add(entry.slot(index)));
        return out;
    }

    public static MenuAction click(MenuState state, int slot, ClickType type, CommandSender viewer) {
        if (!allowed(viewer)) {
            state.discard();
            return MenuAction.denied();
        }
        state.setViewer(viewer);
        Entry entry = page(state).slots().get(slot);
        if (entry == null || type == null || type == ClickType.OTHER) {
            return MenuAction.none();
        }
        return entry.handler().click(state, type);
    }

    /** The viewer closed (or tried to close) the inventory. With unsaved changes a confirmation page opens instead. */
    public static MenuAction closeRequested(MenuState state, CommandSender viewer) {
        if (!allowed(viewer)) {
            state.discard();
            return MenuAction.denied();
        }
        state.setViewer(viewer);
        if (state.hasUnsavedChanges() && state.current().kind() != MenuState.Kind.CONFIRM) {
            state.push(new MenuState.View(MenuState.Kind.CONFIRM, "", 0));
            return MenuAction.refresh();
        }
        int lost = state.stagedCount();
        state.discard();
        return MenuAction.close(lost > 0 ? "The plugin discarded the unsaved changes." : null);
    }

    /** The viewer is gone (quit, server stop): forget everything staged. */
    public static void closed(MenuState state) {
        state.discard();
    }

    /** The answer of a text prompt. {@code ERROR} keeps the request pending so the platform can ask again. */
    public static MenuAction submitText(MenuState state, String text, CommandSender viewer) {
        if (!allowed(viewer)) {
            state.discard();
            return MenuAction.denied();
        }
        state.setViewer(viewer);
        TextRequest request = state.pendingText();
        if (request == null) {
            return MenuAction.none();
        }
        if (request.purpose() == TextRequest.Purpose.SEARCH) {
            return SpecialPages.submitSearch(state, text);
        }
        if (request.purpose() == TextRequest.Purpose.ADD_CUSTOM_WORD) {
            MenuAction denied = guard(state, CoreSettings.ENABLED_LANGUAGES.category());
            return denied != null ? denied : SpecialPages.submitCustomWord(state, text);
        }
        if (request.purpose().ordinal() >= TextRequest.Purpose.ADD_STEP.ordinal()) {
            return EditorPages.submit(state, request, text);
        }
        Setting<?> setting = state.access().registry().get(request.key());
        if (setting == null) {
            state.setPending(null);
            return MenuAction.error("That setting no longer exists.");
        }
        MenuAction notAllowed = guard(state, setting.category());
        if (notAllowed != null) {
            return notAllowed;
        }
        String value = text == null ? "" : text;
        if (request.purpose() == TextRequest.Purpose.ADD_ITEM) {
            value = value.trim();
            if (value.isEmpty()) {
                return MenuAction.error("Nothing typed. Type the new item or cancel.");
            }
            List<String> list = new ArrayList<>(listValue(state, setting));
            list.add(value);
            state.stage(setting, List.copyOf(list));
        } else {
            if (setting.type() == SettingType.SECRET) {
                value = value.trim();
                if (value.isEmpty()) {
                    return MenuAction.error("Nothing typed. Type the new secret or cancel. The plugin changed nothing.");
                }
            }
            Object checked;
            try {
                checked = setting.require(value);
            } catch (IllegalArgumentException ex) {
                return MenuAction.error(setting.type() == SettingType.SECRET ? "That value is not valid." : ex.getMessage());
            }
            state.stage(setting, checked);
        }
        state.setPending(null);
        return MenuAction.refresh();
    }

    /**
     * Checks a typed answer for the pending request without changing anything: {@code null} = fine, else the error for
     * the player (never repeats a secret). Used as the validator of the engine's text prompt.
     */
    public static String validateText(MenuState state, String text) {
        TextRequest request = state.pendingText();
        if (request == null) {
            return "Nothing is being asked right now.";
        }
        String value = text == null ? "" : text.trim();
        switch (request.purpose()) {
            case SEARCH:
                if (value.isEmpty()) {
                    return "Nothing typed.";
                }
                return SpecialPages.find(state, value).isEmpty() ? "No setting matches that. Try another word." : null;
            case ADD_ITEM:
                return value.isEmpty() ? "Nothing typed." : null;
            case ADD_CUSTOM_WORD:
                return com.jason.plugins.moderra.WordMatcher.isValidEntry(value) ? null
                    : "That is not a valid word (use at least 2 letters or digits).";
            case ADD_STEP, SET_STEP_TEXT, ADD_ACTION, EDIT_ACTION, ADD_PROFILE, ADD_OVERRIDE, SET_OVERRIDE, ADD_RULE:
                return EditorPages.validate(state, request, text);
            default:
                Setting<?> setting = state.access().registry().get(request.key());
                if (setting == null) {
                    return "That setting no longer exists.";
                }
                if (setting.type() == SettingType.SECRET && value.isEmpty()) {
                    return "Nothing typed.";
                }
                try {
                    setting.require(text == null ? "" : text);
                    return null;
                } catch (IllegalArgumentException ex) {
                    return setting.type() == SettingType.SECRET ? "That value is not valid." : ex.getMessage();
                }
        }
    }

    /** The viewer cancelled the text prompt (typed "cancel", timeout, closed the sign). Nothing changes. */
    public static MenuAction cancelText(MenuState state, CommandSender viewer) {
        if (!allowed(viewer)) {
            state.discard();
            return MenuAction.denied();
        }
        state.setPending(null);
        return MenuAction.refresh();
    }

    private static boolean allowed(CommandSender viewer) {
        return viewer != null && viewer.has(PERMISSION);
    }

    // ------------------------------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------------------------------

    private static Page page(MenuState state) {
        MenuState.View view = state.current();
        return switch (view.kind()) {
            case MAIN -> mainPage(state, view);
            case CATEGORY -> categoryPage(state, view);
            case LIST -> listPage(state, view);
            case CONFIRM -> confirmPage(state);
            case LANGUAGES -> SpecialPages.languages(state, view);
            case SOURCES -> SpecialPages.sources(state, view);
            case WORDS -> SpecialPages.words(state, view);
            case LADDER -> EditorPages.ladder(state, view);
            case MUTES -> SpecialPages.mutes(state, view);
            case PROFILES -> EditorPages.profiles(state, view);
            case SEARCH -> SpecialPages.search(state, view);
            case STEP -> EditorPages.step(state, view);
            case PROFILE -> EditorPages.profile(state, view);
            case RULES -> EditorPages.rules(state, view);
        };
    }

    private static Page mainPage(MenuState state, MenuState.View view) {
        List<Entry> entries = new ArrayList<>();
        for (String category : state.access().registry().categories()) {
            List<Setting<?>> settings = settingsOf(state, category);
            if (settings.isEmpty()) {
                continue;
            }
            long changed = settings.stream().filter(state::isStaged).count();
            List<String> lore = new ArrayList<>();
            lore.add(settings.size() + (settings.size() == 1 ? " setting" : " settings"));
            if (changed > 0) {
                lore.add("Unsaved changes here: " + changed);
            }
            lore.add("Click to open");
            entries.add(new Entry(Icons.CATEGORY, category, lore, changed > 0, (s, t) -> {
                s.push(new MenuState.View(MenuState.Kind.CATEGORY, category, 0));
                return MenuAction.refresh();
            }));
        }
        entries.addAll(SpecialPages.mainExtras(state));
        Map<Integer, Entry> extra = new TreeMap<>();
        extra.put(Page.ADD, new Entry(Icons.SEARCH, "Search settings", List.of("Click, then type a word of a setting's", "name or description to jump to it"),
            false, (s, t) -> askText(s, TextRequest.Purpose.SEARCH, "", "Type what to search for (part of a name or description).",
                "any text")));
        return paged(state, view, "Moderra", entries, false, extra);
    }

    static List<Setting<?>> settingsOf(MenuState state, String category) {
        List<Setting<?>> out = new ArrayList<>();
        state.access().registry().inCategory(category).forEach(e -> {
            if (e instanceof Setting<?> s) {
                out.add(s);
            }
        });
        return out;
    }

    private static Page categoryPage(MenuState state, MenuState.View view) {
        List<Entry> entries = new ArrayList<>();
        for (Setting<?> setting : settingsOf(state, view.target())) {
            entries.add(settingEntry(state, setting));
        }
        return paged(state, view, "Moderra: " + view.target(), entries, false);
    }

    private static Page listPage(MenuState state, MenuState.View view) {
        Setting<?> setting = state.access().registry().get(view.target());
        if (setting == null) {
            return paged(state, view, "Moderra", List.of(), false);
        }
        List<String> items = listValue(state, setting);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            String item = items.get(i);
            entries.add(new Entry(Icons.LIST_ITEM, shorten(item, 60), List.of("Click to remove this item"), false, (s, t) -> {
                MenuAction denied = guard(s, setting.category());
                if (denied != null) {
                    return denied;
                }
                List<String> now = new ArrayList<>(listValue(s, setting));
                if (index >= now.size() || !now.get(index).equals(item)) {
                    return MenuAction.refresh();
                }
                now.remove(index);
                s.stage(setting, List.copyOf(now));
                return MenuAction.refresh();
            }));
        }
        return paged(state, view, "List: " + setting.title(), entries, true);
    }

    private static Page confirmPage(MenuState state) {
        Map<Integer, Entry> slots = new TreeMap<>();
        int n = state.stagedCount();
        slots.put(20, new Entry(Icons.DONE, "Save and close", List.of(n + " unsaved change" + (n == 1 ? "" : "s")),
            true, (s, t) -> save(s)));
        slots.put(22, new Entry(Icons.WARNING, "Unsaved changes", List.of("Closing now would lose " + n + " change" + (n == 1 ? "" : "s") + "."),
            false, (s, t) -> MenuAction.none()));
        slots.put(24, new Entry(Icons.CANCEL, "Discard changes", List.of("Throw the changes away and close"),
            false, (s, t) -> {
                s.discard();
                return MenuAction.close("The plugin discarded the unsaved changes.");
            }));
        slots.put(Page.BACK, new Entry(Icons.BACK, "Keep editing", List.of("Go back to the menu"), false, (s, t) -> {
            s.pop();
            return MenuAction.refresh();
        }));
        return new Page("Unsaved changes", slots, 0, 1);
    }

    /** Slices the entries into pages of 45 and adds the navigation row. */
    static Page paged(MenuState state, MenuState.View view, String title, List<Entry> entries, boolean listView) {
        return paged(state, view, title, entries, listView, Map.of());
    }

    /** As above; {@code extra} entries (navigation row slots) are added last and replace a default entry in the same slot. */
    static Page paged(MenuState state, MenuState.View view, String title, List<Entry> entries, boolean listView,
                      Map<Integer, Entry> extra) {
        int pages = Math.max(1, (entries.size() + Page.CONTENT_SLOTS - 1) / Page.CONTENT_SLOTS);
        int index = Math.max(0, Math.min(view.page(), pages - 1));
        if (index != view.page()) {
            state.setPage(index);
        }
        Map<Integer, Entry> slots = new TreeMap<>();
        int from = index * Page.CONTENT_SLOTS;
        for (int i = from; i < Math.min(entries.size(), from + Page.CONTENT_SLOTS); i++) {
            slots.put(i - from, entries.get(i));
        }
        if (state.depth() > 1) {
            slots.put(Page.BACK, new Entry(Icons.BACK, "Back", List.of("Go back one step"), false, (s, t) -> {
                s.pop();
                return MenuAction.refresh();
            }));
            slots.put(Page.HOME, new Entry(Icons.HOME, "Main menu", List.of("Go to the category list"), false, (s, t) -> {
                s.home();
                return MenuAction.refresh();
            }));
        }
        if (index > 0) {
            slots.put(Page.PREV, new Entry(Icons.PREV_PAGE, "Previous page", List.of("Page " + index + " of " + pages), false,
                (s, t) -> turn(s, -1)));
        }
        if (index < pages - 1) {
            slots.put(Page.NEXT, new Entry(Icons.NEXT_PAGE, "Next page", List.of("Page " + (index + 2) + " of " + pages), false,
                (s, t) -> turn(s, 1)));
        }
        int n = state.stagedCount();
        slots.put(Page.INFO, new Entry(Icons.INFO, "Page " + (index + 1) + " of " + pages,
            List.of(n == 0 ? "No unsaved changes" : "Unsaved changes: " + n), n > 0, (s, t) -> MenuAction.none()));
        slots.put(Page.CANCEL, new Entry(Icons.CANCEL, "Cancel", List.of("Discard all unsaved changes and close"), false, (s, t) -> {
            s.discard();
            return MenuAction.close("The plugin discarded the unsaved changes.");
        }));
        slots.put(Page.DONE, new Entry(Icons.DONE, "Done", List.of(n == 0 ? "Close the menu" : "Save " + n + " change" + (n == 1 ? "" : "s") + " and close"),
            n > 0, (s, t) -> save(s)));
        if (listView) {
            Setting<?> setting = state.access().registry().get(view.target());
            if (setting != null) {
                slots.put(Page.ADD, new Entry(Icons.ADD, "Add item", List.of("Click, then type the new item"), false,
                    (s, t) -> askText(s, setting, TextRequest.Purpose.ADD_ITEM)));
                slots.put(Page.RESET, new Entry(Icons.RESET, "Reset list", List.of("Back to the default list"), false, (s, t) -> reset(s, setting)));
            }
        }
        slots.putAll(extra);
        String shown = state.hasUnsavedChanges() ? title + " *" : title;
        return new Page(shown, slots, index, pages);
    }

    private static MenuAction turn(MenuState state, int delta) {
        state.setPage(Math.max(0, state.current().page() + delta));
        return MenuAction.refresh();
    }

    // ------------------------------------------------------------------------------------------
    // Setting entries
    // ------------------------------------------------------------------------------------------

    static Entry settingEntry(MenuState state, Setting<?> setting) {
        Object value = state.value(setting);
        boolean staged = state.isStaged(setting);
        List<String> lore = new ArrayList<>(wrap(setting.description(), 40, 6));
        lore.add("");
        lore.add("Value: " + describe(setting, value));
        if (setting.type() != SettingType.SECRET) {
            lore.add("Default: " + describe(setting, setting.defaultValue()));
        }
        lore.add("Allowed: " + setting.constraintText());
        if (staged) {
            lore.add(setting.type() == SettingType.SECRET ? "Changed, not saved yet (value hidden)" : "Changed, not saved yet (was "
                + describe(setting, state.access().current(setting)) + ")");
        }
        lore.add("");
        String icon;
        Entry.Handler handler;
        switch (setting.type()) {
            case BOOLEAN -> {
                icon = Boolean.TRUE.equals(value) ? Icons.TOGGLE_ON : Icons.TOGGLE_OFF;
                lore.add("Click: switch on/off");
                handler = (s, t) -> t.isLeft() || t.isRight() ? changed(s.stage(setting, !Boolean.TRUE.equals(s.value(setting)))) : handleReset(s, setting, t);
            }
            case INT, DOUBLE, DURATION -> {
                icon = setting.type() == SettingType.DURATION ? Icons.DURATION : Icons.NUMBER;
                lore.add("Left click: + " + stepText(setting) + "   Right click: - " + stepText(setting));
                lore.add("Shift: x10");
                handler = (s, t) -> {
                    if (t.isLeft() || t.isRight()) {
                        return changed(s.stage(setting, step(setting, s.value(setting), t.isLeft() ? 1 : -1, t.isShift() ? 10 : 1)));
                    }
                    return handleReset(s, setting, t);
                };
            }
            case ENUM -> {
                icon = Icons.CYCLE;
                List<String> allowed = setting.allowedValues();
                for (String option : allowed) {
                    lore.add((option.equalsIgnoreCase(String.valueOf(value)) ? "> " : "  ") + option);
                }
                lore.add("Left click: next   Right click: previous");
                handler = (s, t) -> {
                    if (t.isLeft() || t.isRight()) {
                        return changed(s.stage(setting, cycle(setting, String.valueOf(s.value(setting)), t.isLeft() ? 1 : -1)));
                    }
                    return handleReset(s, setting, t);
                };
            }
            case STRING, SECRET -> {
                icon = setting.type() == SettingType.SECRET ? Icons.SECRET : Icons.TEXT;
                lore.add("Click: type a new value");
                handler = (s, t) -> t.isLeft() || t.isRight() ? askText(s, setting, TextRequest.Purpose.SET_VALUE) : handleReset(s, setting, t);
            }
            case STRING_LIST -> {
                icon = Icons.LIST;
                lore.add("Click: edit the list");
                handler = (s, t) -> {
                    if (t.isLeft() || t.isRight()) {
                        s.push(new MenuState.View(MenuState.Kind.LIST, setting.key(), 0));
                        return MenuAction.refresh();
                    }
                    return handleReset(s, setting, t);
                };
            }
            default -> throw new IllegalStateException("unknown type " + setting.type());
        }
        lore.add("Middle click or drop key: reset to default");
        if (state.viewer() != null && !mayEdit(state, setting.category())) {
            lore.add("You may not change this (permission " + editNode(setting.category()) + ")");
        }
        Entry.Handler inner = handler;
        boolean opensList = setting.type() == SettingType.STRING_LIST;
        Entry.Handler guarded = (s, t) -> {
            if (opensList && (t.isLeft() || t.isRight())) {
                return inner.click(s, t); // looking at a list is allowed; every change inside it is checked
            }
            if (t == ClickType.OTHER) {
                return MenuAction.none();
            }
            MenuAction denied = guard(s, setting.category());
            return denied != null ? denied : inner.click(s, t);
        };
        return new Entry(icon, setting.title(), lore, staged, guarded);
    }

    private static MenuAction changed(boolean changed) {
        return changed ? MenuAction.refresh() : MenuAction.none();
    }

    private static MenuAction handleReset(MenuState state, Setting<?> setting, ClickType type) {
        return type.isReset() ? reset(state, setting) : MenuAction.none();
    }

    private static MenuAction reset(MenuState state, Setting<?> setting) {
        MenuAction denied = guard(state, setting.category());
        if (denied != null) {
            return denied;
        }
        return changed(state.stage(setting, setting.defaultValue()));
    }

    /** A text request that is not about one setting (search, custom word). */
    static MenuAction askText(MenuState state, TextRequest.Purpose purpose, String key, String prompt, String hint) {
        TextRequest request = new TextRequest(key, purpose, prompt, hint, false);
        state.setPending(request);
        return MenuAction.text(request);
    }

    private static MenuAction askText(MenuState state, Setting<?> setting, TextRequest.Purpose purpose) {
        MenuAction denied = guard(state, setting.category());
        if (denied != null) {
            return denied;
        }
        boolean secret = setting.type() == SettingType.SECRET;
        String prompt = purpose == TextRequest.Purpose.ADD_ITEM ? "Type the new item for " + setting.title() + "."
            : "Type the new value for " + setting.title() + (secret ? " (it will not be shown)." : ".");
        TextRequest request = new TextRequest(setting.key(), purpose, prompt, setting.constraintText(), secret);
        state.setPending(request);
        return MenuAction.text(request);
    }

    static MenuAction save(MenuState state) {
        if (!state.hasUnsavedChanges()) {
            return MenuAction.close(null);
        }
        Map<Setting<?>, Object> changes = new LinkedHashMap<>(state.staged());
        Map<com.jason.plugins.moderra.engine.config.Section, Map<String, Object>> sections = state.stagedSections();
        // last line of defence: the viewer may only save what they may edit (rights can change while the menu is open)
        for (Setting<?> setting : changes.keySet()) {
            MenuAction denied = guard(state, setting.category());
            if (denied != null) {
                return denied;
            }
        }
        for (com.jason.plugins.moderra.engine.config.Section section : sections.keySet()) {
            MenuAction denied = guard(state, section.category());
            if (denied != null) {
                return denied;
            }
        }
        String sectionProblem = EditorPages.problem(state);
        if (sectionProblem != null) {
            return MenuAction.error("Not saved. Problem: " + noDot(sectionProblem) + ". The menu keeps your changes.");
        }
        for (Map.Entry<Setting<?>, Object> e : changes.entrySet()) {
            Setting<?> setting = e.getKey();
            try {
                setting.require(rawYaml(setting, e.getValue()));
            } catch (IllegalArgumentException ex) {
                return MenuAction.error("Not saved. Problem: " + noDot(setting.type() == SettingType.SECRET ? setting.key() + " is not valid" : ex.getMessage()) + ". The menu keeps your changes.");
            }
        }
        try {
            if (sections.isEmpty()) {
                state.access().save(changes);
            } else {
                state.access().save(changes, sections);
            }
        } catch (IOException | RuntimeException ex) {
            return MenuAction.error("Not saved. Problem: " + noDot(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                + ". The menu keeps your changes.");
        }
        int n = changes.size() + sections.size();
        state.discard();
        return MenuAction.saved("The plugin saved " + n + " setting" + (n == 1 ? "" : "s") + " and reloaded the configuration.");
    }

    /** A message without its closing full stop, so a sentence can add its own. */
    private static String noDot(String text) {
        String t = text == null ? "" : text.trim();
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object rawYaml(Setting<?> setting, Object value) {
        return ((Setting) setting).toYaml(value);
    }

    // ------------------------------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<String> listValue(MenuState state, Setting<?> setting) {
        Object value = state.value(setting);
        return value instanceof List<?> ? (List<String>) value : List.of();
    }

    private static String describe(Setting<?> setting, Object value) {
        if (value instanceof List<?> list && setting.type() != SettingType.SECRET) {
            return list.size() + (list.size() == 1 ? " item" : " items");
        }
        return shorten(setting.display(value), 60);
    }

    static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    static List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }
        if (lines.size() > maxLines) {
            List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            cut.set(maxLines - 1, cut.get(maxLines - 1) + " ...");
            return cut;
        }
        return lines;
    }

    static String cycle(Setting<?> setting, String current, int dir) {
        List<String> options = setting.allowedValues();
        int at = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(current)) {
                at = i;
            }
        }
        int next = at < 0 ? 0 : Math.floorMod(at + dir, options.size());
        return options.get(next);
    }

    // ------------------------------------------------------------------------------------------
    // Number steps
    // ------------------------------------------------------------------------------------------

    /** The step of one click: INT 1; DOUBLE 1, 0.1 or 0.01 by the size of the range; DURATION 1s, 1m or 10m by the size. */
    static BigDecimal stepSize(Setting<?> setting) {
        Double min = setting.min();
        Double max = setting.max();
        switch (setting.type()) {
            case INT:
                return BigDecimal.ONE;
            case DOUBLE: {
                double span = min != null && max != null ? max - min : Math.abs(((Number) setting.defaultValue()).doubleValue());
                return span <= 1 ? new BigDecimal("0.01") : span <= 10 ? new BigDecimal("0.1") : BigDecimal.ONE;
            }
            default: {
                double ref = max != null ? max : Math.max(1, ((Duration) setting.defaultValue()).toMillis() / 1000.0);
                return BigDecimal.valueOf(ref <= 120 ? 1000 : ref <= 7200 ? 60_000 : 600_000);
            }
        }
    }

    private static String stepText(Setting<?> setting) {
        BigDecimal step = stepSize(setting);
        return setting.type() == SettingType.DURATION ? DurationText.format(Duration.ofMillis(step.longValue())) : step.stripTrailingZeros().toPlainString();
    }

    /** Value after {@code dir * mult} steps, clamped to the setting's limits. */
    static Object step(Setting<?> setting, Object current, int dir, int mult) {
        BigDecimal delta = stepSize(setting).multiply(BigDecimal.valueOf((long) dir * mult));
        Double min = setting.min();
        Double max = setting.max();
        switch (setting.type()) {
            case INT: {
                BigDecimal next = BigDecimal.valueOf(((Number) current).longValue()).add(delta);
                next = clamp(next, min, max);
                next = next.max(BigDecimal.valueOf(Integer.MIN_VALUE)).min(BigDecimal.valueOf(Integer.MAX_VALUE));
                return next.intValue();
            }
            case DOUBLE: {
                BigDecimal next = BigDecimal.valueOf(((Number) current).doubleValue()).add(delta);
                return clamp(next, min, max).setScale(6, RoundingMode.HALF_UP).doubleValue();
            }
            default: {
                BigDecimal secs = BigDecimal.valueOf(((Duration) current).toMillis()).add(delta).divide(BigDecimal.valueOf(1000));
                secs = clamp(secs, min == null ? Double.valueOf(0) : min, max);
                return Duration.ofMillis(secs.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).longValue());
            }
        }
    }

    private static BigDecimal clamp(BigDecimal v, Double min, Double max) {
        if (min != null && v.compareTo(BigDecimal.valueOf(min)) < 0) {
            return BigDecimal.valueOf(min);
        }
        if (max != null && v.compareTo(BigDecimal.valueOf(max)) > 0) {
            return BigDecimal.valueOf(max);
        }
        return v;
    }
}
