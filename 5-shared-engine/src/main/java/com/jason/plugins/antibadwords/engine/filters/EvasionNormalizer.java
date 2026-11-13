package com.jason.plugins.antibadwords.engine.filters;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns text into the canonical word tokens the bad-word matcher compares. This is where bypass tricks are undone:
 * invisible characters, full-width and other compatibility forms, accents, look-alike letters from other alphabets,
 * leetspeak. Every step can be switched on and off ({@code filters/evasion.yml}); blocked words from the word lists go
 * through exactly the same steps, so both sides of a comparison are always normalised identically.
 *
 * <p>Immutable and thread-safe. {@link #LEGACY} behaves exactly like version 1 of the plugin.
 */
public final class EvasionNormalizer {
    /** Masking character: stands for one arbitrary character of a blocked word ({@code f*ck}); always part of a token. */
    public static final char WILDCARD = '*';

    /**
     * @param stripInvisible       drop zero-width, direction-control, soft-hyphen and variation-selector characters
     * @param stripFillers         also drop invisible "letters" (Hangul fillers) - not done by version 1
     * @param compatibilityFold    fold full-width, circled, mathematical ... forms to the plain character (Unicode NFKC/NFKD)
     * @param foldDiacritics       remove accents and other combining marks
     * @param keepDiacritics       scripts whose marks are kept even when {@code foldDiacritics} is on
     * @param lookAlikes           map look-alike letters to Latin inside words that also contain Latin letters
     * @param lookAlikesReverse    also try the other direction (Latin letters inside a Cyrillic/Greek ... word)
     * @param lookAlikesWholeWords also try words written completely in look-alike letters
     * @param leet                 treat leetspeak symbols and digits as letters
     * @param leetSymbols          symbol -> letter ({@code @ -> a}); the symbols may stand inside a word
     * @param noStart              leet symbols that are cut off at the start of a word
     * @param noEnd                leet symbols that are cut off at the end of a word
     * @param leetDigits           digit -> letter ({@code 0 -> o}); only inside words that contain Latin letters
     * @param ignoredSeparators    characters that are skipped when they stand between two letters ({@code fu.ck})
     */
    public record Options(boolean stripInvisible, boolean stripFillers, boolean compatibilityFold, boolean foldDiacritics,
                          Set<Character.UnicodeScript> keepDiacritics, boolean lookAlikes, boolean lookAlikesReverse,
                          boolean lookAlikesWholeWords, boolean leet, Map<Character, Character> leetSymbols, String noStart,
                          String noEnd, Map<Character, Character> leetDigits, String ignoredSeparators, Confusables table) {
        public Options {
            keepDiacritics = Set.copyOf(keepDiacritics);
            leetSymbols = Map.copyOf(leetSymbols);
            leetDigits = Map.copyOf(leetDigits);
        }
    }

    public static final Map<Character, Character> DEFAULT_LEET_SYMBOLS = Map.of('@', 'a', '$', 's', '!', 'i', '|', 'i', '+', 't');
    public static final Map<Character, Character> DEFAULT_LEET_DIGITS =
        Map.of('0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's', '7', 't', '8', 'b', '9', 'g');
    public static final String DEFAULT_NO_START = "@!|+";
    public static final String DEFAULT_NO_END = "!|+";

    /** Exactly the hard-coded behaviour of version 1 (used where no configuration is at hand, and by the tests as the reference). */
    public static final EvasionNormalizer LEGACY = new EvasionNormalizer(new Options(true, false, true, true, Set.of(), true, false, false,
        true, DEFAULT_LEET_SYMBOLS, DEFAULT_NO_START, DEFAULT_NO_END, DEFAULT_LEET_DIGITS, "", Confusables.LEGACY));

    private final Options o;

    public EvasionNormalizer(Options options) {
        this.o = options;
    }

    public Options options() {
        return o;
    }

    /** Zero-width, direction-control and soft-hyphen characters (Unicode "format"), variation selectors and the grapheme joiner. */
    public static boolean isInvisible(int cp) {
        return Character.getType(cp) == Character.FORMAT || isVariationSelector(cp) || cp == 0x034F;
    }

    static boolean isVariationSelector(int cp) {
        return (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF) || (cp >= 0x180B && cp <= 0x180D);
    }

    /** Letters that draw nothing (Hangul fillers). */
    public static boolean isFiller(int cp) {
        return cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0;
    }

    static boolean isMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
    }

    /** The canonical tokens of a text. */
    public List<String> tokenize(String input) {
        return tokenize(input, null);
    }

    /**
     * @param alternates when not {@code null}: receives, for every returned token, a second spelling to try as well
     *                   (look-alike letters read the other way round) or {@code null}
     */
    public List<String> tokenize(String input, List<String> alternates) {
        List<String> tokens = new ArrayList<>();
        if (input == null) {
            return tokens;
        }
        String text = Normalizer.normalize(input, o.compatibilityFold() ? Normalizer.Form.NFKD : Normalizer.Form.NFD);
        int[] raw = new int[Math.min(text.length(), 64) + 1];
        int length = 0;
        Character.UnicodeScript baseScript = Character.UnicodeScript.COMMON;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean invisible = isInvisible(cp) || (o.stripFillers() && isFiller(cp));
            if (invisible && o.stripInvisible()) {
                continue;
            }
            if (!invisible && isMark(cp)) {
                if (length > 0 && keepsMark(cp, baseScript)) {
                    raw = append(raw, length++, cp);
                }
                continue;
            }
            if (cp >= 128 && o.lookAlikes() && !invisible) {
                String plain = o.table().always(cp); // small capitals and the like: a plain letter wherever they stand
                if (plain != null) {
                    for (int k = 0; k < plain.length(); k++) {
                        raw = append(raw, length++, plain.charAt(k));
                    }
                    baseScript = Character.UnicodeScript.LATIN;
                    continue;
                }
            }
            boolean wordChar = !invisible && (Character.isLetterOrDigit(cp) || cp == WILDCARD
                || (o.leet() && cp < 128 && o.leetSymbols().containsKey((char) cp)));
            if (wordChar) {
                raw = append(raw, length++, cp);
                baseScript = Character.UnicodeScript.of(cp);
                continue;
            }
            if (!invisible && length > 0 && cp < 0x10000 && o.ignoredSeparators().indexOf(cp) >= 0 && i < text.length()
                && Character.isLetterOrDigit(text.codePointAt(i))) {
                continue; // "fu.ck": the separator between two letters is skipped
            }
            if (length > 0) {
                finish(raw, length, tokens, alternates);
                length = 0;
            }
        }
        if (length > 0) {
            finish(raw, length, tokens, alternates);
        }
        return tokens;
    }

    private boolean keepsMark(int mark, Character.UnicodeScript baseScript) {
        if (!o.foldDiacritics()) {
            return true;
        }
        if (o.keepDiacritics().isEmpty()) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(mark);
        if (script == Character.UnicodeScript.INHERITED || script == Character.UnicodeScript.COMMON) {
            script = baseScript;
        }
        return o.keepDiacritics().contains(script);
    }

    private static int[] append(int[] buffer, int at, int cp) {
        if (at == buffer.length) {
            buffer = java.util.Arrays.copyOf(buffer, buffer.length * 2);
        }
        buffer[at] = cp;
        return buffer;
    }

    private void finish(int[] raw, int length, List<String> tokens, List<String> alternates) {
        int start = 0;
        int end = length;
        while (start < end && edge(raw[start], o.noStart())) {
            start++;
        }
        while (end > start && edge(raw[end - 1], o.noEnd())) {
            end--;
        }
        if (start >= end) {
            return;
        }
        // lower-case, leet symbols to letters
        int[] lower = new int[end - start];
        boolean hasLatin = false;
        boolean hasForeign = false;
        for (int i = start; i < end; i++) {
            int c = Character.toLowerCase(raw[i]);
            if (o.leet() && c < 128) {
                Character letter = o.leetSymbols().get((char) c);
                if (letter != null) {
                    c = letter;
                }
            }
            if (c >= 'a' && c <= 'z') {
                hasLatin = true;
            } else if (c >= 128 && Character.isLetter(c)) {
                hasForeign = true;
            }
            lower[i - start] = c;
        }

        StringBuilder out = new StringBuilder(lower.length + 4);
        String alternate = null;
        if (hasLatin && hasForeign && o.lookAlikes()) {
            // look-alike letters inside a word that also has Latin letters ("fuсk" with a Cyrillic с)
            for (int i = 0; i < lower.length; i++) {
                String latin = lower[i] < 128 ? null : o.table().toLatin(raw[start + i]);
                if (latin != null) {
                    out.append(latin);
                } else {
                    out.appendCodePoint(lower[i]);
                }
            }
            if (o.lookAlikesReverse()) {
                alternate = reverse(lower);
            }
        } else {
            for (int c : lower) {
                out.appendCodePoint(c);
            }
            if (!hasLatin && hasForeign && o.lookAlikes() && o.lookAlikesWholeWords()) {
                alternate = wholeWord(raw, start, lower);
            }
        }
        if (hasLatin && o.leet()) {
            leetDigits(out);
        }
        tokens.add(out.toString());
        if (alternates != null) {
            alternates.add(alternate);
        }
    }

    private boolean edge(int cp, String strip) {
        return cp == WILDCARD || (o.leet() && cp < 128 && strip.indexOf(cp) >= 0);
    }

    private void leetDigits(StringBuilder out) {
        for (int i = 0; i < out.length(); i++) {
            Character letter = o.leetDigits().get(out.charAt(i));
            if (letter != null) {
                out.setCharAt(i, letter);
            }
        }
    }

    /** "xуй" with a Latin x: every Latin letter becomes its look-alike in the script of the other letters, or {@code null}. */
    private String reverse(int[] lower) {
        Character.UnicodeScript script = null;
        for (int c : lower) {
            if (c >= 128 && Character.isLetter(c)) {
                script = Character.UnicodeScript.of(c);
                break;
            }
        }
        StringBuilder alt = new StringBuilder(lower.length);
        for (int c : lower) {
            if (c >= 'a' && c <= 'z') {
                int other = o.table().fromLatin(script, (char) c);
                if (other < 0) {
                    return null;
                }
                alt.appendCodePoint(other);
            } else {
                alt.appendCodePoint(c);
            }
        }
        return alt.toString();
    }

    /** A word written completely in look-alike letters ("сосо" in Cyrillic): its Latin reading, or {@code null}. */
    private String wholeWord(int[] raw, int start, int[] lower) {
        StringBuilder alt = new StringBuilder(lower.length);
        for (int i = 0; i < lower.length; i++) {
            if (lower[i] < 128) {
                alt.appendCodePoint(lower[i]);
                continue;
            }
            String latin = o.table().toLatin(raw[start + i]);
            if (latin == null) {
                return null;
            }
            alt.append(latin);
        }
        if (o.leet()) {
            leetDigits(alt);
        }
        return alt.toString();
    }
}
