package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.io.IOException;
import java.util.Map;

/** What the menu needs from the engine: the registry, live values, and one save. */
public interface ConfigAccess {
    /** The engine behind this access; {@code null} when there is none (then the special pages are not offered). */
    default com.jason.plugins.antibadwords.engine.Engine engine() {
        return null;
    }

    SettingsRegistry registry();

    /** The value as currently loaded (not staged). */
    Object current(Setting<?> setting);

    /** Writes every value (already checked) into the files and reloads once. */
    void save(Map<Setting<?>, Object> changes) throws IOException;

    /** A deep copy of the raw content of a free-form section (ladder steps, profiles, world rules). Empty when unknown. */
    default Map<String, Object> section(Section section) {
        return Map.of();
    }

    /**
     * Writes settings and whole sections (each replaces the section's content, comments inside it are lost) and reloads
     * once. The default refuses sections.
     */
    default void save(Map<Setting<?>, Object> changes, Map<Section, Map<String, Object>> sections) throws IOException {
        if (!sections.isEmpty()) {
            throw new IOException("this menu cannot save sections");
        }
        save(changes);
    }
}
