package com.jason.plugins.moderra.engine;

import com.jason.plugins.moderra.PunishmentAction;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Feedback sounds ({@code config.yml}, section {@code sounds}): menu clicks, warnings, staff alerts, punishments and
 * mutes. Each sound is one line {@code "<sound> [volume] [pitch]"} (any name from /playsound); an empty line turns
 * that one sound off, {@code sounds.enabled: false} turns all of them off.
 */
public final class SoundSettings {
    private static final String CATEGORY = "Sounds";
    private static final String FORMAT = "\nFormat: <sound> [volume] [pitch], for example \"block.note_block.pling 1.0 1.5\" (any name from\n"
        + "/playsound). Empty = no sound.";

    public static final Setting<Boolean> ENABLED = Setting.bool("sounds.enabled", true)
        .file(CoreSettings.CONFIG).category(CATEGORY).title("Sounds on")
        .description("Master switch for every Moderra feedback sound below (punishment sounds are separate).").build();
    public static final Setting<String> GUI_CLICK = sound("sounds.gui-click", "ui.button.click 0.5 1.2", "Menu: click",
        "Played when a menu button is clicked.");
    public static final Setting<String> GUI_TOGGLE_ON = sound("sounds.gui-toggle-on", "block.note_block.pling 0.7 1.6", "Menu: switched on",
        "Played when an on/off setting is switched on (lime).");
    public static final Setting<String> GUI_TOGGLE_OFF = sound("sounds.gui-toggle-off", "block.note_block.bass 0.7 0.8", "Menu: switched off",
        "Played when an on/off setting is switched off (red).");
    public static final Setting<String> GUI_SAVE = sound("sounds.gui-save", "entity.player.levelup 0.6 1.4", "Menu: saved",
        "Played when menu changes are saved.");
    public static final Setting<String> GUI_ERROR = sound("sounds.gui-error", "entity.villager.no 0.7 1.0", "Menu: error",
        "Played when a menu action is refused (invalid value, no permission).");
    public static final Setting<String> WARNING = sound("sounds.warning", "block.note_block.bell 1.0 0.7", "Player warned",
        "Played to a player who gets a warning (blocked message, not punished yet).");
    public static final Setting<String> PUNISHED = sound("sounds.punished", "entity.elder_guardian.curse 0.6 1.2", "Player punished",
        "Played to a player who is punished or reaches a ladder step.");
    public static final Setting<String> STAFF_ALERT = sound("sounds.staff-alert", "block.note_block.chime 0.8 1.8", "Staff alert",
        "Played to staff (moderra.notify) together with a violation message.");
    public static final Setting<String> MUTED = sound("sounds.muted", "block.anvil.land 0.4 1.6", "Muted message blocked",
        "Played to a muted player whose message was blocked.");

    private SoundSettings() {
    }

    private static Setting<String> sound(String key, String def, String title, String description) {
        return Setting.string(key, def).file(CoreSettings.CONFIG).category(CATEGORY).title(title).description(description + FORMAT).build();
    }

    public static void register(SettingsRegistry r) {
        for (Setting<?> s : List.of(ENABLED, GUI_CLICK, GUI_TOGGLE_ON, GUI_TOGGLE_OFF, GUI_SAVE, GUI_ERROR, WARNING, PUNISHED,
            STAFF_ALERT, MUTED)) {
            r.register(s);
        }
    }

    /** Plays the configured sound to the player (no-op when off, empty or the player is offline). Never throws. */
    public static void play(Engine engine, UUID player, Setting<String> which) {
        try {
            if (engine == null || player == null || !Boolean.TRUE.equals(engine.value(ENABLED))) {
                return;
            }
            PunishmentAction action = parse(which.key(), engine.value(which));
            if (action != null) {
                engine.platform().applyAction(player, action);
            }
        } catch (RuntimeException ignored) {
            // a feedback sound must never break moderation
        }
    }

    /** {@code "name [volume] [pitch]"} as a sound action, or null when empty or malformed. */
    static PunishmentAction parse(String id, String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        List<String> args = new ArrayList<>(List.of(parts[0], "1.0", "1.0"));
        for (int i = 1; i < Math.min(parts.length, 3); i++) {
            try {
                Float.parseFloat(parts[i]);
                args.set(i, parts[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return new PunishmentAction(true, "sound", id, args);
    }
}
