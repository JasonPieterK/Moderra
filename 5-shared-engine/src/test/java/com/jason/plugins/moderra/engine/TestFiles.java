package com.jason.plugins.moderra.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test helpers for files. */
final class TestFiles {
    private TestFiles() {
    }

    /** The real default config.yml that version 1 shipped (one big file). */
    static String legacyDefaultConfig() {
        try (InputStream in = TestFiles.class.getResourceAsStream("/legacy/config-v1-default.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Replaces text in a file; fails when the text is not there (so tests notice when a default changes). */
    static void edit(Path file, String from, String to) {
        String text = read(file);
        if (!text.contains(from)) {
            throw new AssertionError(file.getFileName() + " does not contain: " + from);
        }
        write(file, text.replace(from, to));
    }

    /** The repository root (the tests run in 5-shared-engine). */
    static Path repoRoot() {
        return Path.of("..").toAbsolutePath().normalize();
    }
}
