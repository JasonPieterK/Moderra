package com.jason.plugins.antibadwords.engine.config;

/** Something that lives at a key in one of the config files: a {@link Setting} or a free-form {@link Section}. */
public interface ConfigEntry {
    /** Dotted key path inside the file, for example {@code filters.scan.chat}. */
    String key();

    /** File the entry lives in, relative to the config folder, for example {@code config.yml} or {@code filters/spam.yml}. */
    String file();

    /** Group shown as one page in the GUI and one chapter in CONFIGURATION.md. */
    String category();

    /** Short label (GUI item name). */
    String title();

    /** Plain-language explanation. Becomes the comment in the generated file. May contain line breaks. */
    String description();

    /** Whether a profile in worlds.yml may override this entry. */
    boolean overridable();
}
