package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.WordMatcher;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.DurationText;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
import com.jason.plugins.antibadwords.engine.config.WordList;
import com.jason.plugins.antibadwords.engine.config.WordLists;
import com.jason.plugins.antibadwords.engine.config.WorldProfiles;
import com.jason.plugins.antibadwords.engine.moderation.Ladder;
import com.jason.plugins.antibadwords.engine.moderation.MuteService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The pages that are not generated from one category: main-menu status, languages, word browser,
 * mutes and search results (ladder, world profiles and world rules are in {@link EditorPages}). They need an engine ({@link ConfigAccess#engine()}); without one the main
 * menu only shows the categories.
 *
 * <p>Staged (written on Done): language switches and the whitelist (both are settings). Applied at once, because they
 * are not settings: custom words (wordlists/custom.yml, removal needs shift-click) and unmuting a player.
 * Read-only: bundled language word lists (the ladder, profiles and world rules are edited in {@link EditorPages})
 * and the world profiles.
 */
final class SpecialPages {
    private SpecialPages() {
    }

    private static Engine engine(MenuState state) {
        return state.access().engine();
    }

    private static MenuAction open(MenuState s, MenuState.Kind kind, String target) {
        s.push(new MenuState.View(kind, target, 0));
        return MenuAction.refresh();
    }

    private static Entry info(String icon, String name, List<String> lore) {
        return new Entry(icon, name, lore, false, (s, t) -> MenuAction.none());
    }

    // ------------------------------------------------------------------------------------------
    // Main menu: buttons and status
    // ------------------------------------------------------------------------------------------

    static List<Entry> mainExtras(MenuState state) {
        Engine engine = engine(state);
        if (engine == null) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        List<String> on = enabledCodes(state);
        out.add(new Entry(Icons.LANGUAGE, "Language lists", List.of("On: " + (on.isEmpty() ? "none" : String.join(", ", on)),
            "Click to switch languages on or off"), false, (s, t) -> open(s, MenuState.Kind.LANGUAGES, "")));
        out.add(new Entry(Icons.WORD, "Word browser", List.of("Look at the words of a list, edit the custom list", "and the whitelist"), false,
            (s, t) -> open(s, MenuState.Kind.SOURCES, "")));
        out.add(new Entry(Icons.LADDER, "Punishment ladder", List.of(state.sectionValue(com.jason.plugins.antibadwords.engine.moderation.LadderSettings.STEPS).size()
            + " steps", "Click to add, change and remove steps"), false, (s, t) -> open(s, MenuState.Kind.LADDER, "")));
        out.add(new Entry(Icons.MUTE, "Muted players", List.of(engine.mutes().list().size() + " muted now"), false,
            (s, t) -> open(s, MenuState.Kind.MUTES, "")));
        out.add(new Entry(Icons.PROFILE, "World profiles", List.of(engine.profileNames().size() + " profiles", "Click to edit which settings differ"), false,
            (s, t) -> open(s, MenuState.Kind.PROFILES, "")));
        out.add(new Entry(Icons.RULE, "World rules", List.of(state.sectionValue(WorldProfiles.WORLDS).size() + " rules", "Which world uses which profile"), false,
            (s, t) -> open(s, MenuState.Kind.RULES, "")));

        Object level = state.value(CoreSettings.PROTECTION_LEVEL);
        out.add(info(Icons.STATUS, "Protection level: " + level, List.of("1 = mild words only, 3 = everything",
            "Change it in the Languages category")));
        List<String> scan = new ArrayList<>();
        for (Setting<?> setting : state.access().registry().settings()) {
            if (setting.type() == SettingType.BOOLEAN && setting.key().startsWith("filters.scan.")) {
                scan.add((Boolean.TRUE.equals(state.value(setting)) ? "on   " : "off  ") + setting.key().substring("filters.scan.".length()));
            }
        }
        out.add(info(Icons.STATUS, "Scan sources", scan));
        String profile = String.valueOf(state.value(WorldProfiles.DEFAULT_PROFILE));
        out.add(info(Icons.STATUS, "Active profile: " + profile, List.of("The profile used when no world rule matches")));
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Languages
    // ------------------------------------------------------------------------------------------

    /** Codes that are on (staged or loaded), old names such as "english" read as their code. */
    static List<String> enabledCodes(MenuState state) {
        List<String> out = new ArrayList<>();
        for (String name : Menu.listValue(state, CoreSettings.ENABLED_LANGUAGES)) {
            String code = WordLists.canonicalCode(String.valueOf(name));
            if (!out.contains(code)) {
                out.add(code);
            }
        }
        return out;
    }

    static Page languages(MenuState state, MenuState.View view) {
        Engine engine = engine(state);
        int level = (Integer) state.value(CoreSettings.PROTECTION_LEVEL);
        List<String> on = enabledCodes(state);
        List<Entry> entries = new ArrayList<>();
        for (String code : engine.wordLists().codes()) {
            WordList list = engine.wordLists().get(code);
            if (list == null) {
                continue;
            }
            boolean custom = code.equals(WordLists.CUSTOM);
            boolean active = custom || on.contains(code);
            List<String> lore = new ArrayList<>();
            lore.add(list.name() + (list.script().isEmpty() ? "" : " (" + list.script() + ")"));
            lore.add("Words: " + list.size() + " (" + list.words(level).size() + " at protection level " + level + ")");
            StringBuilder tiers = new StringBuilder("By level:");
            for (int i = 0; i < list.tiers().size(); i++) {
                tiers.append(' ').append(i + 1).append('=').append(list.tiers().get(i).size());
            }
            lore.add(tiers.toString());
            lore.add("Review: " + (list.reviewStatus().isEmpty() ? "unknown" : list.reviewStatus()));
            lore.add(active ? "Status: ON" : "Status: off");
            lore.add(custom ? "The custom list is always on" : "Left click: switch " + (active ? "off" : "on"));
            lore.add("Right click: browse the words");
            boolean staged = !custom && state.isStaged(CoreSettings.ENABLED_LANGUAGES) && on.contains(code) != engineOn(state, code);
            entries.add(new Entry(active ? Icons.LANGUAGE : Icons.LANGUAGE_OFF, code, lore, staged, (s, t) -> {
                if (t.isRight()) {
                    return open(s, MenuState.Kind.WORDS, code);
                }
                if (!t.isLeft() || custom) {
                    return MenuAction.none();
                }
                return toggleLanguage(s, code);
            }));
        }
        return Menu.paged(state, view, "Languages", entries, false);
    }

    @SuppressWarnings("unchecked")
    private static List<String> loadedLanguages(MenuState state) {
        return (List<String>) state.access().current(CoreSettings.ENABLED_LANGUAGES);
    }

    private static boolean engineOn(MenuState state, String code) {
        List<String> loaded = new ArrayList<>();
        for (String name : loadedLanguages(state)) {
            loaded.add(WordLists.canonicalCode(String.valueOf(name)));
        }
        return loaded.contains(code);
    }

    private static MenuAction toggleLanguage(MenuState state, String code) {
        MenuAction denied = Menu.guard(state, CoreSettings.ENABLED_LANGUAGES.category());
        if (denied != null) {
            return denied;
        }
        List<String> next = new ArrayList<>();
        boolean was = false;
        for (String name : Menu.listValue(state, CoreSettings.ENABLED_LANGUAGES)) {
            if (WordLists.canonicalCode(String.valueOf(name)).equals(code)) {
                was = true;
            } else {
                next.add(String.valueOf(name));
            }
        }
        if (!was) {
            next.add(code);
        }
        state.stage(CoreSettings.ENABLED_LANGUAGES, List.copyOf(next));
        return MenuAction.refresh();
    }

    // ------------------------------------------------------------------------------------------
    // Word browser
    // ------------------------------------------------------------------------------------------

    static Page sources(MenuState state, MenuState.View view) {
        Engine engine = engine(state);
        List<Entry> entries = new ArrayList<>();
        for (String code : engine.wordLists().codes()) {
            WordList list = engine.wordLists().get(code);
            if (list == null) {
                continue;
            }
            boolean custom = code.equals(WordLists.CUSTOM);
            entries.add(new Entry(Icons.WORD, custom ? "custom (your own words)" : code,
                List.of(list.name(), list.size() + " words", custom ? "Click to browse, add and remove" : "Click to browse (read-only)"),
                false, (s, t) -> open(s, MenuState.Kind.WORDS, code)));
        }
        int whitelist = listSize(state, CoreSettings.WHITELIST);
        entries.add(new Entry(Icons.LIST, "Whitelist", List.of(whitelist + " words that are never blocked", "Click to browse, add and remove"), false,
            (s, t) -> open(s, MenuState.Kind.LIST, CoreSettings.WHITELIST.key())));
        return Menu.paged(state, view, "Word browser", entries, false);
    }

    private static int listSize(MenuState state, Setting<List<String>> setting) {
        return Menu.listValue(state, setting).size();
    }

    /** Every word of a language (all levels, no duplicates) or of the custom list. */
    private static List<String> wordsOf(Engine engine, String code) {
        WordList list = engine.wordLists().get(code);
        if (list == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        list.tiers().forEach(out::addAll);
        return new ArrayList<>(out);
    }

    static Page words(MenuState state, MenuState.View view) {
        Engine engine = engine(state);
        String code = view.target();
        boolean custom = code.equals(WordLists.CUSTOM);
        List<Entry> entries = new ArrayList<>();
        for (String word : wordsOf(engine, code)) {
            if (custom) {
                entries.add(new Entry(Icons.WORD, Menu.shorten(word, 60), List.of("Shift-click: remove this word now", "(applies at once, no Done needed)"),
                    false, (s, t) -> {
                        if (!t.isShift()) {
                            return MenuAction.none();
                        }
                        Engine e = engine(s);
                        return e.removeCustomWord(word) ? MenuAction.refresh() : MenuAction.error("Could not remove that word.");
                    }));
            } else {
                entries.add(info(Icons.WORD, Menu.shorten(word, 60), List.of("Bundled list: read-only",
                    "To allow a word use the whitelist; to add words use the custom list")));
            }
        }
        Map<Integer, Entry> extra = new TreeMap<>();
        if (custom) {
            extra.put(Page.ADD, new Entry(Icons.ADD, "Add word", List.of("Click, then type the new word (applies at once)"), false,
                (s, t) -> Menu.askText(s, TextRequest.Purpose.ADD_CUSTOM_WORD, WordLists.CUSTOM, "Type the new word for the custom list.",
                    "at least 2 letters or digits")));
        }
        return Menu.paged(state, view, "Words: " + code, entries, false, extra);
    }

    static MenuAction submitCustomWord(MenuState state, String text) {
        Engine engine = engine(state);
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            return MenuAction.error("Nothing typed. Type the new word or cancel.");
        }
        if (engine == null || !WordMatcher.isValidEntry(raw)) {
            return MenuAction.error("That is not a valid word (use at least 2 letters or digits).");
        }
        if (!engine.addCustomWord(raw)) {
            return MenuAction.error("Already in the custom list or could not be saved: " + WordMatcher.cleanEntry(raw));
        }
        state.setPending(null);
        return MenuAction.refresh();
    }

    // ------------------------------------------------------------------------------------------
    // Mutes
    // ------------------------------------------------------------------------------------------

    static Page mutes(MenuState state, MenuState.View view) {
        Engine engine = engine(state);
        long now = engine.now();
        List<Entry> entries = new ArrayList<>();
        for (MuteService.Mute mute : engine.mutes().list()) {
            List<String> lore = new ArrayList<>();
            lore.add(mute.permanent() ? "Permanent" : "Remaining: " + DurationText.format(Duration.ofMillis(Math.max(0, mute.remaining(now)))));
            if (!mute.reason().isEmpty()) {
                lore.add("Reason: " + Menu.shorten(mute.reason(), 50));
            }
            lore.add("By: " + mute.by());
            lore.add("Click: unmute (needs " + MuteService.PERMISSION + ")");
            entries.add(new Entry(Icons.MUTE, mute.name(), lore, false, (s, t) -> {
                if (!t.isLeft() && !t.isRight()) {
                    return MenuAction.none();
                }
                CommandSender viewer = s.viewer();
                if (viewer == null || !viewer.has(MuteService.PERMISSION)) {
                    return MenuAction.error("You need the permission " + MuteService.PERMISSION + " to unmute players.");
                }
                engine(s).mutes().unmute(mute.id(), viewer.name());
                return MenuAction.refresh();
            }));
        }
        if (entries.isEmpty()) {
            entries.add(info(Icons.INFO, "Nobody is muted", List.of("Mutes appear here")));
        }
        return Menu.paged(state, view, "Muted players", entries, false);
    }

    // ------------------------------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------------------------------

    static List<Setting<?>> find(MenuState state, String query) {
        String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> setting : state.access().registry().settings()) {
            String haystack = (setting.title() + " " + setting.key() + " " + setting.description() + " " + setting.category())
                .toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String term : terms) {
                all &= haystack.contains(term);
            }
            if (all) {
                out.add(setting);
            }
        }
        return out;
    }

    static MenuAction submitSearch(MenuState state, String text) {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) {
            return MenuAction.error("Nothing typed. Type what to search for or cancel.");
        }
        List<Setting<?>> found = find(state, query);
        if (found.isEmpty()) {
            return MenuAction.error("No setting matches \"" + Menu.shorten(query, 40) + "\". Try another word.");
        }
        state.setPending(null);
        state.home();
        if (found.size() == 1) {
            Setting<?> hit = found.get(0);
            int at = Menu.settingsOf(state, hit.category()).indexOf(hit);
            state.push(new MenuState.View(MenuState.Kind.CATEGORY, hit.category(), Math.max(0, at) / Page.CONTENT_SLOTS));
        } else {
            state.push(new MenuState.View(MenuState.Kind.SEARCH, query, 0));
        }
        return MenuAction.refresh();
    }

    static Page search(MenuState state, MenuState.View view) {
        List<Entry> entries = new ArrayList<>();
        for (Setting<?> setting : find(state, view.target())) {
            entries.add(Menu.settingEntry(state, setting));
        }
        return Menu.paged(state, view, "Search: " + Menu.shorten(view.target(), 30), entries, false);
    }
}
