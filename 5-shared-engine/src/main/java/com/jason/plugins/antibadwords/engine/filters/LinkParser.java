package com.jason.plugins.antibadwords.engine.filters;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds links in a text: URLs with a scheme, {@code www.} addresses, bare domains with a known ending, IPv4 and IPv6
 * addresses, and (optionally) the usual ways of hiding the dot: {@code example dot com}, {@code example(dot)com},
 * {@code example[.]com}, {@code example .com}, {@code example . com}, full-width and other Unicode dots, invisible
 * characters inside the address.
 *
 * <p>Hand-written scanner (no regular expressions), linear in the length of the text. Stateless and thread-safe.
 */
public final class LinkParser {
    public enum Kind {
        /** A domain name, with or without scheme and path. */
        DOMAIN,
        IPV4,
        IPV6
    }

    /**
     * One link found in a text.
     *
     * @param start      first character of the link in the original text
     * @param end        one past its last character in the original text
     * @param host       the domain or address, lower-case, without {@code www.}
     * @param path       what follows the host (starts with {@code /}), or ""
     * @param obfuscated true when the dot was hidden ({@code dot}, brackets, spaces)
     */
    public record Link(int start, int end, Kind kind, String host, String path, boolean obfuscated) {
    }

    /**
     * @param endings          domain endings (without dot) that make a bare {@code name.ending} a link
     * @param dotWords         words that stand for a dot ({@code dot})
     * @param detectObfuscated whether hidden dots are undone
     */
    public record Options(Set<String> endings, List<String> dotWords, boolean detectObfuscated) {
    }

    private static final String DOT_LIKE = "。｡‧∙•·⋅●◦⸱・܁܂";
    private static final String OPEN = "[({<";
    private static final String CLOSE = "])}>";

    /** Text with, for every character, the span of the original text it came from. */
    private static final class Mapped {
        char[] chars;
        int[] from;
        int[] to;
        boolean[] hidden; // this dot was written in a hidden way
        int length;

        Mapped(int capacity) {
            chars = new char[capacity + 8];
            from = new int[capacity + 8];
            to = new int[capacity + 8];
            hidden = new boolean[capacity + 8];
        }

        void add(char c, int start, int end, boolean wasHidden) {
            if (length == chars.length) {
                int size = length * 2;
                chars = java.util.Arrays.copyOf(chars, size);
                from = java.util.Arrays.copyOf(from, size);
                to = java.util.Arrays.copyOf(to, size);
                hidden = java.util.Arrays.copyOf(hidden, size);
            }
            chars[length] = c;
            from[length] = start;
            to[length] = end;
            hidden[length] = wasHidden;
            length++;
        }
    }

    private LinkParser() {
    }

    /** Every link in the text, in order of appearance. */
    public static List<Link> find(String text, Options options) {
        List<Link> out = new ArrayList<>();
        if (text == null || text.length() < 3) {
            return out;
        }
        Mapped m = normalise(text);
        if (options.detectObfuscated()) {
            m = revealDots(m, options);
        }
        scanHosts(m, options, out);
        scanIpv6(m, out);
        out.sort(java.util.Comparator.comparingInt(Link::start));
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Step 1: lower case, compatibility forms folded, invisible characters dropped, Unicode dots -> "."
    // ------------------------------------------------------------------------------------------

    private static Mapped normalise(String text) {
        Mapped m = new Mapped(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            if (EvasionNormalizer.isInvisible(cp) || EvasionNormalizer.isFiller(cp)) {
                i = next;
                continue;
            }
            if (cp < 128) {
                m.add((char) Character.toLowerCase(cp), i, next, false);
            } else if (cp < 0x10000 && DOT_LIKE.indexOf(cp) >= 0) {
                m.add('.', i, next, true);
            } else {
                String folded = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
                boolean changed = folded.length() == 1 && folded.charAt(0) < 128;
                for (int k = 0; k < folded.length(); k++) {
                    char c = folded.charAt(k);
                    m.add(Character.isWhitespace(c) || Character.isSpaceChar(c) ? ' ' : c, i, next, changed && c == '.');
                }
            }
            i = next;
        }
        return m;
    }

    // ------------------------------------------------------------------------------------------
    // Step 2: "example dot com", "example(dot)com", "example[.]com", "example .com" -> "example.com"
    // ------------------------------------------------------------------------------------------

    private static Mapped revealDots(Mapped in, Options options) {
        Mapped out = new Mapped(in.length);
        int i = 0;
        while (i < in.length) {
            char c = in.chars[i];
            boolean afterWord = out.length > 0 && hostLetter(out.chars[out.length - 1]);
            if (afterWord && (c == ' ' || OPEN.indexOf(c) >= 0 || startsDotWord(in, i, options) > 0)) {
                int resume = hiddenDot(in, i, options);
                if (resume > 0) {
                    out.add('.', in.from[i], in.to[resume - 1], true);
                    i = resume;
                    continue;
                }
            }
            out.add(c, in.from[i], in.to[i], in.hidden[i]);
            i++;
        }
        return out;
    }

    /** @return index after the hidden dot that starts at {@code i} (the next character belongs to the address), or -1 */
    private static int hiddenDot(Mapped in, int i, Options options) {
        int j = skipSpaces(in, i);
        boolean spaceBefore = j > i;
        if (j >= in.length) {
            return -1;
        }
        // [.] (.) {.} <.> [dot] (dot) ...
        if (OPEN.indexOf(in.chars[j]) >= 0) {
            int k = skipSpaces(in, j + 1);
            int word = k < in.length && in.chars[k] == '.' ? 1 : startsDotWord(in, k, options);
            if (word <= 0) {
                return -1;
            }
            k = skipSpaces(in, k + word);
            if (k >= in.length || CLOSE.indexOf(in.chars[k]) < 0) {
                return -1;
            }
            k = skipSpaces(in, k + 1);
            return k < in.length && hostLetter(in.chars[k]) ? k : -1;
        }
        // " dot com": the word must stand alone and a known ending must follow
        int word = startsDotWord(in, j, options);
        if (word > 0 && spaceBefore) {
            int k = skipSpaces(in, j + word);
            return k > j + word && endingFollows(in, k, options) ? k : -1;
        }
        // " .com" and " . com" (a space before the dot; "end of sentence. com" is left alone)
        if (in.chars[j] == '.' && spaceBefore) {
            int k = skipSpaces(in, j + 1);
            return endingFollows(in, k, options) ? k : -1;
        }
        return -1;
    }

    private static int skipSpaces(Mapped in, int i) {
        while (i < in.length && in.chars[i] == ' ') {
            i++;
        }
        return i;
    }

    /** @return length of the dot word that starts at {@code i} and ends at a word boundary, or 0 */
    private static int startsDotWord(Mapped in, int i, Options options) {
        for (String word : options.dotWords()) {
            int n = word.length();
            if (n == 0 || i + n > in.length) {
                continue;
            }
            boolean same = true;
            for (int k = 0; k < n && same; k++) {
                same = in.chars[i + k] == word.charAt(k);
            }
            if (same && (i + n == in.length || !Character.isLetterOrDigit(in.chars[i + n]))) {
                return n;
            }
        }
        return 0;
    }

    /** Whether the word that starts at {@code i} is a known domain ending. */
    private static boolean endingFollows(Mapped in, int i, Options options) {
        int end = i;
        while (end < in.length && Character.isLetterOrDigit(in.chars[end])) {
            end++;
        }
        return end > i && end - i <= 24 && options.endings().contains(new String(in.chars, i, end - i));
    }

    // ------------------------------------------------------------------------------------------
    // Step 3: domains, URLs and IPv4 addresses
    // ------------------------------------------------------------------------------------------

    private static boolean hostLetter(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static boolean hostChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-';
    }

    private static void scanHosts(Mapped m, Options options, List<Link> out) {
        int i = 0;
        while (i < m.length) {
            if (!hostChar(m.chars[i])) {
                i++;
                continue;
            }
            int runStart = i;
            while (i < m.length && hostChar(m.chars[i])) {
                i++;
            }
            int a = runStart;
            int b = i;
            while (a < b && !hostLetter(m.chars[a])) {
                a++;
            }
            while (b > a && !hostLetter(m.chars[b - 1])) {
                b--;
            }
            if (b - a < 3 && !(b > a && schemeBefore(m, a) > 0)) {
                continue;
            }
            String host = new String(m.chars, a, b - a);
            int scheme = schemeBefore(m, a);
            Kind kind = null;
            if (isIpv4(host)) {
                kind = Kind.IPV4;
            } else if (scheme > 0 && validLabels(host, 1)) {
                kind = Kind.DOMAIN;
            } else if (host.startsWith("www.") && validLabels(host, 2) && host.length() > 5) {
                kind = Kind.DOMAIN;
            } else if (validLabels(host, 2) && options.endings().contains(host.substring(host.lastIndexOf('.') + 1))) {
                kind = Kind.DOMAIN;
            }
            if (kind == null) {
                continue;
            }
            // optional :port and /path up to the next space
            int end = b;
            if (end < m.length && m.chars[end] == ':') {
                int p = end + 1;
                while (p < m.length && m.chars[p] >= '0' && m.chars[p] <= '9') {
                    p++;
                }
                if (p > end + 1) {
                    end = p;
                }
            }
            String path = "";
            if (end < m.length && (m.chars[end] == '/' || m.chars[end] == '?' || m.chars[end] == '#')) {
                int p = end;
                while (p < m.length && m.chars[p] != ' ') {
                    p++;
                }
                path = new String(m.chars, end, p - end);
                end = p;
                i = Math.max(i, p);
            }
            boolean hidden = false;
            for (int k = a; k < b; k++) {
                hidden |= m.hidden[k];
            }
            int start = scheme > 0 ? a - scheme : a;
            out.add(new Link(m.from[start], m.to[end - 1], kind, host.startsWith("www.") ? host.substring(4) : host, path, hidden));
        }
    }

    /** @return length of {@code scheme://} that ends right before {@code at}, or 0 */
    private static int schemeBefore(Mapped m, int at) {
        if (at < 4 || m.chars[at - 1] != '/' || m.chars[at - 2] != '/' || m.chars[at - 3] != ':') {
            return 0;
        }
        int s = at - 3;
        while (s > 0 && m.chars[s - 1] >= 'a' && m.chars[s - 1] <= 'z' && at - 3 - s < 8) {
            s--;
        }
        return at - 3 - s >= 2 ? at - s : 0;
    }

    private static boolean validLabels(String host, int minLabels) {
        int labels = 0;
        int labelStart = 0;
        boolean letter = false;
        for (int i = 0; i <= host.length(); i++) {
            if (i == host.length() || host.charAt(i) == '.') {
                int n = i - labelStart;
                if (n == 0 || n > 63 || host.charAt(labelStart) == '-' || host.charAt(i - 1) == '-') {
                    return false;
                }
                labels++;
                labelStart = i + 1;
            } else if (Character.isLetter(host.charAt(i))) {
                letter = true;
            }
        }
        return labels >= minLabels && letter && host.length() <= 253;
    }

    static boolean isIpv4(String host) {
        int parts = 0;
        int value = 0;
        int digits = 0;
        for (int i = 0; i <= host.length(); i++) {
            if (i == host.length() || host.charAt(i) == '.') {
                if (digits == 0 || value > 255) {
                    return false;
                }
                parts++;
                value = 0;
                digits = 0;
            } else {
                char c = host.charAt(i);
                if (c < '0' || c > '9' || ++digits > 3) {
                    return false;
                }
                value = value * 10 + (c - '0');
            }
        }
        return parts == 4;
    }

    // ------------------------------------------------------------------------------------------
    // Step 4: IPv6 addresses
    // ------------------------------------------------------------------------------------------

    private static boolean hex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    private static void scanIpv6(Mapped m, List<Link> out) {
        int i = 0;
        while (i < m.length) {
            char c = m.chars[i];
            if (!hex(c) && c != ':') {
                i++;
                continue;
            }
            int start = i;
            int colons = 0;
            while (i < m.length && (hex(m.chars[i]) || m.chars[i] == ':')) {
                colons += m.chars[i] == ':' ? 1 : 0;
                i++;
            }
            if (colons < 2) {
                continue;
            }
            boolean boundaryBefore = start == 0 || !Character.isLetterOrDigit(m.chars[start - 1]);
            boolean boundaryAfter = i == m.length || !Character.isLetterOrDigit(m.chars[i]);
            int a = start;
            int b = i;
            // a single ":" at an end belongs to the sentence, "::" belongs to the address
            if (b - a > 2 && m.chars[b - 1] == ':' && m.chars[b - 2] != ':') {
                b--;
            }
            if (b - a > 2 && m.chars[a] == ':' && m.chars[a + 1] != ':') {
                a++;
            }
            String candidate = new String(m.chars, a, b - a);
            if (boundaryBefore && boundaryAfter && isIpv6(candidate)) {
                out.add(new Link(m.from[a], m.to[b - 1], Kind.IPV6, candidate, "", false));
            }
        }
    }

    /**
     * Strict enough for chat: eight groups, or a {@code ::} shortening with at least three groups ("::1" alone and
     * things like "a::b" are not treated as addresses, they are too easy to type by accident).
     */
    static boolean isIpv6(String text) {
        int doubleColon = text.indexOf("::");
        if (doubleColon >= 0 && text.indexOf("::", doubleColon + 1) >= 0) {
            return false;
        }
        int groups = 0;
        int digits = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ':') {
                if (digits > 0) {
                    groups++;
                } else if (doubleColon < 0 || (i != doubleColon && i != doubleColon + 1 && !(i == text.length() && doubleColon == text.length() - 2))) {
                    return false; // empty group that is not part of "::"
                }
                digits = 0;
            } else if (!hex(text.charAt(i)) || ++digits > 4) {
                return false;
            }
        }
        return doubleColon < 0 ? groups == 8 : groups >= 3 && groups <= 7;
    }
}
