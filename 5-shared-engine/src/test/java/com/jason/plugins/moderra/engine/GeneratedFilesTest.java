package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.config.ConfigEntry;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import com.jason.plugins.moderra.engine.tools.GenerateDocs;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The committed generated files must match what the generator produces now. Regenerate: {@code 5-shared-engine/generate-docs.sh}. */
class GeneratedFilesTest {
    private static final String HOW = " is out of date. Run 5-shared-engine/generate-docs.sh";

    @Test
    void configurationGuideIsUpToDateAndListsEverySetting() {
        SettingsRegistry registry = new SettingsRegistry();
        Engine.registerSettings(registry);
        String expected = GenerateDocs.configurationMarkdown(registry);
        for (ConfigEntry entry : registry.entries()) {
            assertTrue(expected.contains("| `" + entry.key() + "` |"), entry.key());
        }
        assertEquals(expected, TestFiles.read(TestFiles.repoRoot().resolve("6-guides/CONFIGURATION.md")), "6-guides/CONFIGURATION.md" + HOW);
    }

    @Test
    void wordListIndexIsUpToDate() throws IOException {
        Path root = TestFiles.repoRoot();
        assertEquals(GenerateDocs.wordListIndex(root.resolve("7-wordlists")),
            TestFiles.read(root.resolve("5-shared-engine/src/main/resources/wordlists-index.txt")), "wordlists-index.txt" + HOW);
    }
}
