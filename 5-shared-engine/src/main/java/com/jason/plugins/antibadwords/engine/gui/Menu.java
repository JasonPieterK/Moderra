package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.DurationText;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
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
    public static final String PERMISSION = "antibadwords.gui";

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
        if (state.hasUnsavedChanges() && state.current().kind() != MenuState.Kind.CONFIRM) {
            state.push(new MenuState.View(MenuState.Kind.CONFIRM, "", 0));
            return MenuAction.refresh();
        }
        int lost = state.stagedCount();
        state.discard();
        return MenuAction.close(lost > 0 ? "Unsaved changes were discarded." : null);
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
        TextRequest request = state.pendingText();
        if (request == null) {
            return MenuAction.none();
        }
        Setting<?> setting = state.access().registry().get(request.key());
        if (setting == null) {
            state.setPending(null);
            return MenuAction.error("That setting no longer exists.");
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
                    return MenuAction.error("Nothing typed. Type the new secret or cancel (nothing was changed).");
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
        return paged(state, view, "AntiBadWords", entries, false);
    }

    private static List<Setting<?>> settingsOf(MenuState state, String category) {
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
        return paged(state, view, "AntiBadWords: " + view.target(), entries, false);
    }

    private static Page listPage(MenuState state, MenuState.View view) {
        Setting<?> setting = state.access().registry().get(view.target());
        if (setting == null) {
            return paged(state, view, "AntiBadWords", List.of(), false);
        }
        List<String> items = listValue(state, setting);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            String item = items.get(i);
            entries.add(new Entry(Icons.LIST_ITEM, shorten(item, 60), List.of("Click to remove this item"), false, (s, t) -> {
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
                return MenuAction.close("Changes discarded.");
            }));
        slots.put(Page.BACK, new Entry(Icons.BACK, "Keep editing", List.of("Go back to the menu"), false, (s, t) -> {
            s.pop();
            return MenuAction.refresh();
        }));
        return new Page("Unsaved changes", slots, 0, 1);
    }

    /** Slices the entries into pages of 45 and adds the navigation row. */
    private static Page paged(MenuState state, MenuState.View view, String title, List<Entry> entries, boolean listView) {
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
            return MenuAction.close("Changes discarded.");
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

    private static Entry settingEntry(MenuState state, Setting<?> setting) {
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
        return new Entry(icon, setting.title(), lore, staged, handler);
    }

    private static MenuAction changed(boolean changed) {
        return changed ? MenuAction.refresh() : MenuAction.none();
    }

    private static MenuAction handleReset(MenuState state, Setting<?> setting, ClickType type) {
        return type.isReset() ? reset(state, setting) : MenuAction.none();
    }

    private static MenuAction reset(MenuState state, Setting<?> setting) {
        return changed(state.stage(setting, setting.defaultValue()));
    }

    private static MenuAction askText(MenuState state, Setting<?> setting, TextRequest.Purpose purpose) {
        boolean secret = setting.type() == SettingType.SECRET;
        String prompt = purpose == TextRequest.Purpose.ADD_ITEM ? "Type the new item for " + setting.title() + "."
            : "Type the new value for " + setting.title() + (secret ? " (it will not be shown)." : ".");
        TextRequest request = new TextRequest(setting.key(), purpose, prompt, setting.constraintText(), secret);
        state.setPending(request);
        return MenuAction.text(request);
    }

    private static MenuAction save(MenuState state) {
        if (state.staged().isEmpty()) {
            return MenuAction.close(null);
        }
        Map<Setting<?>, Object> changes = new LinkedHashMap<>(state.staged());
        for (Map.Entry<Setting<?>, Object> e : changes.entrySet()) {
            Setting<?> setting = e.getKey();
            try {
                setting.require(rawYaml(setting, e.getValue()));
            } catch (IllegalArgumentException ex) {
                return MenuAction.error("Not saved: " + (setting.type() == SettingType.SECRET ? setting.key() + " is not valid" : ex.getMessage()));
            }
        }
        try {
            state.access().save(changes);
        } catch (IOException | RuntimeException ex) {
            return MenuAction.error("Not saved: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                + " (your changes are kept)");
        }
        int n = changes.size();
        state.discard();
        return MenuAction.saved("Saved " + n + " setting" + (n == 1 ? "" : "s") + " and reloaded.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object rawYaml(Setting<?> setting, Object value) {
        return ((Setting) setting).toYaml(value);
    }

    // ------------------------------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<String> listValue(MenuState state, Setting<?> setting) {
        Object value = state.value(setting);
        return value instanceof List<?> ? (List<String>) value : List.of();
    }

    private static String describe(Setting<?> setting, Object value) {
        if (value instanceof List<?> list && setting.type() != SettingType.SECRET) {
            return list.size() + (list.size() == 1 ? " item" : " items");
        }
        return shorten(setting.display(value), 60);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static List<String> wrap(String text, int width, int maxLines) {
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

    private static String cycle(Setting<?> setting, String current, int dir) {
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
