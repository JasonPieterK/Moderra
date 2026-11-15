package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.filters.EvasionNormalizer;
import com.jason.plugins.antibadwords.engine.filters.WordIndex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, thread-safe profanity matcher.
 *
 * <p>The matcher is deliberately free of any Bukkit/Paper dependency so it can be unit tested and
 * safely used from async chat threads. A new instance is built every time the config is (re)loaded.
 *
 * <h2>How text is matched</h2>
 * <ol>
 *   <li>The input is Unicode-normalised (accents and zero-width characters are removed) and split into
 *       <em>tokens</em> (runs of letters/digits) by the {@link EvasionNormalizer}; its steps are switched in
 *       {@code filters/evasion.yml}.</li>
 *   <li>Each token is canonicalised: leetspeak symbols ({@code @ $ ! | +}) and digits are mapped back to
 *       letters, Cyrillic look-alikes inside Latin words are mapped to Latin, and long runs of the same
 *       letter ({@code fuuuuck}) are collapsed.</li>
 *   <li>A token is blocked only when it <strong>equals</strong> a blocked word. This avoids the classic
 *       "Scunthorpe problem" (for example {@code class}, {@code closer} or {@code scrap} being flagged).
 *       Substring matching is available as an opt-in ({@code partial-match}) together with a whitelist.</li>
 *   <li>Common bypass tricks are handled separately: spaced letters ({@code f u c k}), masked words
 *       ({@code f*ck}), multi-word entries ({@code fick dich}) and scripts that are written without
 *       spaces (Chinese, Japanese, Thai, ...).</li>
 * </ol>
 */
public final class WordMatcher {

    /** Masking character: matches any single character of a blocked word ({@code f*ck}). */
    private static final char WILDCARD = EvasionNormalizer.WILDCARD;

    /** Tunable matching behaviour, read from {@code filters.matching.*} in config.yml. */
    public record Options(
        boolean collapseRepeats,
        int repeatThreshold,
        boolean detectSpacedLetters,
        boolean aggressiveJoin,
        boolean partialMatch,
        int partialMinLength,
        Collection<String> whitelist
    ) {
        public static Options defaults() {
            return new Options(true, 3, true, false, false, 4, List.of());
        }
    }

    /** canonical word (spaces removed) -> original word as written in the config. */
    private final Map<String, String> exact = new LinkedHashMap<>();
    /** Subset of {@link #exact} that came from multi-word entries ("fick dich"). */
    private final Map<String, String> multiWord = new LinkedHashMap<>();
    /** Entries written in scripts that have no word spacing; matched as substrings. */
    private final Map<String, String> unspaced = new LinkedHashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    /** Whitelist entries written in unspaced scripts (Han, kana, Hangul, Thai ...): they suppress hits that lie inside them. */
    private final List<String> unspacedWhitelist = new ArrayList<>();
    private final Options options;
    private final EvasionNormalizer normalizer;
    /** Index over {@link #exact} for masked ({@code f*ck}) and partial matching. */
    private final WordIndex exactIndex = new WordIndex();
    /** Index over {@link #unspaced}. */
    private final WordIndex unspacedIndex = new WordIndex();
    private int maxWordsPerEntry = 1;

    /** A matcher that normalises text exactly like version 1 of the plugin did. */
    public WordMatcher(Collection<String> words, Options options) {
        this(words, options, EvasionNormalizer.LEGACY);
    }

    /**
     * @param normalizer how text and blocked words are turned into canonical tokens (the evasion settings)
     */
    public WordMatcher(Collection<String> words, Options options, EvasionNormalizer normalizer) {
        this.options = options;
        this.normalizer = normalizer;
        for (String safe : options.whitelist()) {
            String canonical = String.join("", normalizer.tokenize(safe));
            if (!canonical.isEmpty()) {
                whitelist.add(canonical);
                if (containsUnspacedScript(canonical)) {
                    unspacedWhitelist.add(canonical);
                }
            }
        }
        for (String word : words) {
            addEntry(word);
        }
    }

    /** Number of distinct blocked words this matcher knows about. */
    public int size() {
        return exact.size() + unspaced.size();
    }

    /**
     * Returns {@code true} when the given text could be turned into a usable blocked word
     * (used to validate {@code /abw addword}).
     */
    public static boolean isValidEntry(String raw) {
        return raw != null && canonicalEntry(raw).length() >= 2;
    }

    /** Cleans up a word typed by an admin so it can be stored in the config. */
    public static String cleanEntry(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Finds the first blocked word inside {@code text}.
     *
     * @return the blocked word exactly as written in the config, or {@code null} when the text is clean
     */
    public String find(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> alternates = new ArrayList<>();
        List<String> tokens = normalizer.tokenize(text, alternates);
        if (tokens.isEmpty()) {
            return null;
        }

        // 1) single tokens (plus masked tokens, plus optional partial matching)
        for (int t = 0; t < tokens.size(); t++) {
            String token = tokens.get(t);
            if (token.length() < 2 || whitelist.contains(token)) {
                continue;
            }
            String hit = lookupSingle(token);
            if (hit != null) {
                return hit;
            }
            // the same word with its look-alike letters read the other way round ("xуй" with a Latin x)
            String alternate = alternates.get(t);
            if (alternate != null && !whitelist.contains(alternate)) {
                hit = lookupSingle(alternate);
                if (hit != null) {
                    return hit;
                }
            }
        }

        // 2) letters separated by spaces/punctuation: "f u c k", "f.u.c.k"
        if (options.detectSpacedLetters()) {
            StringBuilder run = new StringBuilder();
            int runLength = 0;
            for (int i = 0; i <= tokens.size(); i++) {
                String token = i < tokens.size() ? tokens.get(i) : "";
                if (token.codePointCount(0, token.length()) == 1 && Character.isLetter(token.codePointAt(0))) {
                    run.append(token);
                    runLength++;
                    continue;
                }
                if (runLength >= 3) {
                    String hit = lookup(run.toString());
                    if (hit != null) {
                        return hit;
                    }
                }
                run.setLength(0);
                runLength = 0;
            }
        }

        // 3) multi-word entries ("fick dich") and, optionally, any adjacent words glued together
        int maxWindow = options.aggressiveJoin() ? Math.max(3, maxWordsPerEntry) : maxWordsPerEntry;
        Map<String, String> windowIndex = options.aggressiveJoin() ? exact : multiWord;
        if (maxWindow >= 2 && !windowIndex.isEmpty()) {
            for (int size = 2; size <= maxWindow; size++) {
                for (int start = 0; start + size <= tokens.size(); start++) {
                    StringBuilder joined = new StringBuilder();
                    for (int k = start; k < start + size; k++) {
                        joined.append(tokens.get(k));
                    }
                    String hit = windowIndex.get(joined.toString());
                    if (hit != null && !whitelist.contains(joined.toString())) {
                        return hit;
                    }
                }
            }
        }

        // 4) scripts written without spaces (Chinese, Japanese, Thai, ...)
        if (!unspaced.isEmpty()) {
            String joined = String.join("", tokens);
            if (unspacedWhitelist.isEmpty()) {
                return unspacedIndex.firstContained(joined, 1);
            }
            // a whitelist entry that contains the found region (你妈妈 around 你妈) suppresses that hit
            List<int[]> safe = new ArrayList<>();
            for (String entry : unspacedWhitelist) {
                for (int at = joined.indexOf(entry); at >= 0; at = joined.indexOf(entry, at + 1)) {
                    safe.add(new int[] {at, at + entry.length()});
                }
            }
            if (safe.isEmpty()) {
                return unspacedIndex.firstContained(joined, 1);
            }
            return unspacedIndex.firstContained(joined, 1, (start, end) -> {
                for (int[] range : safe) {
                    if (range[0] <= start && end <= range[1]) {
                        return false;
                    }
                }
                return true;
            });
        }
        return null;
    }

    /** Everything that is tried for one token: exact, masked ({@code f*ck}) and, when switched on, partial. */
    private String lookupSingle(String token) {
        String hit = lookup(token);
        if (hit == null && token.indexOf(WILDCARD) >= 0) {
            hit = lookupMasked(token);
        }
        if (hit == null && options.partialMatch()) {
            hit = lookupPartial(token);
        }
        return hit;
    }

    // ------------------------------------------------------------------------------------------
    // Index construction
    // ------------------------------------------------------------------------------------------

    private void addEntry(String raw) {
        if (raw == null) {
            return;
        }
        List<String> tokens = normalizer.tokenize(raw);
        String joined = String.join("", tokens);
        if (joined.codePointCount(0, joined.length()) < 2) {
            return;
        }
        String display = cleanEntry(raw);
        if (containsUnspacedScript(joined)) {
            if (unspaced.putIfAbsent(joined, display) == null) {
                unspacedIndex.add(joined, display);
            }
            return;
        }
        if (exact.putIfAbsent(joined, display) == null) {
            exactIndex.add(joined, display);
        }
        if (tokens.size() >= 2) {
            multiWord.putIfAbsent(joined, display);
            maxWordsPerEntry = Math.max(maxWordsPerEntry, Math.min(tokens.size(), 5));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------------------------------

    /** Exact lookup that also tries the "collapsed repeated letters" variants of the token. */
    private String lookup(String token) {
        String hit = exact.get(token);
        if (hit != null) {
            return hit;
        }
        if (!options.collapseRepeats()) {
            return null;
        }
        int threshold = Math.max(2, options.repeatThreshold());
        // "fuuuuck" -> "fuck"   (every long run becomes a single letter)
        String single = collapse(token, threshold, 1);
        if (!single.equals(token)) {
            hit = exact.get(single);
            if (hit != null) {
                return hit;
            }
        }
        // "assssshole" -> "asshole"   (every long run becomes a double letter)
        String pair = collapse(token, threshold, 2);
        if (!pair.equals(token) && !pair.equals(single)) {
            hit = exact.get(pair);
        }
        return hit;
    }

    /** {@code f*ck}: the masking character stands for exactly one arbitrary character. */
    private String lookupMasked(String token) {
        return exactIndex.firstMasked(token, WILDCARD);
    }

    /**
     * Opt-in substring matching: the token merely has to contain a blocked word. Like {@link #lookup}, it also tries
     * the "collapsed repeated letters" variants, so {@code xxfuuuuuuckxx} is caught just like {@code xxfuckxx}.
     */
    private String lookupPartial(String token) {
        String hit = lookupPartialExact(token);
        if (hit != null || !options.collapseRepeats()) {
            return hit;
        }
        int threshold = Math.max(2, options.repeatThreshold());
        String single = collapse(token, threshold, 1);
        if (!single.equals(token)) {
            hit = lookupPartialExact(single);
            if (hit != null) {
                return hit;
            }
        }
        String pair = collapse(token, threshold, 2);
        if (!pair.equals(token) && !pair.equals(single)) {
            hit = lookupPartialExact(pair);
        }
        return hit;
    }

    private String lookupPartialExact(String token) {
        return exactIndex.firstContained(token, Math.max(3, options.partialMinLength()));
    }

    // ------------------------------------------------------------------------------------------
    // Normalisation
    // ------------------------------------------------------------------------------------------

    private static String canonicalEntry(String raw) {
        return String.join("", tokenize(raw));
    }

    /** Collapses every run of {@code threshold}+ identical characters down to {@code keep} characters. */
    static String collapse(String input, int threshold, int keep) {
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            int cp = input.codePointAt(i);
            int width = Character.charCount(cp);
            int j = i + width;
            int run = 1;
            while (j < input.length() && input.codePointAt(j) == cp) {
                j += width;
                run++;
            }
            int emit = run >= threshold ? Math.min(keep, run) : run;
            for (int n = 0; n < emit; n++) {
                out.appendCodePoint(cp);
            }
            i = j;
        }
        return out.toString();
    }

    /**
     * Splits text into canonical tokens the way version 1 did (no configuration). Entries from the word lists go
     * through the same normaliser as the text, so both sides of a comparison are always normalised identically.
     */
    static List<String> tokenize(String input) {
        return EvasionNormalizer.LEGACY.tokenize(input);
    }

    /** The normaliser this matcher uses for text and for its blocked words. */
    public EvasionNormalizer normalizer() {
        return normalizer;
    }

    private static boolean containsUnspacedScript(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            switch (script) {
                case HAN, HIRAGANA, KATAKANA, THAI, LAO, KHMER, MYANMAR, TIBETAN -> {
                    return true;
                }
                default -> { }
            }
        }
        return false;
    }
}
