package com.jason.plugins.moderra.engine.gui;

import com.jason.plugins.moderra.engine.SoundSettings;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.filters.SpamSettings;
import com.jason.plugins.moderra.engine.moderation.LadderSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ready-made setups so an admin does not have to configure everything alone. A click STAGES the preset's values (like
 * any other menu change): nothing is written until "Done" is clicked, and "Cancel" throws it away again.
 */
final class Presets {
    private Presets() {
    }

    /** One preset: its id, icon, name, what it does, which settings it sets and (optional) the ladder it puts in. */
    record Preset(String id, String icon, String name, List<String> about, Map<Setting<?>, Object> values,
                  Map<String, Object> ladder) {
    }

    static final List<Preset> ALL = build();

    private static List<Preset> build() {
        List<Preset> out = new ArrayList<>();
        // ---- punishment ladders ----
        out.add(ladder("ladder_soft", "Ladder: Friendly", List.of("For small, young or casual servers.",
                "2 strikes: warning", "4: mute 5m   6: mute 30m", "8: kick, then start again"),
            step("warning", 2, "once", "&e&lWarning. &7Please keep the chat friendly, {player}.", List.of("warn")),
            step("short-mute", 4, "once", "&c&lMuted. &7You cannot write for &f{duration}&7.", List.of("mute 5m {reason}")),
            step("long-mute", 6, "once", "&c&lMuted. &7You cannot write for &f{duration}&7.", List.of("mute 30m {reason}")),
            step("kick", 8, "once", "", List.of("kick Watch your language", "reset-strikes"))));
        out.add(ladder("ladder_standard", "Ladder: Standard", List.of("A balanced setup for most servers.",
                "2 strikes: warning   3: mute 10m", "5: mute 1h   7: kick", "10: tempban 1d (again every 5)"),
            step("warning", 2, "once", "&e&lWarning. &7The next violation causes a mute.", List.of("warn")),
            step("mute-10m", 3, "once", "&c&lMuted. &7You cannot write for &f{duration}&7.",
                List.of("mute 10m {reason}", "sound entity.villager.no 1 1")),
            step("mute-1h", 5, "once", "&c&lMuted. &7You cannot write for &f{duration}&7.", List.of("mute 1h {reason}")),
            step("kick", 7, "once", "", List.of("kick Watch your language")),
            step("tempban", 10, 5, "", List.of("tempban 1d {reason}"))));
        out.add(ladder("ladder_strict", "Ladder: Strict", List.of("Zero tolerance (competitive, family-friendly).",
                "1 strike: mute 30m   2: kick", "3: tempban 3d   5: permanent ban"),
            step("mute", 1, "once", "&c&lMuted. &7You cannot write for &f{duration}&7.", List.of("mute 30m {reason}")),
            step("kick", 2, "once", "", List.of("kick Language that is not permitted")),
            step("tempban", 3, "once", "", List.of("tempban 3d {reason}")),
            step("ban", 5, "once", "", List.of("ban {reason}"))));
        out.add(ladder("ladder_warn", "Ladder: Warnings only", List.of("Never punishes: blocks the message", "and warns the player every time."),
            step("warning", 1, 1, "&e&lWarning. &7That word is not allowed here.", List.of("warn"))));

        // ---- filter strength ----
        out.add(values("filter_relaxed", "Filter: Relaxed", List.of("Only the worst words, chat only.", "Few false positives."),
            Map.of(CoreSettings.PROTECTION_LEVEL, 1, CoreSettings.PARTIAL_MATCH, false, CoreSettings.DETECT_SPACED, false,
                CoreSettings.SCAN_SIGNS, false, CoreSettings.SCAN_BOOKS, false, CoreSettings.SCAN_ANVIL, false)));
        out.add(values("filter_standard", "Filter: Standard", List.of("Common bad words in chat, signs,", "books and anvils."),
            Map.of(CoreSettings.PROTECTION_LEVEL, 2, CoreSettings.PARTIAL_MATCH, false, CoreSettings.DETECT_SPACED, true,
                CoreSettings.SCAN_SIGNS, true, CoreSettings.SCAN_BOOKS, true, CoreSettings.SCAN_ANVIL, true)));
        out.add(values("filter_strict", "Filter: Strict", List.of("Every word level, spaced letters,", "words hidden inside other words."),
            Map.of(CoreSettings.PROTECTION_LEVEL, 3, CoreSettings.PARTIAL_MATCH, true, CoreSettings.DETECT_SPACED, true,
                CoreSettings.SCAN_SIGNS, true, CoreSettings.SCAN_BOOKS, true, CoreSettings.SCAN_ANVIL, true)));

        // ---- word lists ----
        out.add(values("words_en", "Words: English", List.of("English word list + your custom list."),
            Map.of(CoreSettings.ENABLED_LANGUAGES, List.of("en", "custom"))));
        out.add(values("words_europe", "Words: Europe", List.of("English, Spanish, French, German, Italian,",
                "Dutch, Portuguese, Polish + custom."),
            Map.of(CoreSettings.ENABLED_LANGUAGES, List.of("en", "es", "fr", "de", "it", "nl", "pt-pt", "pl", "custom"))));
        out.add(values("words_americas", "Words: Americas", List.of("English, Spanish, Brazilian Portuguese,", "French + custom."),
            Map.of(CoreSettings.ENABLED_LANGUAGES, List.of("en", "es", "pt-br", "fr", "custom"))));
        out.add(values("words_asia", "Words: Asia", List.of("English, Chinese, Japanese, Korean, Hindi,",
                "Indonesian, Filipino, Thai, Vietnamese + custom."),
            Map.of(CoreSettings.ENABLED_LANGUAGES, List.of("en", "zh", "zh-tw", "ja", "ko", "hi", "id", "fil", "th", "vi", "custom"))));

        // ---- spam and sounds ----
        out.add(values("spam_on", "Spam filter: On", List.of("Switches the spam filter on", "(flood, repeats, caps)."),
            Map.of(SpamSettings.ENABLED, true)));
        out.add(values("spam_off", "Spam filter: Off", List.of("Switches the spam filter off."), Map.of(SpamSettings.ENABLED, false)));
        out.add(values("sounds_on", "Sounds: On", List.of("Menu, warning and staff alert sounds."), Map.of(SoundSettings.ENABLED, true)));
        out.add(values("sounds_off", "Sounds: Silent", List.of("No Moderra feedback sounds."), Map.of(SoundSettings.ENABLED, false)));
        return List.copyOf(out);
    }

    private static Preset values(String id, String name, List<String> about, Map<Setting<?>, Object> values) {
        return new Preset(id, Icons.ADD, name, about, values, null);
    }

    @SafeVarargs
    private static Preset ladder(String id, String name, List<String> about, Map.Entry<String, Object>... steps) {
        Map<String, Object> ladder = new LinkedHashMap<>();
        for (Map.Entry<String, Object> step : steps) {
            ladder.put(step.getKey(), step.getValue());
        }
        return new Preset(id, Icons.LADDER, name, about, Map.of(), ladder);
    }

    private static Map.Entry<String, Object> step(String name, int at, Object repeat, String message, List<String> actions) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("at", at);
        step.put("repeat", repeat);
        if (!message.isEmpty()) {
            step.put("message", message);
        }
        step.put("actions", new ArrayList<>(actions));
        return Map.entry(name, step);
    }

    /** The presets page. */
    static Page page(MenuState state, MenuState.View view) {
        List<Entry> entries = new ArrayList<>();
        for (Preset preset : ALL) {
            List<String> lore = new ArrayList<>(preset.about());
            lore.add("");
            lore.add("Click: apply (then Done to save)");
            entries.add(new Entry(preset.icon(), preset.name(), lore, false, (s, t) -> apply(s, preset)).hinted("preset." + preset.id()));
        }
        return Menu.paged(state, view, "Presets", entries, false);
    }

    static MenuAction apply(MenuState state, Preset preset) {
        for (Setting<?> setting : preset.values().keySet()) {
            MenuAction denied = Menu.guard(state, setting.category());
            if (denied != null) {
                return denied;
            }
        }
        if (preset.ladder() != null) {
            MenuAction denied = Menu.guard(state, LadderSettings.STEPS.category());
            if (denied != null) {
                return denied;
            }
            state.stageSection(LadderSettings.STEPS, preset.ladder());
        }
        preset.values().forEach((setting, value) -> {
            if (state.access().registry().get(setting.key()) != null) {
                state.stage(setting, value);
            }
        });
        return MenuAction.refresh();
    }
}
