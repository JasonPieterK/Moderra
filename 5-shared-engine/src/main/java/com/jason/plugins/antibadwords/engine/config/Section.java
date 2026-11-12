package com.jason.plugins.antibadwords.engine.config;

/**
 * A free-form part of a config file whose child keys are chosen by the admin (punishment actions, world profiles ...).
 * The registry knows it exists (so its keys are not reported as unknown, and the default file and the docs contain it)
 * but its content is read by the feature that owns it. Not editable in the GUI.
 *
 * @param defaultYaml the default content as YAML text at indent 0 (may contain comments), or {@code "{}"} for empty
 */
public record Section(String key, String file, String category, String title, String description, String defaultYaml,
                      boolean overridable) implements ConfigEntry {
}
