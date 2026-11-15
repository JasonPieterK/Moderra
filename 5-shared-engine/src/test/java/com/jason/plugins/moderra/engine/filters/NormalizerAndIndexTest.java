package com.jason.plugins.moderra.engine.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.WordMatcher;
import com.jason.plugins.moderra.engine.config.WordLists;
import com.jason.plugins.moderra.engine.legacy.LegacyWordMatcher;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The reworked matcher against the unchanged old one ({@link LegacyWordMatcher}): same tokens and same decisions for
 * every input when it is configured like version 1, and same decisions for clean text with the new defaults.
 */
class NormalizerAndIndexTest {
    /** Characters picked to hit every branch: plain, leet, look-alikes, marks, invisible, full-width, CJK, emoji. */
    private static final String[] POOL = {
        "a", "b", "c", "e", "f", "i", "k", "o", "s", "t", "u", "x", "A", "S", "H", "0", "1", "3", "4", "5", "7", "9", "@", "$", "!", "|", "+", "*",
        " ", " ", " ", ".", ",", "-", "_", "'", "а", "е", "о", "с", "р", "х", "у", "к", "м", "н", "т", "і", "ш", "ы", "Б", "С", "ο", "α", "ν", "ω",
        "́", "̈", "̶", "​", "‍", "­", "️", "‮", "ｆ", "ｕ", "Ａ", "①", "ß", "é", "ñ", "ü", "ç", "ı", "İ",
        "傻", "逼", "操", "你", "妈", "の", "ば", "か", "ก", "ร", "ह", "ि", "ा", "م", "ر", "ح", "ب", "ا", "ש", "ל", "한", "국", "😀", "👍", "🇩🇪"};

    private static String random(Random random, int maxParts, List<String> words) {
        StringBuilder out = new StringBuilder();
        int parts = 1 + random.nextInt(maxParts);
        for (int i = 0; i < parts; i++) {
            if (!words.isEmpty() && random.nextInt(6) == 0) {
                String word = words.get(random.nextInt(words.size()));
                // sometimes damage the word the way players do
                switch (random.nextInt(6)) {
                    case 0 -> word = word.replace('a', '@').replace('i', '1').replace('o', '0');
                    case 1 -> word = word.charAt(0) + "*" + word.substring(Math.min(2, word.length()));
                    case 2 -> word = String.join(" ", word.split(""));
                    case 3 -> word = word + word.charAt(word.length() - 1) + word.charAt(word.length() - 1) + word.charAt(word.length() - 1);
                    case 4 -> word = "x" + word + "y";
                    default -> { }
                }
                out.append(word);
            } else {
                out.append(POOL[random.nextInt(POOL.length)]);
            }
        }
        return out.toString();
    }

    static List<String> allBundledWords() {
        WordLists lists = WordLists.load(Path.of("..").toAbsolutePath().normalize().resolve("7-wordlists"), null, new ArrayList<>());
        Set<String> words = new LinkedHashSet<>();
        for (String code : lists.codes()) {
            words.addAll(lists.get(code).words(3));
        }
        return new ArrayList<>(words);
    }

    @Test
    void legacyNormaliserGivesExactlyTheOldTokens() {
        Random random = new Random(42);
        for (int i = 0; i < 60_000; i++) {
            String text = random(random, 24, List.of());
            assertEquals(LegacyWordMatcher.tokenize(text), EvasionNormalizer.LEGACY.tokenize(text), () -> "tokens changed for: " + escape(text));
        }
        for (String text : new String[] {null, "", " ", "***", "@", "!!!a!!!", "$$", "a$$", "@ss", "+1", "f*ck", "ＦＵＣＫ", "ǅ", "ﬁ", "İstanbul"}) {
            assertEquals(LegacyWordMatcher.tokenize(text), EvasionNormalizer.LEGACY.tokenize(text), String.valueOf(text));
        }
    }

    @Test
    void indexedMatcherDecidesLikeTheOldScanningMatcher() {
        List<String> words = allBundledWords();
        assertTrue(words.size() > 3000, "all bundled lists: " + words.size());
        List<String> whitelist = List.of("class", "assassin", "grass", "scunthorpe", "cockpit");
        Random random = new Random(7);
        int blocked = 0;
        int total = 0;
        for (boolean partial : new boolean[] {false, true}) {
            for (boolean join : new boolean[] {false, true}) {
                WordMatcher.Options now = new WordMatcher.Options(true, 3, true, join, partial, 4, whitelist);
                LegacyWordMatcher.Options then = new LegacyWordMatcher.Options(true, 3, true, join, partial, 4, whitelist);
                WordMatcher matcher = new WordMatcher(words, now);
                LegacyWordMatcher old = new LegacyWordMatcher(words, then);
                assertEquals(old.size(), matcher.size());
                List<String> texts = new ArrayList<>();
                for (int i = 0; i < 6000; i++) {
                    texts.add(random(random, 30, words));
                }
                for (int i = 0; i < words.size(); i += 7) {
                    String w = words.get(i);
                    texts.add(w);
                    texts.add("so " + w.toUpperCase() + " !");
                    texts.add(w.charAt(0) + "*" + w.substring(Math.min(2, w.length())));
                    texts.add("**" + w.substring(Math.min(2, w.length())));
                    texts.add("pre" + w + "post");
                }
                for (String text : texts) {
                    String expected = old.find(text);
                    assertEquals(expected, matcher.find(text), () -> "decision changed (partial=" + partial + ", join=" + join + ") for: " + escape(text));
                    blocked += expected == null ? 0 : 1;
                    total++;
                }
            }
        }
        assertTrue(blocked > 2000 && blocked < total - 2000, "the corpus must contain blocked and clean texts: " + blocked + "/" + total);
    }

    /** Clean sentences in many writing systems: the new default normaliser must give the tokens version 1 gave. */
    static final Map<String, List<String>> CLEAN = Map.ofEntries(
        Map.entry("latin-english", List.of("Hello everyone, how are you today?", "Let's meet at spawn in 5 minutes!", "gg wp, that was a great game :)",
            "I need 3 diamonds & 12 iron for the trade", "The assassin passed the class with a classic glass of grass")),
        Map.entry("latin-accents", List.of("Où est la bibliothèque, s'il vous plaît ?", "Ich hätte gern ein Stück Käsekuchen für fünf Euro",
            "¿Dónde está el niño? Mañana jugamos al fútbol", "Zażółć gęślą jaźń i chodź do domu", "Tôi muốn xây một ngôi nhà gỗ",
            "Çok güzel bir gün, İstanbul'da buluşalım", "Příliš žluťoučký kůň úpěl ďábelské ódy")),
        Map.entry("cyrillic", List.of("Привет, как дела? Пойдём строить дом", "Сьогодні гарна погода, ходімо на шахту", "Здравей, колко е часът?")),
        Map.entry("greek", List.of("Καλημέρα, τι κάνεις σήμερα;", "Θέλω να χτίσω ένα μεγάλο κάστρο")),
        Map.entry("arabic-persian", List.of("مرحبا كيف حالك اليوم", "می‌خواهم یک خانه‌ی بزرگ بسازم", "السَّلَامُ عَلَيْكُمْ")),
        Map.entry("hebrew", List.of("שלום, מה שלומך היום?", "שָׁלוֹם עֲלֵיכֶם")),
        Map.entry("indic", List.of("नमस्ते, आप कैसे हैं?", "আমি একটি বাড়ি বানাতে চাই", "நான் ஒரு வீடு கட்ட விரும்புகிறேன்")),
        Map.entry("thai", List.of("สวัสดีครับ วันนี้อากาศดีมาก", "ไปขุดเพชรกันเถอะ")),
        Map.entry("cjk", List.of("你好，今天我们一起建房子吧", "こんにちは、今日は一緒に遊びましょう", "안녕하세요, 같이 집을 지어요", "我去挖矿了，一会儿见")),
        Map.entry("emoji-and-symbols", List.of("nice 👍🏽 see you ❤️ 👨‍👩‍👧", "<3 :D ^_^ -> 100% !!!", "x=10, y=64, z=-120 (base)")));

    @Test
    void newDefaultsDoNotChangeCleanTextInAnyLanguageFamily() {
        EvasionNormalizer now = defaults();
        List<String> words = allBundledWords();
        WordMatcher matcher = new WordMatcher(words, WordMatcher.Options.defaults(), now);
        LegacyWordMatcher old = new LegacyWordMatcher(words, LegacyWordMatcher.Options.defaults());
        assertEquals(old.size(), matcher.size(), "every word of every bundled list is indexed as before");
        int sentences = 0;
        for (Map.Entry<String, List<String>> family : CLEAN.entrySet()) {
            for (String text : family.getValue()) {
                assertEquals(LegacyWordMatcher.tokenize(text), now.tokenize(text), family.getKey() + ": " + text);
                assertNull(old.find(text), "the sample must be clean for the old matcher (" + family.getKey() + "): " + text);
                assertNull(matcher.find(text), "false positive in " + family.getKey() + ": " + text);
                sentences++;
            }
        }
        assertTrue(sentences >= 30);
        // and every blocked word of every list is still found, written plainly
        for (String word : words) {
            assertEquals(old.find(word), matcher.find(word), word);
            assertNotNull(matcher.find("a " + word + " b"), word);
        }
    }

    static EvasionNormalizer defaults() {
        return new EvasionNormalizer(new EvasionNormalizer.Options(true, true, true, true, Set.of(), true, true, false, true,
            EvasionNormalizer.DEFAULT_LEET_SYMBOLS, EvasionNormalizer.DEFAULT_NO_START, EvasionNormalizer.DEFAULT_NO_END,
            EvasionNormalizer.DEFAULT_LEET_DIGITS, "", Confusables.bundled()));
    }

    @Test
    void bundledTableIsTheUnicodeDerivedOneAndContainsTheOldLetters() {
        Confusables table = Confusables.bundled();
        assertTrue(table.size() > 200, "size " + table.size());
        assertEquals("f", table.always('ꜰ'));
        assertNull(table.always('ı'), "the Turkish dotless i is a real letter, not a trick");
        assertNull(table.toLatin('ı'));
        for (String pair : "а=a с=c е=e о=o р=p х=x у=y к=k м=m н=h т=t і=i".split(" ")) {
            assertEquals(pair.substring(2), table.toLatin(pair.codePointAt(0)), pair);
        }
        assertEquals("b", table.toLatin('В'), "capital look-alikes are known as typed");
        assertNull(table.toLatin('в'), "the small letter does not look like b");
        assertNull(table.toLatin('ж'));
        assertEquals("o", table.toLatin('ο'));
        assertEquals('а', table.fromLatin(Character.UnicodeScript.CYRILLIC, 'a'));
        assertEquals('х', table.fromLatin(Character.UnicodeScript.CYRILLIC, 'x'));
        assertEquals(-1, table.fromLatin(Character.UnicodeScript.CYRILLIC, 'f'));
        assertEquals(-1, table.fromLatin(Character.UnicodeScript.HAN, 'a'));
    }

    @Test
    void wordIndexAnswersLikeScanningEveryWordInOrder() {
        Random random = new Random(3);
        String alphabet = "abcde*";
        List<String> keys = new ArrayList<>();
        WordIndex index = new WordIndex();
        for (int i = 0; i < 400; i++) {
            StringBuilder key = new StringBuilder();
            for (int n = 2 + random.nextInt(5); n > 0; n--) {
                key.append("abcde".charAt(random.nextInt(5)));
            }
            if (!keys.contains(key.toString())) {
                keys.add(key.toString());
                index.add(key.toString(), "#" + keys.size());
            }
        }
        assertEquals(keys.size(), index.size());
        for (int i = 0; i < 20_000; i++) {
            StringBuilder text = new StringBuilder();
            for (int n = random.nextInt(12); n > 0; n--) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String t = text.toString();
            for (int min : new int[] {1, 3, 5}) {
                String expected = null;
                for (int k = 0; k < keys.size() && expected == null; k++) {
                    expected = keys.get(k).length() >= min && t.contains(keys.get(k)) ? "#" + (k + 1) : null;
                }
                assertEquals(expected, index.firstContained(t, min), t + " min " + min);
            }
            String expected = null;
            for (int k = 0; k < keys.size() && expected == null && t.length() >= 3; k++) {
                String key = keys.get(k);
                boolean same = key.length() == t.length();
                boolean real = false;
                for (int c = 0; same && c < key.length(); c++) {
                    if (t.charAt(c) != '*') {
                        real = true;
                        same = t.charAt(c) == key.charAt(c);
                    }
                }
                expected = same && real ? "#" + (k + 1) : null;
            }
            assertEquals(expected, index.firstMasked(t, '*'), t);
        }
        assertNull(new WordIndex().firstContained("anything", 1));
        assertNull(new WordIndex().firstMasked("a*c", '*'));
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> out.append(cp >= 32 && cp < 127 ? String.valueOf((char) cp) : String.format("\\u{%X}", cp)));
        return out.toString();
    }
}
