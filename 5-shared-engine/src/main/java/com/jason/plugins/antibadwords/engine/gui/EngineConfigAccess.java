package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.io.IOException;
import java.util.Map;

/** {@link ConfigAccess} on a real engine: comment-preserving writes through the config store, then one reload. */
public final class EngineConfigAccess implements ConfigAccess {
    private final Engine engine;

    public EngineConfigAccess(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Engine engine() {
        return engine;
    }

    @Override
    public SettingsRegistry registry() {
        return engine.registry();
    }

    @Override
    public Object current(Setting<?> setting) {
        return engine.value(setting);
    }

    @Override
    public Map<String, Object> section(Section section) {
        return engine.rawSection(section);
    }

    @Override
    public void save(Map<Setting<?>, Object> changes) throws IOException {
        save(changes, Map.of());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void save(Map<Setting<?>, Object> changes, Map<Section, Map<String, Object>> sections) throws IOException {
        synchronized (engine) {
            for (Map.Entry<Setting<?>, Object> e : changes.entrySet()) {
                engine.config().set((Setting) e.getKey(), e.getValue());
            }
            for (Map.Entry<Section, Map<String, Object>> e : sections.entrySet()) {
                Section section = e.getKey();
                engine.config().edit(section.file(), editor -> editor.set(section.key(), e.getValue()));
            }
            engine.reload();
        }
    }
}
