package com.jason.plugins.antibadwords.engine.filters;

import java.util.Locale;

/** "Is this nearly the same message?" for the repeat check: edit distance turned into a ratio, with a cost limit. */
public final class Similarity {
    /** Longer texts are compared by their first characters only, so one comparison has a fixed upper cost. */
    static final int MAX_LENGTH = 256;

    private Similarity() {
    }

    /** Lower case, runs of white space become one space, trimmed, cut to {@link #MAX_LENGTH}. */
    public static String canonical(String text) {
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_LENGTH));
        boolean space = false;
        for (int i = 0; i < text.length() && out.length() < MAX_LENGTH; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * @param ratio 1.0 = only equal texts; 0.8 = at most 20% of the longer text may differ
     * @return true when {@code 1 - editDistance / longerLength >= ratio}
     */
    public static boolean similar(String a, String b, double ratio) {
        if (a.equals(b)) {
            return true;
        }
        int longer = Math.max(a.length(), b.length());
        int allowed = (int) Math.floor((1.0 - ratio) * longer + 1e-9);
        if (ratio >= 1.0 || allowed <= 0 || Math.abs(a.length() - b.length()) > allowed) {
            return false;
        }
        return withinDistance(a, b, allowed);
    }

    /** Banded Levenshtein: only looks at cells within {@code limit} of the diagonal, so the cost is O(limit * length). */
    static boolean withinDistance(String a, String b, int limit) {
        int n = a.length();
        int m = b.length();
        int big = limit + 1;
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j <= limit ? j : big;
        }
        for (int i = 1; i <= n; i++) {
            int from = Math.max(1, i - limit);
            int to = Math.min(m, i + limit);
            current[from - 1] = from == 1 ? (i <= limit ? i : big) : big;
            int best = current[from - 1];
            char ca = a.charAt(i - 1);
            for (int j = from; j <= to; j++) {
                int cost = ca == b.charAt(j - 1) ? previous[j - 1] : previous[j - 1] + 1;
                cost = Math.min(cost, Math.min(previous[j], current[j - 1]) + 1);
                current[j] = Math.min(cost, big);
                best = Math.min(best, current[j]);
            }
            if (to < m) {
                current[to + 1] = big;
            }
            if (best > limit) {
                return false;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m] <= limit;
    }
}
