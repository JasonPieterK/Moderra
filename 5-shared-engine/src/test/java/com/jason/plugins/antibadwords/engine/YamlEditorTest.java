package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.YamlEditor;
import com.jason.plugins.antibadwords.engine.config.YamlTree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlEditorTest {
    private static final String DOC = """
        # header comment
        # second header line

        filters:
          # how strict
          protection-level: 3   # trailing note
          scan:
            chat: true
            signs: true

          # safe words
          whitelist:
            - class
            # between items
            - "glass"   # why not
            - 'grass'
          matching: {a: 1, b: [x, y]}

        messages:
          warning: "&cNo: {word} # not a comment"
          other: 'it''s # fine'
        # the end
        """;

    private static long comments(String text) {
        return text.lines().filter(l -> l.contains("#")).count();
    }

    @Test
    void scalarEditKeepsEveryOtherByte() {
        String out = YamlEditor.of(DOC).set("filters.protection-level", 1).text();
        assertEquals(DOC.replace("protection-level: 3   # trailing note", "protection-level: 1   # trailing note"), out);
        out = YamlEditor.of(DOC).set("filters.scan.signs", false).text();
        assertEquals(DOC.replace("signs: true", "signs: false"), out);
    }

    @Test
    void stringsAreQuotedOnlyWhenNeededAndRoundTrip() {
        for (String value : List.of("plain", "two words", "&cRed {player}: \"quoted\" \\ back", "yes", "no", "123", "#hash", " lead",
            "trail ", "a: b", "multi\nline", "üñí 傻", "", "- dash", "[x]", "null", "~", "0x1F", "tab\there")) {
            String out = YamlEditor.of(DOC).set("messages.warning", value).text();
            assertEquals(value, YamlTree.parse(out).get("messages.warning"), out);
            assertEquals("it's # fine", YamlTree.parse(out).get("messages.other"));
            assertEquals(comments(DOC) - 1, comments(out.replace(value, "")), "comments must survive for value " + value);
        }
        assertTrue(YamlEditor.of(DOC).set("messages.warning", "plain").text().contains("warning: plain\n"));
        assertTrue(YamlEditor.of(DOC).set("messages.warning", "yes").text().contains("warning: \"yes\"\n"));
    }

    @Test
    void listAddAndRemoveTouchOnlyOneLine() {
        String added = YamlEditor.of(DOC).listAdd("filters.whitelist", "pass").text();
        assertEquals(DOC.replace("    - 'grass'\n", "    - 'grass'\n    - pass\n"), added);

        YamlEditor editor = YamlEditor.of(DOC);
        assertTrue(editor.listRemove("filters.whitelist", "GLASS"));
        assertEquals(DOC.replace("    - \"glass\"   # why not\n", ""), editor.text());
        assertFalse(editor.listRemove("filters.whitelist", "nothere"));
        assertEquals(List.of("class", "grass"), YamlTree.parse(editor.text()).getStringList("filters.whitelist"));

        // an entry that needs quoting
        String odd = YamlEditor.of(DOC).listAdd("filters.whitelist", "no").text();
        assertEquals(List.of("class", "glass", "grass", "no"), YamlTree.parse(odd).getStringList("filters.whitelist"));
    }

    @Test
    void removingTheLastItemAndRefillingWorks() {
        YamlEditor editor = YamlEditor.of("a:\n  list:\n    - one\n  after: 1\n");
        assertTrue(editor.listRemove("a.list", "one"));
        assertEquals("a:\n  list: []\n  after: 1\n", editor.text());
        editor.listAdd("a.list", "two");
        assertEquals("a:\n  list:\n    - two\n  after: 1\n", editor.text());
    }

    @Test
    void wholeListAndMapReplacement() {
        String out = YamlEditor.of(DOC).set("filters.whitelist", List.of("a", "b c")).text();
        assertEquals(List.of("a", "b c"), YamlTree.parse(out).getStringList("filters.whitelist"));
        assertTrue(out.contains("  # safe words\n  whitelist:\n    - a\n    - b c\n  matching:"), out);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", true);
        map.put("names", List.of("x"));
        map.put("inner", Map.of("deep", 1.5));
        out = YamlEditor.of(DOC).set("filters.matching", map).text();
        assertEquals(1.5, YamlTree.parse(out).get("filters.matching.inner.deep"));
        assertEquals(List.of("x"), YamlTree.parse(out).getStringList("filters.matching.names"));
        assertTrue(out.contains("\nmessages:\n"), "the next section is untouched");
        assertEquals(DOC.substring(DOC.indexOf("messages:")), out.substring(out.indexOf("messages:")));
    }

    @Test
    void missingKeysAreCreatedInPlaceAndAtTheEnd() {
        String out = YamlEditor.of(DOC).set("filters.scan.books", false).text();
        assertEquals(DOC.replace("    signs: true\n", "    signs: true\n    books: false\n"), out);

        out = YamlEditor.of(DOC).set("logging.console", true).text();
        assertTrue(out.endsWith("# the end\nlogging:\n  console: true\n") || out.contains("logging:\n  console: true\n"), out);
        assertEquals(true, YamlTree.parse(out).get("logging.console"));
        assertEquals(comments(DOC), comments(out));

        out = YamlEditor.of("").set("a.b.c", 5).text();
        assertEquals("a:\n  b:\n    c: 5\n", out);
    }

    @Test
    void keyInsideAnInlineParentRewritesOnlyThatParent() {
        String doc = "# top\nprofiles: {}   # all profiles\nworlds:\n  a: b\n";
        String out = YamlEditor.of(doc).set(List.of("profiles", "strict", "filters.protection-level"), 3).text();
        assertEquals("# top\nprofiles:   # all profiles\n  strict:\n    filters.protection-level: 3\nworlds:\n  a: b\n", out);
        // flow map parent: existing content is kept
        out = YamlEditor.of(DOC).set("filters.matching.c", "new").text();
        YamlTree tree = YamlTree.parse(out);
        assertEquals(1, tree.get("filters.matching.a"));
        assertEquals("new", tree.get("filters.matching.c"));
        assertEquals(comments(DOC), comments(out));
    }

    @Test
    void multiLineFlowListsAsInTheWordListFiles() {
        String doc = """
            # AntiBadWords word list.
            language: "no"
            level-1: []
            level-2: [
              "aaa", "bbb",   # comment inside
              "ccc"
            ]
            level-3: ["x y"]
            """;
        YamlEditor editor = YamlEditor.of(doc);
        editor.listAdd("level-2", "ddd").listAdd("level-1", "first").listAdd("level-3", "zz");
        YamlTree tree = YamlTree.parse(editor.text());
        assertEquals(List.of("aaa", "bbb", "ccc", "ddd"), tree.getStringList("level-2"));
        assertEquals(List.of("first"), tree.getStringList("level-1"));
        assertEquals(List.of("x y", "zz"), tree.getStringList("level-3"));
        assertEquals("no", tree.get("language"));
        assertTrue(editor.text().startsWith("# AntiBadWords word list.\nlanguage: \"no\"\n"));
    }

    @Test
    void quotedAndSpecialKeys() {
        String doc = "worlds:\n  \"minecraft:the_nether\": relaxed\n  'creative_*': relaxed\n  plain: x\n";
        String out = YamlEditor.of(doc).set(List.of("worlds", "minecraft:the_nether"), "strict")
            .set(List.of("worlds", "creative_*"), "strict").set(List.of("worlds", "new world"), "x").text();
        assertEquals("worlds:\n  \"minecraft:the_nether\": strict\n  'creative_*': strict\n  plain: x\n  \"new world\": x\n", out);
    }

    @Test
    void sameIndentListsAndWindowsLineEndings() {
        String doc = "list:\r\n- a\r\n- b\r\nnext: 1\r\n";
        YamlEditor editor = YamlEditor.of(doc).listAdd("list", "c").set("next", 2);
        assertEquals("list:\r\n- a\r\n- b\r\n- c\r\nnext: 2\r\n", editor.text());
        assertEquals("list:\n- x\nnext: 1\n", YamlEditor.of("list:\n- a\n- b\nnext: 1\n").set("list", List.of("x")).text());
    }

    @Test
    void removeDropsTheKeyAndItsOwnComment() {
        YamlEditor editor = YamlEditor.of(DOC);
        assertTrue(editor.remove("filters.protection-level"));
        assertEquals(DOC.replace("  # how strict\n  protection-level: 3   # trailing note\n", ""), editor.text());
        assertFalse(editor.remove("filters.nope"));
    }

    @Test
    void durationsAndNumbers() {
        String out = YamlEditor.of("a: 1\n").set("a", Duration.ofMinutes(90)).set("b", 0.05).set("c", -3).text();
        assertEquals("a: \"1h30m\"\nb: 0.05\nc: -3\n", out);
    }

    @Test
    void aFileThatIsNotUnderstoodIsLeftAlone() {
        YamlEditor broken = YamlEditor.of("a: [unclosed\nb: 1\n");
        assertThrows(IllegalStateException.class, () -> broken.set("b", 2));
        assertEquals("a: [unclosed\nb: 1\n", broken.text());
        YamlEditor list = YamlEditor.of("items:\n  - name: x\n");
        assertThrows(IllegalStateException.class, () -> list.set("items.name", "y"));
        assertEquals("items:\n  - name: x\n", list.text());
    }

    @Test
    void saveIsAtomicAndCreatesFolders(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/dir/x.yml");
        YamlEditor.of("a: 1\n").set("a", 2).save(file);
        assertEquals("a: 2\n", Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling("x.yml.tmp")));
        assertEquals("a: 3\n", YamlEditor.load(file).set("a", 3).text());
    }
}
