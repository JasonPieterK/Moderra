package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.ConfigProblem;
import com.jason.plugins.antibadwords.engine.config.Resources;
import com.jason.plugins.antibadwords.engine.config.WordList;
import com.jason.plugins.antibadwords.engine.config.WordLists;
import com.jason.plugins.antibadwords.engine.config.YamlTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads the real files of {@code 7-wordlists/} (the build copies them into the jar as {@code wordlists/<code>.yml}). */
class WordListsTest {
    @TempDir
    Path dir;

    @Test
    void everyRealWordListFileLoads() {
        List<ConfigProblem> problems = new ArrayList<>();
        WordLists lists = WordLists.load(TestFiles.repoRoot().resolve("7-wordlists"), null, problems);
        assertEquals(List.of(), problems);
        assertEquals(38, lists.codes().size(), "37 language files + custom.yml");
        assertTrue(lists.codes().containsAll(List.of("en", "en-gb", "id", "pt-br", "pt-pt", "ru", "de", "zh", "zh-tw", "ko", "ja", "ms", "no", "custom")));
        int total = 0;
        for (String code : lists.codes()) {
            WordList list = lists.get(code);
            assertEquals(code, list.code());
            assertFalse(list.name().isBlank(), code);
            assertEquals("unreviewed", list.reviewStatus(), code);
            if (!code.equals("custom")) {
                assertTrue(list.tiers().get(2).size() >= 5, code + " has severe words");
                assertTrue(list.size() >= 20, code);
            }
            // tiers do not overlap inside a file, and every entry survives the cleaning the matcher uses
            Set<String> seen = new HashSet<>();
            list.tiers().forEach(tier -> tier.forEach(word -> assertTrue(seen.add(word), code + " duplicate: " + word)));
            total += list.size();
        }
        assertTrue(total > 3500, "all languages together: " + total);
        assertEquals(0, lists.get("custom").size());
    }

    @Test
    void norwegianIsKeyedByFileNameAndLanguageIsReadAsText() {
        WordLists lists = WordLists.load(TestFiles.repoRoot().resolve("7-wordlists"), null, new ArrayList<>());
        assertEquals("Norwegian", lists.get("no").name());
        assertEquals("no", lists.get("NO").code());

        // even when somebody removes the quotes (YAML then reads the Boolean false), the file name wins and nothing is reported
        List<String> remarks = new ArrayList<>();
        WordList unquoted = WordList.from("no", YamlTree.parse("language: no\nname: Norsk\nlevel-3: [faen]\n"), remarks);
        assertEquals("no", unquoted.code());
        assertEquals(List.of("faen"), unquoted.tiers().get(2));
        assertEquals(List.of(), remarks);
        // a real mismatch is reported but does not stop the file
        WordList wrong = WordList.from("sv", YamlTree.parse("language: \"de\"\nlevel-1: 5\nlevel-2: [\"Två  Ord\", x, 12, null]\n"), remarks);
        assertEquals("sv", wrong.code());
        assertEquals(List.of("två ord", "12"), wrong.tiers().get(1), "entries are cleaned; too short ones are dropped");
        assertEquals(2, remarks.size(), remarks.toString());
    }

    @Test
    void protectionLevelPicksTheTiers() {
        WordList list = WordList.from("xx", YamlTree.parse("level-1: [mild]\nlevel-2: [rude]\nlevel-3: [severe]\n"), new ArrayList<>());
        assertEquals(List.of("severe"), list.words(1));
        assertEquals(List.of("rude", "severe"), list.words(2));
        assertEquals(List.of("mild", "rude", "severe"), list.words(3));
        assertEquals(list.words(3), list.words(99));
        assertEquals(list.words(1), list.words(0));
    }

    @Test
    void theJarIndexMatchesTheFolderAndFilesAreCopiedOnce() throws IOException {
        List<String> codes = Resources.bundledWordListCodes();
        assertEquals(38, codes.size());
        for (String code : codes) {
            assertNotNull(Resources.bundledWordList(code), code);
            assertEquals(TestFiles.read(TestFiles.repoRoot().resolve("7-wordlists/" + code + ".yml")), Resources.bundledWordList(code), code);
        }
        assertNull(Resources.bundledWordList("nope"));

        assertEquals(38, WordLists.copyBundled(dir));
        TestFiles.write(dir.resolve("de.yml"), "language: \"de\"\nlevel-3: [\"meinwort\"]\n");
        Files.delete(dir.resolve("fr.yml"));
        assertEquals(1, WordLists.copyBundled(dir), "only the missing file comes back");
        WordLists lists = WordLists.load(dir, null, new ArrayList<>());
        assertEquals(1, lists.get("de").size(), "an edited file is never overwritten");
        assertTrue(lists.get("fr").size() > 20);
    }

    @Test
    void aBrokenLanguageFileKeepsItsOldWords() {
        TestFiles.write(dir.resolve("aa.yml"), "language: \"aa\"\nlevel-3: [\"wordone\"]\n");
        TestFiles.write(dir.resolve("bb.yml"), "language: \"bb\"\nlevel-3: [\"wordtwo\"]\n");
        TestFiles.write(dir.resolve("notes.txt"), "not a list");
        List<ConfigProblem> problems = new ArrayList<>();
        WordLists first = WordLists.load(dir, null, problems);
        assertEquals(List.of("aa", "bb"), first.codes());
        assertTrue(problems.isEmpty());

        TestFiles.write(dir.resolve("aa.yml"), "level-3: [\"unclosed\n");
        TestFiles.write(dir.resolve("cc.yml"), "level-3: {{{\n");
        WordLists second = WordLists.load(dir, first, problems);
        assertEquals(List.of("wordone"), second.get("aa").tiers().get(2), "previous good content");
        assertNull(second.get("cc"), "never loaded before: skipped");
        assertEquals(2, problems.size());
        assertTrue(problems.get(0).toString().startsWith("wordlists/aa.yml: cannot be read"), problems.get(0).toString());
        assertTrue(problems.get(0).message().contains("keeping the previous words"));
        assertTrue(problems.get(1).message().contains("skipped"));
    }

    @Test
    void oldLanguageNamesAreUnderstood() {
        assertEquals("en", WordLists.canonicalCode(" English "));
        assertEquals("en-gb", WordLists.canonicalCode("uk_english"));
        assertEquals("id", WordLists.canonicalCode("indonesian"));
        assertEquals("pt-br", WordLists.canonicalCode("portuguese_brazil"));
        assertEquals("ru", WordLists.canonicalCode("russian"));
        assertEquals("de", WordLists.canonicalCode("german"));
        assertEquals("zh", WordLists.canonicalCode("chinese"));
        assertEquals("custom", WordLists.canonicalCode("custom"));
        assertEquals("fr", WordLists.canonicalCode("FR"));
    }

    @Test
    void enablingRealLanguagesBlocksTheirWords() {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        assertNull(engine.find("du bist ein arschloch"));
        TestFiles.edit(f.file("config.yml"), "    - en\n    - custom\n", "    - en\n    - de\n    - \"no\"\n    - custom\n");
        assertTrue(engine.reload().isEmpty());
        assertEquals(List.of("en", "de", "no", "custom"), engine.settings().enabledLanguages());
        assertEquals("arschloch", engine.find("du bist ein arschloch"));
        assertTrue(engine.listWords("no").size() > 20);
        assertTrue(engine.availableLists().containsAll(List.of("de", "no", "custom", "active")));
        // a language without a file is reported, like the old plugin did for a missing word-lists section
        TestFiles.edit(f.file("config.yml"), "    - de\n", "    - de\n    - elvish\n");
        engine.reload();
        assertTrue(f.platform.warnings.contains("filters.enabled-word-lists contains 'elvish' but there is no file wordlists/elvish.yml."));
    }
}
