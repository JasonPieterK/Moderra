package com.jason.plugins.antibadwords.engine.filters;

import com.jason.plugins.antibadwords.engine.config.Resources;
import com.jason.plugins.antibadwords.engine.config.YamlTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The look-alike table: characters from other alphabets that look like a plain Latin letter (Cyrillic "а" looks like
 * "a"). The default table is the bundled {@code abw-defaults/look-alikes.yml}, made from Unicode's confusables data;
 * admins extend it in {@code filters/confusables.yml}. Immutable.
 *
 * <p>Text format of one group: items separated by spaces, each {@code <one character>=<plain letters>}, for example
 * {@code "а=a е=e о=o"}.
 */
public final class Confusables {
    /** The 12 Cyrillic letters version 1 of the plugin knew. Used when no table can be read at all. */
    public static final Confusables LEGACY = new Confusables(parsePairs("а=a с=c е=e о=o р=p х=x у=y к=k м=m н=h т=t і=i", null, null));

    private static volatile Confusables bundled;

    private final Map<Integer, String> toLatin;
    /** script -> (Latin letter -> the letter of that script that looks like it). */
    private final Map<Character.UnicodeScript, Map<Character, Integer>> fromLatin = new HashMap<>();

    private Confusables(Map<Integer, String> toLatin) {
        this.toLatin = Map.copyOf(toLatin);
        // Reverse direction ("xуй" written with a Latin x): prefer a lower-case look-alike, keep the first one listed.
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<Integer, String> e : toLatin.entrySet()) {
                int cp = e.getKey();
                boolean lower = Character.toLowerCase(cp) == cp;
                if (e.getValue().length() != 1 || lower != (pass == 0)) {
                    continue;
                }
                int source = Character.toLowerCase(cp);
                fromLatin.computeIfAbsent(Character.UnicodeScript.of(source), s -> new HashMap<>()).putIfAbsent(e.getValue().charAt(0), source);
            }
        }
    }

    /** The table shipped inside the jar (falls back to {@link #LEGACY} when the resource is missing or broken). */
    public static Confusables bundled() {
        Confusables table = bundled;
        if (table == null) {
            try {
                String text = Resources.text("/abw-defaults/look-alikes.yml");
                Map<Integer, String> pairs = text == null ? Map.of() : read(YamlTree.parse(text).root(), w -> { });
                table = pairs.isEmpty() ? LEGACY : new Confusables(pairs);
            } catch (RuntimeException ex) {
                table = LEGACY;
            }
            bundled = table;
        }
        return table;
    }

    /**
     * Reads the {@code look-alikes:} section: group name -> text (or list of texts) of {@code x=y} items.
     * Wrong items are skipped with a warning. A section without any usable item gives the bundled table.
     */
    public static Confusables parse(Map<String, Object> section, Consumer<String> warn) {
        Map<Integer, String> table = read(section, warn);
        return table.isEmpty() ? bundled() : new Confusables(table);
    }

    private static Map<Integer, String> read(Map<String, Object> section, Consumer<String> warn) {
        Map<Integer, String> table = new LinkedHashMap<>();
        for (Map.Entry<String, Object> group : section.entrySet()) {
            List<Object> texts = new ArrayList<>();
            if (group.getValue() instanceof List<?> list) {
                texts.addAll(list);
            } else if (group.getValue() != null) {
                texts.add(group.getValue());
            }
            for (Object text : texts) {
                if (text instanceof Map<?, ?> || text instanceof List<?>) {
                    warn.accept("look-alikes." + group.getKey() + " must be text such as \"а=a е=e\"; this part is ignored.");
                    continue;
                }
                table.putAll(parsePairs(String.valueOf(text), "look-alikes." + group.getKey(), warn));
            }
        }
        return table;
    }

    /** Parses {@code "x=y x=y"}; the left side is exactly one character, the right side 1 to 4 letters or digits. */
    static Map<Integer, String> parsePairs(String text, String where, Consumer<String> warn) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (String item : text.trim().split("\\s+")) {
            if (item.isEmpty()) {
                continue;
            }
            int cp = item.codePointAt(0);
            int width = Character.charCount(cp);
            String target = item.length() > width + 1 && item.charAt(width) == '=' ? item.substring(width + 1).toLowerCase(Locale.ROOT) : "";
            if (target.isEmpty() || target.length() > 4 || !target.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c))) {
                if (warn != null) {
                    warn.accept(where + ": '" + item + "' is not of the form <one character>=<plain letter>; it is ignored.");
                }
                continue;
            }
            out.put(cp, target);
        }
        return out;
    }

    /** The plain letters this character looks like, or {@code null}. Looks at the character as typed, then lower-cased. */
    public String toLatin(int codePoint) {
        String hit = toLatin.get(codePoint);
        return hit != null ? hit : toLatin.get(Character.toLowerCase(codePoint));
    }

    /** The lower-case letter of {@code script} that looks like this plain Latin letter, or -1. */
    public int fromLatin(Character.UnicodeScript script, char latinLetter) {
        Map<Character, Integer> map = fromLatin.get(script);
        Integer hit = map == null ? null : map.get(latinLetter);
        return hit == null ? -1 : hit;
    }

    public int size() {
        return toLatin.size();
    }
}
