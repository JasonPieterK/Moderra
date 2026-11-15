package com.jason.plugins.moderra.engine;

/** Where the engine writes its log lines (each loader forwards them to its own logger). */
public interface EngineLog {
    void info(String message);

    void warn(String message);
}
