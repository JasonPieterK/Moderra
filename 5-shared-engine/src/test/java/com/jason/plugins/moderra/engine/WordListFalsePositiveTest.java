package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.config.WordLists;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs ordinary sentences of every language (resources {@code falsepositive/corpus-*.txt}) through the real matcher with ALL
 * real word lists switched on at once (the hardest case: every list can collide with every other language), default
 * matching options, the bundled default whitelist, the evasion normaliser, and protection levels 1, 2 and 3.
 * Every hit is a failure: remove or move the word, whitelist it in the bundled defaults, or explain it in the file's notes.
 */
class WordListFalsePositiveTest {
    @TempDir
    Path dir;

    private static List<String[]> corpus() throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (int part = 1; part <= 4; part++) {
            try (InputStream in = WordListFalsePositiveTest.class.getResourceAsStream("/falsepositive/corpus-" + part + ".txt")) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        int bar = line.indexOf('|');
                        rows.add(new String[] {line.substring(0, bar), line.substring(bar + 1)});
                    }
                }
            }
        }
        return rows;
    }

    private static WordLists realLists() {
        return WordLists.load(TestFiles.repoRoot().resolve("7-wordlists"), null, new ArrayList<>());
    }

    @Test
    void corpusCoversEveryLanguage() throws IOException {
        Map<String, Integer> perLanguage = new TreeMap<>();
        for (String[] row : corpus()) {
            perLanguage.merge(row[0], 1, Integer::sum);
        }
        int languages = 0;
        for (String code : realLists().codes()) {
            if (!code.equals(WordLists.CUSTOM)) {
                languages++;
                assertTrue(perLanguage.getOrDefault(code, 0) >= 15, code + " needs at least 15 everyday sentences, has " + perLanguage.get(code));
            }
        }
        assertEquals(37, languages);
        assertTrue(perLanguage.getOrDefault("misc", 0) >= 30, "names, places, Minecraft words");
    }

    @Test
    void everydayTextIsNeverBlockedWithAllListsOn() throws IOException {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        StringBuilder all = new StringBuilder();
        for (String code : realLists().codes()) {
            if (!code.equals(WordLists.CUSTOM)) {
                all.append("\n    - ").append(code);
            }
        }
        TestFiles.edit(f.file("config.yml"), "  enabled-word-lists:\n    - en\n", "  enabled-word-lists:" + all + "\n    - en\n");
        List<String> failures = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            TestFiles.edit(f.file("config.yml"), "protection-level: " + (level == 1 ? 3 : level - 1), "protection-level: " + level);
            assertTrue(engine.reload().isEmpty());
            assertTrue(engine.settings().enabledLanguages().size() > 37, "all languages are on");
            for (String[] row : corpus()) {
                String hit = engine.find(row[1]);
                if (hit != null) {
                    failures.add("level " + level + " [" + row[0] + "] '" + hit + "' in: " + row[1]);
                }
            }
        }
        String report = System.getProperty("moderra.fpreport"); // optional: a UTF-8 copy of the failures for reading in a terminal
        if (report != null) {
            java.nio.file.Files.writeString(Path.of(report), String.join("\n", failures) + "\n", StandardCharsets.UTF_8);
        }
        assertEquals(List.of(), failures);
    }
}
