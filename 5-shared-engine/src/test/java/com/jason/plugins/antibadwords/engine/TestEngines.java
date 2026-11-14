package com.jason.plugins.antibadwords.engine;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Shuts down every engine a test started, after the test and BEFORE JUnit deletes the @TempDir.
 * Engines own background threads (history writer, webhook senders, debounced saves) that would otherwise keep
 * creating files in the temp folder and make its deletion fail at random. Registered globally (autodetection,
 * see junit-platform.properties and META-INF/services); test code only has to call {@link #track}.
 */
public final class TestEngines implements AfterEachCallback {
    private static final List<Engine> STARTED = new ArrayList<>();

    public static Engine track(Engine engine) {
        synchronized (STARTED) {
            STARTED.add(engine);
        }
        return engine;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        List<Engine> copy;
        synchronized (STARTED) {
            copy = new ArrayList<>(STARTED);
            STARTED.clear();
        }
        for (Engine engine : copy) {
            try {
                engine.shutdown();
            } catch (RuntimeException ignored) {
                // already shut down by the test itself
            }
        }
    }
}
