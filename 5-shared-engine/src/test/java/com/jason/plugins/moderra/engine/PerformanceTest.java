package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.WordMatcher;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.WordLists;
import com.jason.plugins.moderra.engine.filters.EvasionSettings;
import com.jason.plugins.moderra.engine.filters.LinkSettings;
import com.jason.plugins.moderra.engine.filters.SpamSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The performance budget: with 10 000 blocked words loaded, judging a 256-character message must take less than
 * 1 millisecond at the 99th percentile. Measured with a plain timing loop (no extra library) over normal, random and
 * deliberately nasty messages. The numbers are printed; the limit can be changed with
 * {@code -Dmoderra.perf.budget.micros=...} on a very slow or very busy machine.
 */
class PerformanceTest {
    private static final int WORDS = 10_000;
    private static final int LENGTH = 256;
    private static final long BUDGET_MICROS = Long.getLong("moderra.perf.budget.micros", 1000L);

    @TempDir
    Path dir;

    /** Every bundled word plus made-up ones, 10 000 in total. */
    private static List<String> tenThousandWords() {
        WordLists lists = WordLists.load(TestFiles.repoRoot().resolve("7-wordlists"), null, new ArrayList<>());
        Set<String> words = new LinkedHashSet<>();
        for (String code : lists.codes()) {
            words.addAll(lists.get(code).words(3));
        }
        Random random = new Random(1);
        String consonants = "bcdfghjklmnprstvwz";
        String vowels = "aeiou";
        while (words.size() < WORDS) {
            StringBuilder word = new StringBuilder();
            for (int n = 2 + random.nextInt(4); n > 0; n--) {
                word.append(consonants.charAt(random.nextInt(consonants.length()))).append(vowels.charAt(random.nextInt(vowels.length())));
            }
            words.add("q" + word); // "q..." keeps the made-up words away from real ones
        }
        return new ArrayList<>(words);
    }

    private static String cut(String text) {
        int end = Math.min(text.length(), LENGTH);
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** 256-character messages: chat-like, random Unicode, and inputs built to hit the expensive paths. */
    static List<String> messages(List<String> words) {
        Random random = new Random(2);
        List<String> out = new ArrayList<>();
        String[] chat = {"hello", "everyone", "how", "are", "you", "today", "lets", "build", "a", "big", "castle", "near", "spawn", "who", "has",
            "diamonds", "for", "trade", "gg", "wp", "that", "was", "fun", "see", "you", "tomorrow", "Привет", "как", "дела", "你好", "こんにちは",
            "what's", "up?", "I'm", "at", "x=120,", "y=64", ":)", "lol!!!", "brb", "the", "nether", "portal", "is", "broken"};
        for (int i = 0; i < 300; i++) {
            StringBuilder text = new StringBuilder();
            while (text.length() < LENGTH) {
                text.append(random.nextInt(25) == 0 ? words.get(random.nextInt(words.size())) : chat[random.nextInt(chat.length)]).append(' ');
            }
            out.add(cut(text.toString()));
        }
        String pool = "abcdefghijklmnopqrstuvwxyzABCDEFXYZ0123456789 @$!|+*.,-_:/()[] асеоруxкмнтіΑαοώ̈​‍­ｆｕｃｋé傻逼の한😀";
        int[] cps = pool.codePoints().toArray();
        for (int i = 0; i < 300; i++) {
            StringBuilder text = new StringBuilder();
            while (text.length() < LENGTH) {
                text.appendCodePoint(cps[random.nextInt(cps.length)]);
            }
            out.add(cut(text.toString()));
        }
        String[] nasty = {
            "*", "a*", "*a", "f*ck ", "****e ", "a***** ", "*a*a*a ", "f u c k ", "a ", "@$!|+", "a$$", "aaaaaaaaaa", "ab", "q", "qb",
            "fuuuuuuuuck ", "f.u.c.k.", "ź̶̈", "а", "aа", "​", "a​", "ｆｕｃｋ ", "傻", "逼傻", ". ", "a.", " dot ", "a dot ",
            "(dot)", "http://", "www.", "1.", ":", "a:", "::", "a.com ", "xx" + words.get(5) + "xx ", words.get(100) + " ", "A", "AB ", "!?",
            "😀", "buy ", "x9x9", "1337 ", "s3x ", "qba*ba ", "*********", "a*b*c*d*e*f ", "q*******", "*q*a*e*i*"};
        for (String unit : nasty) {
            StringBuilder text = new StringBuilder();
            while (text.length() < LENGTH) {
                text.append(unit);
            }
            out.add(cut(text.toString()));
        }
        // the tails of many different words glued together, with and without masks
        for (int i = 0; i < 100; i++) {
            StringBuilder text = new StringBuilder();
            while (text.length() < LENGTH) {
                String w = words.get(random.nextInt(words.size()));
                text.append(w, 0, Math.max(1, w.length() - 1)).append(random.nextBoolean() ? "*" : "").append(random.nextInt(3) == 0 ? " " : "");
            }
            out.add(cut(text.toString()));
        }
        return out;
    }

    private interface Judge {
        void judge(String text);
    }

    /** @return {p50, p99, max} in microseconds; the best of three rounds, so one garbage-collector pause does not decide */
    private static long[] measure(List<String> messages, Judge judge) {
        for (int warm = 0; warm < 12; warm++) {
            messages.forEach(judge::judge);
        }
        long[] best = null;
        for (int round = 0; round < 3; round++) {
            long[] nanos = new long[messages.size() * 6];
            int at = 0;
            for (int repeat = 0; repeat < 6; repeat++) {
                for (String message : messages) {
                    long start = System.nanoTime();
                    judge.judge(message);
                    nanos[at++] = System.nanoTime() - start;
                }
            }
            Arrays.sort(nanos);
            long[] result = {nanos[nanos.length / 2] / 1000, nanos[(int) (nanos.length * 0.99)] / 1000, nanos[nanos.length - 1] / 1000};
            if (best == null || result[1] < best[1]) {
                best = result;
            }
        }
        return best;
    }

    private static void report(String what, int count, long[] r) {
        System.out.println("[performance] " + what + ": " + count + " messages of " + LENGTH + " characters, " + WORDS + " words -> p50 "
            + r[0] + " us, p99 " + r[1] + " us, slowest " + r[2] + " us (budget: p99 < " + BUDGET_MICROS + " us)");
        assertTrue(r[1] < BUDGET_MICROS, what + ": p99 is " + r[1] + " us, the budget is " + BUDGET_MICROS + " us");
    }

    @Test
    void matcherStaysInsideTheBudgetWithTenThousandWords() {
        List<String> words = tenThousandWords();
        List<String> messages = messages(words);
        assertEquals(WORDS, words.size());
        assertTrue(messages.stream().allMatch(m -> m.length() >= LENGTH - 1 && m.length() <= LENGTH));
        WordMatcher exact = new WordMatcher(words, new WordMatcher.Options(true, 3, true, false, false, 4, List.of("class", "grass")));
        WordMatcher partial = new WordMatcher(words, new WordMatcher.Options(true, 3, true, false, true, 4, List.of("class", "grass")));
        WordMatcher everything = new WordMatcher(words, new WordMatcher.Options(true, 2, true, true, true, 3, List.of("class", "grass")));
        assertTrue(exact.size() > 9000, "words indexed: " + exact.size());
        report("word matcher, default options", messages.size(), measure(messages, exact::find));
        report("word matcher, partial-match on", messages.size(), measure(messages, partial::find));
        report("word matcher, partial-match + join-adjacent-words on", messages.size(), measure(messages, everything::find));
        // for comparison only (not checked): the old matcher, which scanned every word for partial and masked matches
        com.jason.plugins.moderra.engine.legacy.LegacyWordMatcher old = new com.jason.plugins.moderra.engine.legacy.LegacyWordMatcher(
            words, new com.jason.plugins.moderra.engine.legacy.LegacyWordMatcher.Options(true, 3, true, false, true, 4, List.of("class", "grass")));
        long[] before = measure(messages, old::find);
        System.out.println("[performance] for comparison, the old scanning matcher with partial-match on: p50 " + before[0] + " us, p99 "
            + before[1] + " us, slowest " + before[2] + " us");
    }

    @Test
    void wholePipelineWithEveryFilterOnStaysInsideTheBudget() throws IOException {
        List<String> words = tenThousandWords();
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        // all 10 000 words as the admin's own list, every filter on, partial matching on
        engine.config().edit("wordlists/custom.yml", editor -> editor.set("level-3", words));
        engine.set(CoreSettings.PARTIAL_MATCH, true);
        engine.set(SpamSettings.ENABLED, true);
        engine.set(SpamSettings.RATE_MAX, 1000);
        engine.set(LinkSettings.MODE, "block-all");
        engine.set(EvasionSettings.DETECT_ACTION, "censor");
        assertTrue(engine.problems().isEmpty(), engine.problems().toString());
        assertTrue(engine.settings().matcher().size() > 9000, "words active: " + engine.settings().matcher().size());
        f.platform.rewritable.add(Message.CHAT);
        UUID bob = f.platform.join("Bob");
        List<String> messages = messages(words);
        int[] blocked = {0};
        long[] result = measure(messages, text -> {
            f.now.addAndGet(700);
            if (!engine.inspect(engine.message(bob, "Bob", Message.CHAT, text)).allowed()) {
                blocked[0]++;
            }
        });
        assertTrue(blocked[0] > 0);
        report("whole pipeline (evasion, bad words with partial-match, links, all spam rules)", messages.size(), result);
    }
}
