package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.filters.Confusables;
import com.jason.plugins.antibadwords.engine.filters.EvasionCheck;
import com.jason.plugins.antibadwords.engine.filters.EvasionSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Bypass tricks against the real engine with its default files, and every switch of filters/evasion.yml. */
class EvasionFilterTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
        // neutral test words, so this file does not need real swear words for the tricky cases
        assertTrue(engine.addCustomWord("zorblax"));
        assertTrue(engine.addCustomWord("сорока"));
        assertTrue(engine.addCustomWord("año"));
    }

    private <T> void set(Setting<T> setting, T value) {
        try {
            engine.set(setting, value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        assertTrue(engine.problems().isEmpty(), engine.problems().toString());
    }

    @Test
    void realBypassAttemptsAreCaughtWithTheDefaults() {
        assertTrue(f.platform.warnings.isEmpty(), f.platform.warnings.toString());
        String[] attempts = {
            "zor​blax",                 // zero-width space inside the word
            "z‌o‍r⁠blax",     // joiners and word joiner
            "zor­blax",                 // soft hyphen
            "zorb‮lax",                 // right-to-left override
            "zorblax️",                 // variation selector
            "zorㅤblax",                 // invisible Hangul filler "letter"
            "zоrblаx",                       // Cyrillic о and а inside a Latin word
            "ZОRBLАХ",                       // capitals: Cyrillic О, А and Х
            "zοrblax",                       // Greek omicron
            "ｚｏｒｂｌａｘ",                   // full-width
            "𝐳𝐨𝐫𝐛𝐥𝐚𝐱",                 // mathematical bold
            "ⓩⓞⓡⓑⓛⓐⓧ",                   // circled letters
            "z̷o̷r̷b̷l̷a̷x̷",                   // combining strike-through
            "zórbläx",                       // accents
            "z͛o͛r͛b͛l͛a͛x͛ z̸̛̤o",               // zalgo
            "ᴢᴏʀʙʟᴀx",                       // small capitals
            "z0rbl@x",                       // leetspeak
            "z o r b l a x",                 // spaced letters
            "zorblaaaaax",                   // repeated letters
            "z*rblax",                       // masked
        };
        for (String attempt : attempts) {
            assertEquals("zorblax", engine.find("hey " + attempt + " you"), "not caught: " + attempt);
        }
        // the other direction: a Cyrillic word typed with Latin look-alikes
        assertEquals("сорока", engine.find("сорока"));
        assertEquals("сорока", engine.find("copoка"), "Latin c, o, p, o + Cyrillic ка");
        assertEquals("сорока", engine.find("СOPOКА"));
        // real swear words through the same tricks (words from the bundled English list)
        assertEquals("fuck", engine.find("fu​ck"));
        assertEquals("fuck", engine.find("fuсk"));
        assertEquals("shit", engine.find("ｓｈｉｔ"));
        assertEquals("shit", engine.find("ꜱʜɪᴛ"));
    }

    @Test
    void cleanTextStaysClean() {
        for (String clean : new String[] {"zorb lax", "blax zor", "сорок а", "сорокопут", "Hello мир", "IT-отдел и Wi-Fi", "я люблю Minecraft",
            "می‌خواهم", "👨‍👩‍👧 family", "naïve café", "uno nuevo es bueno", "COVID-19 тест", "x2 XP", "sık sık gelirim"}) {
            assertNull(engine.find(clean), clean);
        }
        assertEquals("año", engine.find("feliz ano"), "accents are ignored by default, as they always were");
    }

    @Test
    void everyStepCanBeSwitchedOff() {
        set(EvasionSettings.STRIP_INVISIBLE, false);
        assertNull(engine.find("zor​blax"));
        assertNotNull(engine.find("zоrblаx"), "the other steps still work");
        set(EvasionSettings.STRIP_INVISIBLE, true);

        set(EvasionSettings.COMPATIBILITY_FOLD, false);
        assertNull(engine.find("ｚｏｒｂｌａｘ"));
        assertNotNull(engine.find("zórblax"), "accents are still folded without the compatibility step");
        set(EvasionSettings.COMPATIBILITY_FOLD, true);

        set(EvasionSettings.FOLD_DIACRITICS, false);
        assertNull(engine.find("zórblax"));
        assertNull(engine.find("feliz ano"));
        assertEquals("año", engine.find("feliz año"));
        assertEquals("año", engine.find("feliz AÑO"), "decomposed and capital spelling of the same word");
        set(EvasionSettings.FOLD_DIACRITICS, true);

        set(EvasionSettings.LOOK_ALIKES, false);
        assertNull(engine.find("zоrblаx"));
        assertNull(engine.find("ᴢᴏʀʙʟᴀx"));
        assertNull(engine.find("copoка"));
        set(EvasionSettings.LOOK_ALIKES, true);

        set(EvasionSettings.LOOK_ALIKES_REVERSE, false);
        assertNull(engine.find("copoка"));
        assertNotNull(engine.find("zоrblаx"));
        set(EvasionSettings.LOOK_ALIKES_REVERSE, true);

        set(EvasionSettings.LEET, false);
        assertNull(engine.find("z0rbl@x"));
        assertNotNull(engine.find("zorblax!"));
        set(EvasionSettings.LEET, true);
        assertNotNull(engine.find("z0rbl@x"));
    }

    @Test
    void diacriticsCanBeKeptPerScript() {
        set(EvasionSettings.KEEP_DIACRITICS, List.of("latin"));
        assertNull(engine.find("feliz ano"), "with latin kept, ano and año are different words");
        assertEquals("año", engine.find("feliz año"));
        assertEquals("fuck", engine.find("fuck"));
        assertNull(engine.find("fück"), "kept accents also mean an accent hides a word; that is the admin's choice");

        // Hindi: two words that differ only in their vowel signs
        set(EvasionSettings.KEEP_DIACRITICS, List.of());
        assertTrue(engine.addCustomWord("कल"));
        assertEquals("कल", engine.find("काली"), "all marks folded (old behaviour): the vowel signs are ignored");
        set(EvasionSettings.KEEP_DIACRITICS, List.of("Devanagari"));
        assertNull(engine.find("काली"));
        assertEquals("कल", engine.find("कल"));
        assertEquals("zorblax", engine.find("zórblax"), "other scripts still fold");

        set(EvasionSettings.KEEP_DIACRITICS, List.of("latin", "klingon"));
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("'klingon' is not a known writing system")), f.platform.warnings.toString());
    }

    @Test
    void wholeLookAlikeWordsAndIgnoredSeparatorsAreOptIn() {
        assertTrue(engine.addCustomWord("cop"));
        assertNull(engine.find("сор"), "a normal Russian word that only looks like a blocked Latin word");
        set(EvasionSettings.LOOK_ALIKES_WHOLE, true);
        assertEquals("cop", engine.find("сор"));
        assertNull(engine.find("сорт"), "not every letter... but this one maps too, to another word");
        set(EvasionSettings.LOOK_ALIKES_WHOLE, false);

        assertNull(engine.find("zor.blax"));
        assertNull(engine.find("zor-bl_ax"));
        set(EvasionSettings.IGNORED_SEPARATORS, ".-_");
        assertEquals("zorblax", engine.find("zor.blax"));
        assertEquals("zorblax", engine.find("zor-bl_ax"));
        assertNull(engine.find("zor. blax"), "only between two letters");
        assertNull(engine.find("the end. Zorro"));
    }

    @Test
    void tablesAreAdminEditableAndBrokenEntriesFallBackSafely() {
        assertTrue(TestFiles.read(f.file("filters/confusables.yml")).contains("cyrillic:"));
        assertTrue(TestFiles.read(f.file("filters/evasion.yml")).contains("strip-invisible-characters: true"));
        // the admin adds a look-alike and a leet symbol
        assertNull(engine.find("zorblaж"));
        TestFiles.edit(f.file("filters/confusables.yml"), "  cyrillic:\n", "  mine: \"ж=x\"\n  cyrillic:\n");
        TestFiles.edit(f.file("filters/confusables.yml"), "leet-symbols: \"@=a $=s !=i |=i +=t\"", "leet-symbols: \"@=a $=s !=i |=i +=t (=c\"");
        assertTrue(engine.reload().isEmpty(), engine.problems().toString());
        assertEquals("zorblax", engine.find("zorblaж"));
        assertTrue(engine.addCustomWord("cab"));
        assertEquals("cab", engine.find("(ab"));

        // nonsense in the tables: named in the log, the rest keeps working
        TestFiles.edit(f.file("filters/confusables.yml"), "  mine: \"ж=x\"\n", "  mine: \"ж=x nonsense =y ю=!!\"\n");
        TestFiles.edit(f.file("filters/confusables.yml"), "leet-digits: \"0=o 1=i", "leet-digits: \"x=y 0=o 1=i");
        engine.reload();
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("filters/confusables.yml") && w.contains("'nonsense'")), f.platform.warnings.toString());
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("leet-digits") && w.contains("'x=y'")), f.platform.warnings.toString());
        assertEquals("zorblax", engine.find("zorblaж"));
        assertEquals("zorblax", engine.find("z0rblax"));

        // an emptied table section falls back to the built-in table instead of switching the protection off
        String text = TestFiles.read(f.file("filters/confusables.yml"));
        TestFiles.write(f.file("filters/confusables.yml"), text.substring(0, text.indexOf("look-alikes:")) + "look-alikes: {}\n");
        engine.reload();
        assertEquals("zorblax", engine.find("zоrblаx"));
        assertNull(engine.find("zorblaж"));
        assertTrue(Confusables.bundled().size() > 200);
    }

    @Test
    void detectorNotesCleansOrBlocksTheTrickItself() {
        UUID bob = f.platform.join("Bob");
        UUID vip = f.platform.join("Vip", EvasionSettings.PERMISSION_BYPASS);
        List<ModerationEvent> events = new ArrayList<>();
        engine.events().subscribe(events::add);

        // default: log-only. The message goes through, nobody is told, an event and a console line exist.
        Verdict v = f.say(bob, Message.CHAT, "he​llo wоrld");
        assertTrue(v.allowed() && v.hasHits());
        assertEquals("evasion", v.primary().check());
        assertEquals("invisible characters, look-alike letters", v.primary().detail());
        assertTrue(f.platform.sentTo(bob).isEmpty() && f.platform.broadcasts.isEmpty());
        assertEquals(0, engine.strikes().get(bob));
        assertEquals(List.of("evasion", "violation"), events.stream().map(ModerationEvent::kind).toList());
        assertEquals("log-only", events.get(0).data().get("action"));
        assertTrue(f.platform.infos.contains("Bob triggered evasion (invisible characters, look-alike letters) in chat: noted only"));

        // things that are not tricks
        for (String fine : new String[] {"hello world", "می‌خواهم", "👨‍👩‍👧 ok ❤️", "Привет мир", "IT-отдел", "naïve", "Ωmega 3",
            "x² + y²", "한국어 text", "end​", "​start", "a ​ b"}) {
            assertFalse(engine.inspect(engine.message(bob, "Bob", Message.CHAT, fine)).hasHits(), fine);
        }
        assertFalse(engine.inspect(engine.message(vip, "Vip", Message.CHAT, "he​llo")).hasHits(), "bypass permission");

        // censor: delivered cleaned where the server can rewrite (chat), blocked elsewhere
        set(EvasionSettings.DETECT_ACTION, "censor");
        f.platform.rewritable.add(Message.CHAT);
        v = f.say(bob, Message.CHAT, "He​llo WОrld, Привет Aндрей");
        assertTrue(v.censored());
        assertEquals("Hello WOrld, Привет Андрей", v.text());
        assertTrue(f.platform.sentTo(bob).isEmpty(), "a cleaned message needs no explanation");
        v = f.say(bob, Message.SIGN, "He​llo");
        assertTrue(v.blocked(), "signs cannot be rewritten, so the trick is blocked there");
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §c§lMessage blocked. §7Your message contains hidden or look-alike characters. §7§oUse normal letters."), f.platform.sentTo(bob));

        // block, with strike weight
        set(EvasionSettings.DETECT_ACTION, "block");
        set(EvasionSettings.DETECT_WEIGHT, 1);
        f.platform.sentTo(bob).clear();
        v = f.say(bob, Message.CHAT, "wоrld");
        assertTrue(v.blocked());
        assertEquals(1, engine.strikes().get(bob));
        assertEquals("§8[§6§lAntiBadWords§8]§r §c§lMessage blocked. §7Your message contains hidden or look-alike characters. §7§oUse normal letters.", f.platform.sentTo(bob).get(0));

        // switches
        set(EvasionSettings.DETECT_MIXED, false);
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.CHAT, "wоrld")).allowed());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.CHAT, "wo​rld")).blocked());
        set(EvasionSettings.DETECT_SOURCES, List.of("sign"));
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.CHAT, "wo​rld")).allowed());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.SIGN, "wo​rld")).blocked());
        assertTrue(engine.inspect(engine.message(bob, "Bob", Message.SIGN_BACK, "wo​rld")).blocked(), "sign covers the back side");
        set(EvasionSettings.DETECT_ACTION, "off");
        assertFalse(engine.inspect(engine.message(bob, "Bob", Message.SIGN, "wo​rld")).hasHits());

        EvasionCheck.Finding finding = EvasionCheck.scan("a​b", true, true, Set.of(Character.UnicodeScript.LATIN), Confusables.bundled());
        assertEquals("ab", finding.cleaned());
        assertNull(EvasionCheck.scan("a​b", true, true, Set.of(Character.UnicodeScript.CYRILLIC), Confusables.bundled()));
    }
}
