package com.jason.plugins.antibadwords;

import java.text.Normalizer;
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
 *       <em>tokens</em> (runs of letters/digits).</li>
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

    /** Characters that may appear inside a token and are translated to a letter ({@code f@ck}). */
    private static final String LEET_SYMBOLS = "@$!|+";
    /** Masking character: matches any single character of a blocked word ({@code f*ck}). */
    private static final char WILDCARD = '*';
    /** Characters that are only meaningful inside a word (stripped when they lead or trail a token). */
    private static final String LEADING_STRIP = "@!|+*";
    private static final String TRAILING_STRIP = "!|+*";

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
    private final Options options;
    private int maxWordsPerEntry = 1;

    public WordMatcher(Collection<String> words, Options options) {
        this.options = options;
        for (String safe : options.whitelist()) {
            String canonical = canonicalEntry(safe);
            if (!canonical.isEmpty()) {
                whitelist.add(canonical);
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
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return null;
        }

        // 1) single tokens (plus masked tokens, plus optional partial matching)
        for (String token : tokens) {
            if (token.length() < 2 || whitelist.contains(token)) {
                continue;
            }
            String hit = lookup(token);
            if (hit != null) {
                return hit;
            }
            if (token.indexOf(WILDCARD) >= 0) {
                hit = lookupMasked(token);
                if (hit != null) {
                    return hit;
                }
            }
            if (options.partialMatch()) {
                hit = lookupPartial(token);
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
            String all = String.join("", tokens);
            for (Map.Entry<String, String> entry : unspaced.entrySet()) {
                if (all.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Index construction
    // ------------------------------------------------------------------------------------------

    private void addEntry(String raw) {
        if (raw == null) {
            return;
        }
        List<String> tokens = tokenize(raw);
        String joined = String.join("", tokens);
        if (joined.codePointCount(0, joined.length()) < 2) {
            return;
        }
        String display = cleanEntry(raw);
        if (containsUnspacedScript(joined)) {
            unspaced.putIfAbsent(joined, display);
            return;
        }
        exact.putIfAbsent(joined, display);
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
        if (token.length() < 3) {
            return null;
        }
        for (Map.Entry<String, String> entry : exact.entrySet()) {
            String word = entry.getKey();
            if (word.length() != token.length() || word.length() < 3) {
                continue;
            }
            boolean same = true;
            boolean hasRealLetter = false;
            for (int i = 0; i < word.length() && same; i++) {
                char t = token.charAt(i);
                if (t == WILDCARD) {
                    continue;
                }
                hasRealLetter = true;
                same = t == word.charAt(i);
            }
            if (same && hasRealLetter) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Opt-in substring matching: the token merely has to contain a blocked word. */
    private String lookupPartial(String token) {
        int minLength = Math.max(3, options.partialMinLength());
        for (Map.Entry<String, String> entry : exact.entrySet()) {
            String word = entry.getKey();
            if (word.length() >= minLength && token.contains(word)) {
                return entry.getValue();
            }
        }
        return null;
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
     * Splits text into canonical tokens. Public for tests; entries from the config go through the exact
     * same pipeline so that both sides of a comparison are always normalised identically.
     */
    static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        if (input == null) {
            return tokens;
        }
        String text = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.FORMAT) {
                continue; // accents and zero-width characters are simply dropped
            }
            if (Character.isLetterOrDigit(cp) || cp == WILDCARD || (cp < 128 && LEET_SYMBOLS.indexOf(cp) >= 0)) {
                raw.appendCodePoint(Character.toLowerCase(cp));
            } else {
                flush(raw, tokens);
            }
        }
        flush(raw, tokens);
        return tokens;
    }

    private static void flush(StringBuilder raw, List<String> tokens) {
        if (raw.length() == 0) {
            return;
        }
        String token = finishToken(raw.toString());
        raw.setLength(0);
        if (!token.isEmpty()) {
            tokens.add(token);
        }
    }

    private static String finishToken(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && LEADING_STRIP.indexOf(token.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRAILING_STRIP.indexOf(token.charAt(end - 1)) >= 0) {
            end--;
        }
        if (start >= end) {
            return "";
        }

        StringBuilder out = new StringBuilder(end - start);
        boolean hasLatin = false;
        boolean hasCyrillic = false;
        for (int i = start; i < end; i++) {
            char c = token.charAt(i);
            switch (c) {
                case '@' -> c = 'a';
                case '$' -> c = 's';
                case '!', '|' -> c = 'i';
                case '+' -> c = 't';
                default -> { }
            }
            if (c >= 'a' && c <= 'z') {
                hasLatin = true;
            } else if (c >= 'Ѐ' && c <= 'ӿ') {
                hasCyrillic = true;
            }
            out.append(c);
        }

        // Cyrillic letters that look like Latin ones, but only inside mixed-script words ("fuсk").
        if (hasLatin && hasCyrillic) {
            for (int i = 0; i < out.length(); i++) {
                out.setCharAt(i, homoglyph(out.charAt(i)));
            }
        }
        // Digits are only treated as leetspeak inside words that already contain letters ("sh1t").
        if (hasLatin) {
            for (int i = 0; i < out.length(); i++) {
                out.setCharAt(i, leetDigit(out.charAt(i)));
            }
        }
        return out.toString();
    }

    private static char leetDigit(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1' -> 'i';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '7' -> 't';
            case '8' -> 'b';
            case '9' -> 'g';
            default -> c;
        };
    }

    private static char homoglyph(char c) {
        return switch (c) {
            case 'а' -> 'a'; // а
            case 'с' -> 'c'; // с
            case 'е' -> 'e'; // е
            case 'о' -> 'o'; // о
            case 'р' -> 'p'; // р
            case 'х' -> 'x'; // х
            case 'у' -> 'y'; // у
            case 'к' -> 'k'; // к
            case 'м' -> 'm'; // м
            case 'н' -> 'h'; // н
            case 'т' -> 't'; // т
            case 'і' -> 'i'; // і
            default -> c;
        };
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
