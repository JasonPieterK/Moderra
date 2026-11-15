package com.jason.plugins.moderra.engine.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast look-ups over the blocked words, so a message costs the same whether 50 or 50 000 words are loaded:
 * <ul>
 *   <li>{@link #firstContained}: which word is contained in a text (a character trie, walked from every position),</li>
 *   <li>{@link #firstMasked}: which word fits a masked token such as {@code f*ck} (one bit set per length, position and
 *       character).</li>
 * </ul>
 * Both answer with the word that was added first, like the old "scan every entry in order" code did.
 * Build once, then read from any thread.
 */
public final class WordIndex {
    private final List<String> keys = new ArrayList<>();
    private final List<String> values = new ArrayList<>();

    // ---- trie: transitions in an open-addressing table keyed by (node << 16 | char) ----
    private long[] slots = new long[1024];
    private int[] targets = new int[1024];
    private int used;
    private int[] terminal = new int[256]; // node -> id of the word ending there, or -1
    private int nodes = 1;

    // ---- masks: (length, position, char) -> ids of the words of that length, numbered per length ----
    private final Map<Integer, List<Integer>> byLength = new HashMap<>();
    private final Map<Long, BitSet> masks = new HashMap<>();

    public WordIndex() {
        Arrays.fill(slots, -1L);
        Arrays.fill(terminal, -1);
    }

    /** Adds a word. Words added earlier win when several match. */
    public void add(String key, String value) {
        int id = keys.size();
        keys.add(key);
        values.add(value);
        int node = 0;
        for (int i = 0; i < key.length(); i++) {
            long slotKey = ((long) node << 16) | key.charAt(i);
            int next = find(slotKey);
            if (next < 0) {
                next = nodes++;
                put(slotKey, next);
                if (next >= terminal.length) {
                    int old = terminal.length;
                    terminal = Arrays.copyOf(terminal, old * 2);
                    Arrays.fill(terminal, old, terminal.length, -1);
                }
            }
            node = next;
        }
        if (terminal[node] < 0) {
            terminal[node] = id;
        }
        List<Integer> bucket = byLength.computeIfAbsent(key.length(), k -> new ArrayList<>());
        int local = bucket.size();
        bucket.add(id);
        for (int i = 0; i < key.length(); i++) {
            masks.computeIfAbsent(maskKey(key.length(), i, key.charAt(i)), k -> new BitSet()).set(local);
        }
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    private static long maskKey(int length, int position, char c) {
        return ((long) length << 32) | ((long) position << 16) | c;
    }

    private static int hash(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    private int find(long key) {
        int mask = slots.length - 1;
        for (int i = hash(key) & mask; ; i = (i + 1) & mask) {
            long slot = slots[i];
            if (slot == key) {
                return targets[i];
            }
            if (slot == -1L) {
                return -1;
            }
        }
    }

    private void put(long key, int target) {
        if ((used + 1) * 2 > slots.length) {
            long[] oldSlots = slots;
            int[] oldTargets = targets;
            slots = new long[oldSlots.length * 2];
            targets = new int[slots.length];
            Arrays.fill(slots, -1L);
            used = 0;
            for (int i = 0; i < oldSlots.length; i++) {
                if (oldSlots[i] != -1L) {
                    put(oldSlots[i], oldTargets[i]);
                }
            }
        }
        int mask = slots.length - 1;
        int i = hash(key) & mask;
        while (slots[i] != -1L) {
            i = (i + 1) & mask;
        }
        slots[i] = key;
        targets[i] = target;
        used++;
    }

    /**
     * The first-added word of at least {@code minLength} characters that occurs somewhere inside {@code text}.
     *
     * @return its value, or {@code null}
     */
    public String firstContained(String text, int minLength) {
        return firstContained(text, minLength, null);
    }

    /** An occurrence filter for {@link #firstContained(String, int, Occurrence)}. */
    @FunctionalInterface
    public interface Occurrence {
        /** True when the word found at {@code [start, end)} of the text counts. */
        boolean accept(int start, int end);
    }

    /** Like {@link #firstContained(String, int)}, but occurrences the filter rejects are ignored ({@code null} = accept all). */
    public String firstContained(String text, int minLength, Occurrence filter) {
        int best = -1;
        int n = text.length();
        for (int start = 0; start < n; start++) {
            int node = 0;
            for (int i = start; i < n; i++) {
                node = find(((long) node << 16) | text.charAt(i));
                if (node < 0) {
                    break;
                }
                int id = terminal[node];
                if (id >= 0 && i - start + 1 >= minLength && (best < 0 || id < best)
                    && (filter == null || filter.accept(start, i + 1))) {
                    best = id;
                }
            }
        }
        return best < 0 ? null : values.get(best);
    }

    /**
     * The first-added word that has the same length as {@code token} and the same character at every position that
     * is not {@code wildcard}. A token made only of wildcards, or shorter than 3, matches nothing.
     */
    public String firstMasked(String token, char wildcard) {
        int length = token.length();
        List<Integer> bucket = byLength.get(length);
        if (length < 3 || bucket == null) {
            return null;
        }
        BitSet candidates = null;
        for (int i = 0; i < length; i++) {
            char c = token.charAt(i);
            if (c == wildcard) {
                continue;
            }
            BitSet with = masks.get(maskKey(length, i, c));
            if (with == null) {
                return null;
            }
            if (candidates == null) {
                candidates = (BitSet) with.clone();
            } else {
                candidates.and(with);
                if (candidates.isEmpty()) {
                    return null;
                }
            }
        }
        if (candidates == null) {
            return null;
        }
        int local = candidates.nextSetBit(0);
        return local < 0 ? null : values.get(bucket.get(local));
    }
}
