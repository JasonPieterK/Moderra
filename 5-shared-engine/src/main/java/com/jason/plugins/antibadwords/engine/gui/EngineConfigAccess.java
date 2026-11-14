package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.engine.Engine;
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
    public SettingsRegistry registry() {
        return engine.registry();
    }

    @Override
    public Object current(Setting<?> setting) {
        return engine.value(setting);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void save(Map<Setting<?>, Object> changes) throws IOException {
        synchronized (engine) {
            for (Map.Entry<Setting<?>, Object> e : changes.entrySet()) {
                engine.config().set((Setting) e.getKey(), e.getValue());
            }
            engine.reload();
        }
    }
}
