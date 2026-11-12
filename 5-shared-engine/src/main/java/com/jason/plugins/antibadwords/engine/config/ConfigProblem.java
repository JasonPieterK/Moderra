package com.jason.plugins.antibadwords.engine.config;

/**
 * One thing that is wrong in the config folder. Never fatal: the engine keeps running with the default or the previous
 * good value and reports the problem.
 *
 * @param file    file relative to the config folder
 * @param key     the offending key, or {@code null} when the whole file is affected
 * @param message what is wrong and what was used instead
 */
public record ConfigProblem(String file, String key, String message) {
    @Override
    public String toString() {
        return file + (key == null ? "" : " -> " + key) + ": " + message;
    }
}
