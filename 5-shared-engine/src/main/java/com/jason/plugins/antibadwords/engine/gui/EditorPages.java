package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.engine.config.ConfigEntry;
import com.jason.plugins.antibadwords.engine.config.ConfigProblem;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
import com.jason.plugins.antibadwords.engine.config.WorldProfiles;
import com.jason.plugins.antibadwords.engine.moderation.Ladder;
import com.jason.plugins.antibadwords.engine.moderation.LadderSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The editors for the free-form sections: punishment ladder steps (at, repeat, message, broadcast, final command and the
 * list of actions), world profiles (which settings differ) and the world rules (which world uses which profile).
 *
 * <p>Everything is staged in the {@link MenuState} (private copies of the raw sections) and written with the other
 * staged changes when the viewer clicks Done. A section is written as a whole by the comment-preserving editor: the
 * comments inside that section are replaced, comments elsewhere in the file stay. Before writing, the staged
 * content is read back with the real parsers ({@link Ladder#parse}, {@link WorldProfiles#parse}); problems stop the
 * save and are shown. Edit rights: the category of the section ("Ladder", "Worlds") is checked on every click.
 */
final class EditorPages {
    private static final String SEP = "\n";
    private static final Pattern STEP_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern PROFILE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private EditorPages() {
    }

    private static String ladderCategory() {
        return LadderSettings.STEPS.category();
    }

    private static String worldsCategory() {
        return WorldProfiles.PROFILES.category();
    }

    private static MenuAction open(MenuState s, MenuState.Kind kind, String target) {
        s.push(new MenuState.View(kind, target, 0));
        return MenuAction.refresh();
    }

    private static Entry info(String icon, String name, List<String> lore) {
        return new Entry(icon, name, lore, false, (s, t) -> MenuAction.none());
    }

    private static String viewOnlyNote(MenuState state, String category) {
        return state.viewer() != null && !Menu.mayEdit(state, category) ? "You may not change this (permission " + Menu.editNode(category) + ")" : null;
    }

    private static List<String> withNote(MenuState state, String category, List<String> lore) {
        String note = viewOnlyNote(state, category);
        if (note == null) {
            return lore;
        }
        List<String> out = new ArrayList<>(lore);
        out.add(note);
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Ladder
    // ------------------------------------------------------------------------------------------

    static Page ladder(MenuState state, MenuState.View view) {
        Map<String, Object> steps = state.sectionValue(LadderSettings.STEPS);
        Map<String, Object> loaded = state.access().section(LadderSettings.STEPS);
        List<String> names = new ArrayList<>(steps.keySet());
        names.sort(Comparator.comparingInt(n -> steps.get(n) instanceof Map<?, ?> m ? Sections.intOf(m.get("at"), Integer.MAX_VALUE) : Integer.MAX_VALUE));
        List<Entry> entries = new ArrayList<>();
        if (steps.isEmpty()) {
            entries.add(info(Icons.LADDER, "No ladder steps", withNote(state, ladderCategory(), List.of("The classic flat punishment is used.",
                "Click \"Add step\" to start a ladder.", "With steps, the steps decide instead (see /abw ladder)."))));
        }
        for (String name : names) {
            Object raw = steps.get(name);
            boolean changed = !java.util.Objects.equals(raw, loaded.get(name));
            if (!(raw instanceof Map<?, ?> map)) {
                entries.add(new Entry(Icons.WARNING, name, List.of("Not a step (it must be a section with at: and actions:)",
                    "It is ignored. Shift-right click: delete it"), changed, (s, t) -> t == ClickType.SHIFT_RIGHT ? removeStep(s, name) : MenuAction.none()));
                continue;
            }
            Map<String, Object> step = Sections.strings(map);
            List<String> lore = new ArrayList<>();
            int repeat = Sections.repeatOf(step.get("repeat"));
            lore.add("At " + Sections.intOf(step.get("at"), 0) + " strikes" + (repeat > 0 ? ", again every " + repeat : ", once"));
            for (Object action : Sections.actionsOf(step)) {
                lore.add("- " + Menu.shorten(String.valueOf(action), 50));
            }
            String message = String.valueOf(step.getOrDefault("message", ""));
            if (!message.isEmpty()) {
                lore.add("Message: " + Menu.shorten(message, 45));
            }
            lore.add("Click: edit this step");
            lore.add("Shift-right click: delete this step");
            entries.add(new Entry(Icons.LADDER, name, withNote(state, ladderCategory(), lore), changed, (s, t) -> {
                if (t == ClickType.SHIFT_RIGHT) {
                    return removeStep(s, name);
                }
                return t.isLeft() || t.isRight() ? open(s, MenuState.Kind.STEP, name) : MenuAction.none();
            }));
        }
        Map<Integer, Entry> extra = new TreeMap<>();
        extra.put(Page.ADD, new Entry(Icons.ADD, "Add step", List.of("Click, then type a name for the new step"), false,
            (s, t) -> {
                MenuAction denied = Menu.guard(s, ladderCategory());
                return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.ADD_STEP, "", "Type a name for the new ladder step (letters, digits, - and _).",
                    "for example first-warning");
            }));
        return Menu.paged(state, view, "Punishment ladder", entries, false, extra);
    }

    private static MenuAction removeStep(MenuState state, String name) {
        MenuAction denied = Menu.guard(state, ladderCategory());
        if (denied != null) {
            return denied;
        }
        Map<String, Object> steps = state.sectionValue(LadderSettings.STEPS);
        if (steps.remove(name) != null) {
            state.stageSection(LadderSettings.STEPS, steps);
        }
        return MenuAction.refresh();
    }

    /** Runs {@code change} on a private copy of one step and stages the result. */
    private static MenuAction mutateStep(MenuState state, String name, Consumer<Map<String, Object>> change) {
        MenuAction denied = Menu.guard(state, ladderCategory());
        if (denied != null) {
            return denied;
        }
        Map<String, Object> steps = state.sectionValue(LadderSettings.STEPS);
        if (!(steps.get(name) instanceof Map<?, ?> raw)) {
            return MenuAction.error("That step no longer exists.");
        }
        Map<String, Object> step = Sections.strings(raw);
        change.accept(step);
        steps.put(name, step);
        state.stageSection(LadderSettings.STEPS, steps);
        return MenuAction.refresh();
    }

    static Page step(MenuState state, MenuState.View view) {
        String name = view.target();
        Map<String, Object> steps = state.sectionValue(LadderSettings.STEPS);
        if (!(steps.get(name) instanceof Map<?, ?> raw)) {
            return Menu.paged(state, view, "Step: " + Menu.shorten(name, 24),
                List.of(info(Icons.WARNING, "This step no longer exists", List.of("Go back"))), false);
        }
        Map<String, Object> step = Sections.strings(raw);
        int at = Sections.intOf(step.get("at"), 1);
        int repeat = Sections.repeatOf(step.get("repeat"));
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(Icons.NUMBER, "At: " + at + " strikes", withNote(state, ladderCategory(), List.of("The step happens when a player reaches this many strikes.",
            "Left click: +1   Right click: -1", "Shift: x10")), false, (s, t) -> {
            if (!t.isLeft() && !t.isRight()) {
                return MenuAction.none();
            }
            int delta = (t.isLeft() ? 1 : -1) * (t.isShift() ? 10 : 1);
            return mutateStep(s, name, m -> m.put("at", (int) Math.max(1, Math.min(1_000_000, (long) Sections.intOf(m.get("at"), 1) + delta))));
        }));
        entries.add(new Entry(Icons.NUMBER, repeat > 0 ? "Repeat: every " + repeat + " strikes" : "Repeat: once", withNote(state, ladderCategory(), List.of(
            "once = only at the number above; a number N = again every N strikes after it.",
            "Left click: +1   Right click: -1 (0 = once)", "Shift: x5   Middle click or drop: once")), false, (s, t) -> {
            if (!t.isLeft() && !t.isRight() && !t.isReset()) {
                return MenuAction.none();
            }
            return mutateStep(s, name, m -> {
                int now = Sections.repeatOf(m.get("repeat"));
                int next = t.isReset() ? 0 : (int) Math.max(0, Math.min(1_000_000, (long) now + (t.isLeft() ? 1 : -1) * (t.isShift() ? 5 : 1)));
                m.put("repeat", next == 0 ? "once" : (Object) next);
            });
        }));
        entries.add(textEntry(state, name, step, "message", "Message to the player"));
        entries.add(textEntry(state, name, step, "broadcast", "Broadcast to everyone"));
        entries.add(textEntry(state, name, step, "final-command", "Final console command"));
        entries.add(new Entry(Icons.CANCEL, "Delete this step", withNote(state, ladderCategory(), List.of("Removes the step (still needs Done to be saved)")), false, (s, t) -> {
            if (!t.isLeft()) {
                return MenuAction.none();
            }
            MenuAction result = removeStep(s, name);
            if (result.kind() == MenuAction.Kind.REFRESH) {
                s.pop();
            }
            return result;
        }));
        List<Object> actions = Sections.actionsOf(step);
        for (int i = 0; i < actions.size(); i++) {
            int index = i;
            String text = String.valueOf(actions.get(i));
            entries.add(new Entry(Icons.LIST_ITEM, Menu.shorten((i + 1) + ". " + text, 60), withNote(state, ladderCategory(), List.of(
                "Click: change this action", "Shift-left click: move up", "Shift-right click, middle click or drop: remove")), false, (s, t) -> {
                if (t == ClickType.SHIFT_LEFT) {
                    return mutateStep(s, name, m -> {
                        List<Object> list = Sections.actionsOf(m);
                        if (index > 0 && index < list.size()) {
                            list.add(index - 1, list.remove(index));
                            m.put("actions", list);
                        }
                    });
                }
                if (t == ClickType.SHIFT_RIGHT || t.isReset()) {
                    return mutateStep(s, name, m -> {
                        List<Object> list = Sections.actionsOf(m);
                        if (index < list.size()) {
                            list.remove(index);
                            m.put("actions", list);
                        }
                    });
                }
                if (t.isLeft() || t.isRight()) {
                    MenuAction denied = Menu.guard(s, ladderCategory());
                    return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.EDIT_ACTION, name + SEP + index,
                        "Type the new text of the action (it replaces: " + Menu.shorten(text, 40) + ").", ACTION_HINT);
                }
                return MenuAction.none();
            }));
        }
        Map<Integer, Entry> extra = new TreeMap<>();
        extra.put(Page.ADD, new Entry(Icons.ADD, "Add action", withNote(state, ladderCategory(), List.of("Click, then type the action", "for example: mute 10m Bad language")),
            false, (s, t) -> {
                MenuAction denied = Menu.guard(s, ladderCategory());
                return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.ADD_ACTION, name, "Type the new action.", ACTION_HINT);
            }));
        return Menu.paged(state, view, "Step: " + Menu.shorten(name, 24), entries, false, extra);
    }

    private static final String ACTION_HINT = "warn [text] | mute <time|perm> [reason] | kick [reason] | ban [reason] | command <cmd> | effect/attribute/sound <3 values> | punishments | reset-strikes";

    private static Entry textEntry(MenuState state, String step, Map<String, Object> values, String field, String title) {
        String current = String.valueOf(values.getOrDefault(field, ""));
        List<String> lore = new ArrayList<>();
        lore.add(current.isEmpty() ? "(not set)" : Menu.shorten(current, 50));
        lore.add("Click, then type the new text. Type - to clear it.");
        lore.add("Placeholders: {player} {count} {threshold} {reason} {duration} ...");
        return new Entry(Icons.TEXT, title, withNote(state, ladderCategory(), lore), false, (s, t) -> {
            if (!t.isLeft() && !t.isRight()) {
                return MenuAction.none();
            }
            MenuAction denied = Menu.guard(s, ladderCategory());
            return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.SET_STEP_TEXT, step + SEP + field,
                "Type the new " + title.toLowerCase(Locale.ROOT) + " (colours: & codes). Type - to clear it.", "any text");
        });
    }

    // ------------------------------------------------------------------------------------------
    // Profiles
    // ------------------------------------------------------------------------------------------

    private static List<String> profileNames(MenuState state) {
        List<String> names = new ArrayList<>(state.sectionValue(WorldProfiles.PROFILES).keySet());
        if (names.stream().noneMatch(WorldProfiles.DEFAULT::equalsIgnoreCase)) {
            names.add(0, WorldProfiles.DEFAULT);
        }
        return names;
    }

    private static Map<String, Object> flat(MenuState state, Object rawProfile) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (rawProfile instanceof Map<?, ?> map) {
            WorldProfiles.flatten("", Sections.strings(map), state.access().registry(), out);
        }
        return out;
    }

    private static int rulesUsing(MenuState state, String profile) {
        int n = 0;
        for (Object target : state.sectionValue(WorldProfiles.WORLDS).values()) {
            if (String.valueOf(target).trim().equalsIgnoreCase(profile)) {
                n++;
            }
        }
        return n;
    }

    static Page profiles(MenuState state, MenuState.View view) {
        Map<String, Object> profiles = state.sectionValue(WorldProfiles.PROFILES);
        Map<String, Object> loaded = state.access().section(WorldProfiles.PROFILES);
        String def = String.valueOf(state.value(WorldProfiles.DEFAULT_PROFILE)).trim().toLowerCase(Locale.ROOT);
        List<Entry> entries = new ArrayList<>();
        for (String name : profileNames(state)) {
            Map<String, Object> overrides = flat(state, profiles.get(name));
            List<String> lore = new ArrayList<>();
            boolean isDefault = name.equalsIgnoreCase(def);
            lore.add(isDefault ? "Default profile (used when no rule matches)" : "Used by world rules and permissions");
            lore.add(overrides.isEmpty() ? "No differences from the normal files" : overrides.size() + " setting" + (overrides.size() == 1 ? "" : "s") + " differ");
            int rules = rulesUsing(state, name);
            lore.add(rules + " world rule" + (rules == 1 ? "" : "s") + " use it");
            lore.add("Click: edit this profile");
            if (!name.equalsIgnoreCase(WorldProfiles.DEFAULT)) {
                lore.add("Shift-right click: delete this profile");
            }
            boolean changed = !java.util.Objects.equals(profiles.get(name), loaded.get(name));
            entries.add(new Entry(Icons.PROFILE, name, withNote(state, worldsCategory(), lore), changed || isDefault, (s, t) -> {
                if (t == ClickType.SHIFT_RIGHT) {
                    return removeProfile(s, name);
                }
                return t.isLeft() || t.isRight() ? open(s, MenuState.Kind.PROFILE, name) : MenuAction.none();
            }));
        }
        Map<Integer, Entry> extra = new TreeMap<>();
        extra.put(Page.ADD, new Entry(Icons.ADD, "Add profile", List.of("Click, then type a name for the new profile"), false, (s, t) -> {
            MenuAction denied = Menu.guard(s, worldsCategory());
            return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.ADD_PROFILE, "",
                "Type a name for the new profile (lower-case letters, digits, - and _).", "for example relaxed");
        }));
        return Menu.paged(state, view, "World profiles", entries, false, extra);
    }

    private static MenuAction removeProfile(MenuState state, String name) {
        MenuAction denied = Menu.guard(state, worldsCategory());
        if (denied != null) {
            return denied;
        }
        if (name.equalsIgnoreCase(WorldProfiles.DEFAULT)) {
            return MenuAction.error("The profile \"default\" cannot be deleted.");
        }
        if (name.equalsIgnoreCase(String.valueOf(state.value(WorldProfiles.DEFAULT_PROFILE)).trim())) {
            return MenuAction.error("That is the default profile (setting default-profile). Choose another default first.");
        }
        int rules = rulesUsing(state, name);
        if (rules > 0) {
            return MenuAction.error("World rules still use that profile (" + rules + "). Remove or change them first.");
        }
        Map<String, Object> profiles = state.sectionValue(WorldProfiles.PROFILES);
        if (profiles.remove(name) != null) {
            state.stageSection(WorldProfiles.PROFILES, profiles);
        }
        return MenuAction.refresh();
    }

    private static MenuAction mutateProfile(MenuState state, String profile, Consumer<Map<String, Object>> change) {
        MenuAction denied = Menu.guard(state, worldsCategory());
        if (denied != null) {
            return denied;
        }
        Map<String, Object> profiles = state.sectionValue(WorldProfiles.PROFILES);
        Map<String, Object> overrides = flat(state, profiles.get(profile));
        change.accept(overrides);
        profiles.put(profile, overrides);
        state.stageSection(WorldProfiles.PROFILES, profiles);
        return MenuAction.refresh();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Page profile(MenuState state, MenuState.View view) {
        String name = view.target();
        Map<String, Object> profiles = state.sectionValue(WorldProfiles.PROFILES);
        if (!profiles.containsKey(name) && !name.equalsIgnoreCase(WorldProfiles.DEFAULT)) {
            return Menu.paged(state, view, "Profile: " + Menu.shorten(name, 22),
                List.of(info(Icons.WARNING, "This profile no longer exists", List.of("Go back"))), false);
        }
        Map<String, Object> overrides = flat(state, profiles.get(name));
        List<Entry> entries = new ArrayList<>();
        if (overrides.isEmpty()) {
            entries.add(info(Icons.INFO, "No differences", List.of("This profile uses the normal files.", "Click \"Add setting\" to change one setting here.")));
        }
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            String key = e.getKey();
            ConfigEntry entry = state.access().registry().entry(key);
            List<String> lore = new ArrayList<>();
            if (!(entry instanceof Setting<?> setting) || !entry.overridable()) {
                lore.add(entry == null ? "Unknown setting: it is ignored." : entry instanceof Setting<?> ? "Cannot differ per world: it is ignored."
                    : "A section: edit worlds.yml to change it.");
                lore.add("Middle click, drop or shift-right click: remove");
                entries.add(new Entry(Icons.WARNING, Menu.shorten(key, 50), withNote(state, worldsCategory(), lore), false, (s, t) ->
                    t.isReset() || t == ClickType.SHIFT_RIGHT ? mutateProfile(s, name, m -> m.remove(key)) : MenuAction.none()));
                continue;
            }
            Object value = setting.parse(e.getValue()).value();
            lore.add(setting.title());
            lore.add("Here: " + Menu.shorten(setting.display(value), 50));
            lore.add("Normal value: " + Menu.shorten(setting.display(state.value(setting)), 50));
            switch (setting.type()) {
                case BOOLEAN -> lore.add("Click: switch on/off");
                case INT, DOUBLE, DURATION -> lore.add("Left click: +   Right click: -   Shift: x10");
                case ENUM -> lore.add("Left click: next   Right click: previous");
                default -> lore.add("Click: type a new value");
            }
            lore.add("Middle click, drop or shift-right click: remove (use the normal value)");
            Setting raw = setting;
            entries.add(new Entry(icon(setting), Menu.shorten(key, 50), withNote(state, worldsCategory(), lore), false, (s, t) -> {
                if (t.isReset() || t == ClickType.SHIFT_RIGHT) {
                    return mutateProfile(s, name, m -> m.remove(key));
                }
                if (!t.isLeft() && !t.isRight()) {
                    return MenuAction.none();
                }
                int dir = t.isLeft() ? 1 : -1;
                Object next = switch (raw.type()) {
                    case BOOLEAN -> !Boolean.TRUE.equals(value);
                    case INT, DOUBLE, DURATION -> Menu.step(raw, value, dir, t.isShift() ? 10 : 1);
                    case ENUM -> Menu.cycle(raw, String.valueOf(value), dir);
                    default -> null;
                };
                if (next == null) {
                    MenuAction denied = Menu.guard(s, worldsCategory());
                    return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.SET_OVERRIDE, name + SEP + key,
                        "Type the value of " + key + " in the profile " + name + ".", raw.constraintText());
                }
                return mutateProfile(s, name, m -> m.put(key, raw.toYaml(next)));
            }));
        }
        Map<Integer, Entry> extra = new TreeMap<>();
        extra.put(Page.ADD, new Entry(Icons.ADD, "Add setting", withNote(state, worldsCategory(), List.of("Click, then type: <setting key> <value>",
            "for example: filters.protection-level 1", "Only settings marked \"per world\" in CONFIGURATION.md work")), false, (s, t) -> {
            MenuAction denied = Menu.guard(s, worldsCategory());
            return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.ADD_OVERRIDE, name, "Type the setting key and its value for the profile "
                + name + ".", "<setting key> <value>, for example punishment.enabled false");
        }));
        return Menu.paged(state, view, "Profile: " + Menu.shorten(name, 22), entries, false, extra);
    }

    private static String icon(Setting<?> setting) {
        return switch (setting.type()) {
            case BOOLEAN -> Icons.TOGGLE_ON;
            case INT, DOUBLE -> Icons.NUMBER;
            case DURATION -> Icons.DURATION;
            case ENUM -> Icons.CYCLE;
            case STRING_LIST -> Icons.LIST;
            case SECRET -> Icons.SECRET;
            default -> Icons.TEXT;
        };
    }

    // ------------------------------------------------------------------------------------------
    // World rules
    // ------------------------------------------------------------------------------------------

    static Page rules(MenuState state, MenuState.View view) {
        Map<String, Object> rules = state.sectionValue(WorldProfiles.WORLDS);
        Map<String, Object> loaded = state.access().section(WorldProfiles.WORLDS);
        List<String> profiles = profileNames(state);
        List<String> patterns = new ArrayList<>(rules.keySet());
        List<Entry> entries = new ArrayList<>();
        if (patterns.isEmpty()) {
            entries.add(info(Icons.INFO, "No world rules", List.of("Every world uses the default profile.",
                "Click \"Add rule\" to give a world (or a pattern such as creative_*) a profile.")));
        }
        for (int i = 0; i < patterns.size(); i++) {
            int index = i;
            String pattern = patterns.get(i);
            String target = String.valueOf(rules.get(pattern)).trim();
            List<String> lore = new ArrayList<>();
            lore.add("Profile: " + target + (profiles.stream().anyMatch(target::equalsIgnoreCase) ? "" : " (does not exist!)"));
            lore.add("Rule " + (i + 1) + " of " + patterns.size() + " (the first match wins)");
            lore.add("Left click: next profile   Right click: previous profile");
            lore.add("Shift-left click: move up   Shift-right click: move down");
            lore.add("Middle click or drop: remove the rule");
            boolean changed = !loaded.containsKey(pattern) || !String.valueOf(loaded.get(pattern)).equals(String.valueOf(rules.get(pattern)));
            entries.add(new Entry(Icons.RULE, pattern, withNote(state, worldsCategory(), lore), changed, (s, t) -> {
                if (t == ClickType.OTHER) {
                    return MenuAction.none();
                }
                return mutateRules(s, m -> {
                    List<String> keys = new ArrayList<>(m.keySet());
                    if (!keys.contains(pattern)) {
                        return;
                    }
                    if (t.isReset()) {
                        m.remove(pattern);
                    } else if (t == ClickType.SHIFT_LEFT || t == ClickType.SHIFT_RIGHT) {
                        int at = keys.indexOf(pattern);
                        int to = t == ClickType.SHIFT_LEFT ? at - 1 : at + 1;
                        if (to < 0 || to >= keys.size()) {
                            return;
                        }
                        keys.add(to, keys.remove(at));
                        Map<String, Object> copy = new LinkedHashMap<>(m);
                        m.clear();
                        keys.forEach(k -> m.put(k, copy.get(k)));
                    } else {
                        List<String> names = profileNames(s);
                        int at = -1;
                        for (int n = 0; n < names.size(); n++) {
                            if (names.get(n).equalsIgnoreCase(String.valueOf(m.get(pattern)).trim())) {
                                at = n;
                            }
                        }
                        m.put(pattern, names.get(Math.floorMod(at + (t.isLeft() ? 1 : -1), names.size())));
                    }
                });
            }));
        }
        Map<Integer, Entry> extra = new TreeMap<>();
        extra.put(Page.ADD, new Entry(Icons.ADD, "Add rule", withNote(state, worldsCategory(), List.of("Click, then type: <world name or pattern> <profile>",
            "Known profiles: " + String.join(", ", profiles))), false, (s, t) -> {
            MenuAction denied = Menu.guard(s, worldsCategory());
            return denied != null ? denied : Menu.askText(s, TextRequest.Purpose.ADD_RULE, "", "Type the world and the profile for the new rule.",
                "<world or pattern> <profile>, for example creative_* relaxed (* = any text)");
        }));
        return Menu.paged(state, view, "World rules", entries, false, extra);
    }

    private static MenuAction mutateRules(MenuState state, Consumer<Map<String, Object>> change) {
        MenuAction denied = Menu.guard(state, worldsCategory());
        if (denied != null) {
            return denied;
        }
        Map<String, Object> rules = state.sectionValue(WorldProfiles.WORLDS);
        change.accept(rules);
        state.stageSection(WorldProfiles.WORLDS, rules);
        return MenuAction.refresh();
    }

    // ------------------------------------------------------------------------------------------
    // Text answers
    // ------------------------------------------------------------------------------------------

    /** {@code null} = fine, else what is wrong. */
    static String validate(MenuState state, TextRequest request, String text) {
        String value = text == null ? "" : text.trim();
        String[] key = request.key().split(SEP, 2);
        switch (request.purpose()) {
            case ADD_STEP:
                if (!STEP_NAME.matcher(value).matches()) {
                    return "Use 1 to 32 letters, digits, - or _.";
                }
                return state.sectionValue(LadderSettings.STEPS).keySet().stream().anyMatch(value::equalsIgnoreCase) ? "A step with that name exists." : null;
            case SET_STEP_TEXT:
                return value.isEmpty() ? "Nothing typed. Type the text, or - to clear it." : null;
            case ADD_ACTION:
            case EDIT_ACTION:
                try {
                    Ladder.action(value);
                    return null;
                } catch (IllegalArgumentException ex) {
                    return ex.getMessage();
                }
            case ADD_PROFILE:
                if (!PROFILE_NAME.matcher(value.toLowerCase(Locale.ROOT)).matches()) {
                    return "Use 1 to 32 letters, digits, - or _.";
                }
                return profileNames(state).stream().anyMatch(value::equalsIgnoreCase) ? "A profile with that name exists." : null;
            case ADD_OVERRIDE: {
                String[] parts = value.split("\\s+", 2);
                return parts.length < 2 ? "Type the setting key, a space and the value." : checkOverride(state, parts[0], parts[1]);
            }
            case SET_OVERRIDE:
                return key.length < 2 ? "Nothing is being asked right now." : checkOverride(state, key[1], value);
            case ADD_RULE: {
                String[] parts = splitRule(value);
                if (parts == null) {
                    return "Type the world (or pattern), a space and the profile.";
                }
                return profileNames(state).stream().noneMatch(parts[1]::equalsIgnoreCase) ? "No such profile. Known: " + String.join(", ", profileNames(state)) : null;
            }
            default:
                return null;
        }
    }

    private static String[] splitRule(String value) {
        int cut = value.lastIndexOf(' ');
        if (cut <= 0 || cut == value.length() - 1) {
            return null;
        }
        return new String[] {value.substring(0, cut).trim(), value.substring(cut + 1).trim()};
    }

    private static String checkOverride(MenuState state, String key, String valueText) {
        ConfigEntry entry = state.access().registry().entry(key);
        if (!(entry instanceof Setting<?> setting)) {
            return entry == null ? "No such setting: " + key : key + " is a section; edit worlds.yml for that.";
        }
        if (!setting.overridable()) {
            return key + " cannot be different per world.";
        }
        try {
            parseOverride(setting, valueText);
            return null;
        } catch (IllegalArgumentException ex) {
            return setting.type() == SettingType.SECRET ? "That value is not valid." : ex.getMessage();
        }
    }

    /** The YAML form of a typed value for the setting; lists are typed comma separated. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseOverride(Setting<?> setting, String text) {
        Object raw = text;
        if (setting.type() == SettingType.STRING_LIST) {
            List<String> items = new ArrayList<>();
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    items.add(part.trim());
                }
            }
            raw = items;
        }
        return ((Setting) setting).toYaml(setting.require(raw));
    }

    /** Applies the answer. The caller already checked it with {@link #validate} (it is checked again here). */
    static MenuAction submit(MenuState state, TextRequest request, String text) {
        String value = text == null ? "" : text.trim();
        String problem = validate(state, request, text);
        if (problem != null) {
            return MenuAction.error(problem);
        }
        String[] key = request.key().split(SEP, 2);
        MenuAction result;
        switch (request.purpose()) {
            case ADD_STEP: {
                result = mutateSteps(state, steps -> {
                    int top = 0;
                    for (Object o : steps.values()) {
                        if (o instanceof Map<?, ?> m) {
                            top = Math.max(top, Sections.intOf(m.get("at"), 0));
                        }
                    }
                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("at", top + 1);
                    step.put("repeat", "once");
                    step.put("message", "&eWatch your language, {player}.");
                    step.put("actions", new ArrayList<Object>(List.of("warn")));
                    steps.put(value, step);
                });
                if (result.kind() == MenuAction.Kind.REFRESH) {
                    open(state, MenuState.Kind.STEP, value);
                }
                break;
            }
            case SET_STEP_TEXT:
                result = mutateStep(state, key[0], m -> {
                    if (value.equals("-")) {
                        m.remove(key[1]);
                    } else {
                        m.put(key[1], value);
                    }
                });
                break;
            case ADD_ACTION:
                result = mutateStep(state, key[0], m -> {
                    List<Object> list = Sections.actionsOf(m);
                    list.add(value);
                    m.put("actions", list);
                });
                break;
            case EDIT_ACTION:
                result = mutateStep(state, key[0], m -> {
                    List<Object> list = Sections.actionsOf(m);
                    int index = Sections.intOf(key.length > 1 ? key[1] : null, -1);
                    if (index >= 0 && index < list.size()) {
                        list.set(index, value);
                        m.put("actions", list);
                    }
                });
                break;
            case ADD_PROFILE: {
                String name = value.toLowerCase(Locale.ROOT);
                result = mutateProfiles(state, profiles -> profiles.put(name, new LinkedHashMap<String, Object>()));
                if (result.kind() == MenuAction.Kind.REFRESH) {
                    open(state, MenuState.Kind.PROFILE, name);
                }
                break;
            }
            case ADD_OVERRIDE: {
                String[] parts = value.split("\\s+", 2);
                result = overrideValue(state, request.key(), parts[0], parts[1]);
                break;
            }
            case SET_OVERRIDE:
                result = overrideValue(state, key[0], key[1], value);
                break;
            case ADD_RULE: {
                String[] parts = splitRule(value);
                String profile = profileNames(state).stream().filter(parts[1]::equalsIgnoreCase).findFirst().orElse(parts[1]);
                result = mutateRules(state, rules -> rules.put(parts[0], profile));
                break;
            }
            default:
                return MenuAction.none();
        }
        if (result.kind() == MenuAction.Kind.REFRESH) {
            state.setPending(null);
        }
        return result;
    }

    private static MenuAction mutateSteps(MenuState state, Consumer<Map<String, Object>> change) {
        MenuAction denied = Menu.guard(state, ladderCategory());
        if (denied != null) {
            return denied;
        }
        Map<String, Object> steps = state.sectionValue(LadderSettings.STEPS);
        change.accept(steps);
        state.stageSection(LadderSettings.STEPS, steps);
        return MenuAction.refresh();
    }

    private static MenuAction mutateProfiles(MenuState state, Consumer<Map<String, Object>> change) {
        MenuAction denied = Menu.guard(state, worldsCategory());
        if (denied != null) {
            return denied;
        }
        Map<String, Object> profiles = state.sectionValue(WorldProfiles.PROFILES);
        change.accept(profiles);
        state.stageSection(WorldProfiles.PROFILES, profiles);
        return MenuAction.refresh();
    }

    private static MenuAction overrideValue(MenuState state, String profile, String key, String valueText) {
        Setting<?> setting = (Setting<?>) state.access().registry().entry(key);
        Object yaml = parseOverride(setting, valueText);
        return mutateProfile(state, profile, m -> m.put(key, yaml));
    }

    // ------------------------------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------------------------------

    /** Reads the staged sections with the real parsers; {@code null} when they are fine, else the first problem. */
    static String problem(MenuState state) {
        if (state.isSectionStaged(LadderSettings.STEPS)) {
            List<String> warnings = new ArrayList<>();
            Ladder.parse(state.sectionValue(LadderSettings.STEPS), warnings::add);
            if (!warnings.isEmpty()) {
                return "Ladder: " + warnings.get(0);
            }
        }
        if (state.isSectionStaged(WorldProfiles.PROFILES) || state.isSectionStaged(WorldProfiles.WORLDS)) {
            List<ConfigProblem> problems = new ArrayList<>();
            WorldProfiles.parse(state.sectionValue(WorldProfiles.PROFILES), state.sectionValue(WorldProfiles.WORLDS),
                String.valueOf(state.value(WorldProfiles.DEFAULT_PROFILE)), Boolean.TRUE.equals(state.value(WorldProfiles.USE_PERMISSIONS)),
                state.access().registry(), problems);
            if (!problems.isEmpty()) {
                return "World profiles: " + problems.get(0).toString();
            }
        }
        return null;
    }
}
