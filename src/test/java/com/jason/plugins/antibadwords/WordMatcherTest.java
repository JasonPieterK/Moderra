package com.jason.plugins.antibadwords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WordMatcherTest {
    private static final List<String> WORDS = List.of(
        "fuck", "shit", "ass", "asshole", "crap", "loser", "cock", "arse", "puta", "nigga",
        "fick dich", "sha bi", "filha da puta", "mf", "傻逼", "操你妈", "cuzão", "сука", "хуй");

    private static WordMatcher matcher() {
        return new WordMatcher(WORDS, WordMatcher.Options.defaults());
    }

    @Test
    void blocksPlainWords() {
        assertEquals("fuck", matcher().find("what the fuck is this"));
        assertEquals("shit", matcher().find("SHIT"));
    }

    @Test
    void doesNotFlagInnocentWordsContainingBlockedOnes() {
        WordMatcher m = matcher();
        for (String clean : new String[] {"class", "closer", "scrap", "coarse", "peacock", "cocktail",
            "computer", "computation", "assume", "hello world", "pass the glass", "I am a nice guy"}) {
            assertNull(m.find(clean), clean);
        }
    }

    @Test
    void catchesLeetspeakAndSymbolsInsideWords() {
        WordMatcher m = matcher();
        assertEquals("shit", m.find("sh1t"));
        assertEquals("shit", m.find("sh!t"));
        assertEquals("shit", m.find("$hit"));
        assertEquals("ass", m.find("a$$"));
        assertEquals("nigga", m.find("n1gg@"));
    }

    @Test
    void punctuationAroundWordIsNotLeetspeak() {
        WordMatcher m = matcher();
        assertNull(m.find("wow!"));
        assertEquals("fuck", m.find("fuck!!!"));
        assertEquals("fuck", m.find("**fuck**"));
    }

    @Test
    void collapsesRepeatedLetters() {
        WordMatcher m = matcher();
        assertEquals("fuck", m.find("fuuuuuck"));
        assertEquals("asshole", m.find("assssshole"));
    }

    @Test
    void catchesSpacedLetters() {
        WordMatcher m = matcher();
        assertEquals("fuck", m.find("f u c k"));
        assertEquals("fuck", m.find("f.u.c.k you"));
        assertNull(m.find("i a m here"));
    }

    @Test
    void catchesMaskedWords() {
        WordMatcher m = matcher();
        assertEquals("fuck", m.find("f*ck"));
        assertEquals("shit", m.find("sh*t"));
        assertNull(m.find("f**"));
    }

    @Test
    void multiWordEntriesMatchWithAndWithoutSpaces() {
        WordMatcher m = matcher();
        assertEquals("fick dich", m.find("fick dich"));
        assertEquals("fick dich", m.find("fickdich"));
        assertNotNull(m.find("sua filha da puta")); // "puta" alone is blocked too
        assertEquals("sha bi", m.find("sha bi"));
        assertNull(m.find("fick"));
    }

    @Test
    void adjacentWordsAreNotGluedByDefault() {
        assertNull(matcher().find("pen is mightier"));
        WordMatcher aggressive = new WordMatcher(List.of("penis"),
            new WordMatcher.Options(true, 3, true, true, false, 4, List.of()));
        assertEquals("penis", aggressive.find("pen is"));
    }

    @Test
    void handlesAccentsAndZeroWidthCharacters() {
        WordMatcher m = matcher();
        assertEquals("cuzão", m.find("seu cuzao"));
        assertEquals("cuzão", m.find("seu cuzão"));
        assertEquals("fuck", m.find("fu​ck"));
    }

    @Test
    void catchesFullWidthCharacters() {
        assertEquals("shit", matcher().find("\uFF33\uFF28\uFF29\uFF34"));
    }

    @Test
    void handlesNonLatinScripts() {
        WordMatcher m = matcher();
        assertEquals("傻逼", m.find("你是个傻逼吧"));
        assertEquals("操你妈", m.find("操你妈"));
        assertEquals("сука", m.find("ты сука"));
        assertEquals("хуй", m.find("ХУЙ"));
        assertNull(m.find("привет мир"));
        assertNull(m.find("你好世界"));
    }

    @Test
    void cyrillicLookalikesInsideLatinWords() {
        // the "с" below is Cyrillic
        assertEquals("fuck", matcher().find("fuсk"));
    }

    @Test
    void partialMatchingIsOptInAndRespectsWhitelist() {
        WordMatcher.Options partial = new WordMatcher.Options(true, 3, true, false, true, 4, List.of("scrap", "closer"));
        WordMatcher m = new WordMatcher(WORDS, partial);
        assertEquals("fuck", m.find("motherfuckers"));
        assertNull(m.find("scrap"));
        assertNull(m.find("closer"));
    }

    @Test
    void whitelistOverridesExactMatch() {
        WordMatcher m = new WordMatcher(List.of("cock"),
            new WordMatcher.Options(true, 3, true, false, false, 4, List.of("cock")));
        assertNull(m.find("cock"));
    }

    @Test
    void entryValidation() {
        assertTrue(WordMatcher.isValidEntry("fuck"));
        assertTrue(WordMatcher.isValidEntry("傻逼"));
        assertFalse(WordMatcher.isValidEntry("x"));
        assertFalse(WordMatcher.isValidEntry("  "));
        assertFalse(WordMatcher.isValidEntry("!!"));
        assertEquals("fick dich", WordMatcher.cleanEntry("  Fick   DICH "));
    }

    @Test
    void emptyAndNullInputAreClean() {
        assertNull(matcher().find(""));
        assertNull(matcher().find(null));
        assertNotNull(matcher().find("fuck"));
    }
}
