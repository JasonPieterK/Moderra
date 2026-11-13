package com.jason.plugins.antibadwords.engine.filters;

import com.jason.plugins.antibadwords.engine.EffectiveSettings;
import com.jason.plugins.antibadwords.engine.Hit;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.TextCheck;
import com.jason.plugins.antibadwords.mod.Names;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Notices bypass tricks as such: an invisible character hidden inside a word, or a word that mixes Latin letters with
 * look-alike letters of another alphabet. (Finding the blocked word behind the trick is the job of the
 * {@link EvasionNormalizer} inside the bad-word check; this check exists so admins can log, clean or block the trick
 * itself.) Stateless and thread-safe.
 */
public final class EvasionCheck implements TextCheck {
    public static final String ID = "evasion";
    static final String INVISIBLE = "invisible characters";
    static final String LOOK_ALIKE = "look-alike letters";

    /** What {@link #scan} found: the reasons and the text with the tricks undone. */
    public record Finding(List<String> reasons, String cleaned) {
    }

    private record Compiled(Set<Character.UnicodeScript> scripts, Confusables table) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean enabled(EffectiveSettings settings) {
        return !FilterAction.OFF.equals(settings.get(EvasionSettings.DETECT_ACTION))
            && (settings.get(EvasionSettings.DETECT_INVISIBLE) || settings.get(EvasionSettings.DETECT_MIXED));
    }

    @Override
    public Optional<Hit> check(Message message, EffectiveSettings settings) {
        if (!FilterAction.covers(settings.get(EvasionSettings.DETECT_SOURCES), message) || message.has(EvasionSettings.PERMISSION_BYPASS)) {
            return Optional.empty();
        }
        Compiled compiled = settings.derived("evasion.compiled", s -> new Compiled(
            EvasionSettings.scripts(s.get(EvasionSettings.DETECT_SCRIPTS), EvasionSettings.DETECT_SCRIPTS, w -> { }),
            EvasionSettings.table(s, w -> { })));
        Finding finding = scan(message.text(), settings.get(EvasionSettings.DETECT_INVISIBLE), settings.get(EvasionSettings.DETECT_MIXED),
            compiled.scripts(), compiled.table());
        if (finding == null) {
            return Optional.empty();
        }
        return Optional.of(FilterAction.hit(ID, String.join(", ", finding.reasons()), settings.get(EvasionSettings.DETECT_ACTION),
            settings.get(EvasionSettings.DETECT_WEIGHT), finding.cleaned()));
    }

    @Override
    public String feedback(Hit hit, EffectiveSettings settings) {
        return Names.colour(settings.get(EvasionSettings.DETECT_MESSAGE));
    }

    /** @return {@code null} when the text uses no trick */
    public static Finding scan(String text, boolean invisible, boolean mixed, Set<Character.UnicodeScript> scripts, Confusables table) {
        int[] cps = text.codePoints().toArray();
        boolean[] drop = new boolean[cps.length];
        boolean foundInvisible = false;
        boolean foundMixed = false;

        if (invisible) {
            for (int i = 0; i < cps.length; i++) {
                if (!hidden(cps[i])) {
                    continue;
                }
                int end = i;
                while (end < cps.length && hidden(cps[end])) {
                    end++;
                }
                if (i > 0 && end < cps.length && watchedLetter(cps[i - 1], scripts) && watchedLetter(cps[end], scripts)) {
                    foundInvisible = true;
                    for (int k = i; k < end; k++) {
                        drop[k] = true;
                    }
                }
                i = end - 1;
            }
        }
        if (mixed) {
            for (int start = 0; start < cps.length; ) {
                if (!wordPart(cps[start])) {
                    start++;
                    continue;
                }
                int end = start;
                while (end < cps.length && wordPart(cps[end])) {
                    end++;
                }
                foundMixed |= fixMixedWord(cps, start, end, table);
                start = end;
            }
        }
        if (!foundInvisible && !foundMixed) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        if (foundInvisible) {
            reasons.add(INVISIBLE);
        }
        if (foundMixed) {
            reasons.add(LOOK_ALIKE);
        }
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < cps.length; i++) {
            if (!drop[i]) {
                cleaned.appendCodePoint(cps[i]);
            }
        }
        return new Finding(reasons, cleaned.toString());
    }

    private static boolean hidden(int cp) {
        return EvasionNormalizer.isInvisible(cp) || EvasionNormalizer.isFiller(cp);
    }

    private static boolean wordPart(int cp) {
        return hidden(cp) || Character.isLetter(cp) || EvasionNormalizer.isMark(cp);
    }

    private static boolean watchedLetter(int cp, Set<Character.UnicodeScript> scripts) {
        return Character.isLetter(cp) && !EvasionNormalizer.isFiller(cp) && scripts.contains(Character.UnicodeScript.of(cp));
    }

    /**
     * Looks at one word. When it mixes Latin letters with look-alikes, the look-alikes are replaced in place
     * (keeping capital letters capital) and true is returned.
     */
    private static boolean fixMixedWord(int[] cps, int start, int end, Confusables table) {
        int latin = 0;
        int foreign = 0;
        boolean allForeignMapped = true;
        boolean allLatinReversible = true;
        Character.UnicodeScript other = null;
        for (int i = start; i < end; i++) {
            int cp = cps[i];
            if (!Character.isLetter(cp) || EvasionNormalizer.isFiller(cp)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.LATIN) {
                latin++;
            } else if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) {
                foreign++;
                if (other == null) {
                    other = script;
                }
                allForeignMapped &= table.toLatin(cp) != null;
            }
        }
        if (latin == 0 || foreign == 0) {
            return false;
        }
        if (allForeignMapped) {
            for (int i = start; i < end; i++) {
                int cp = cps[i];
                String to = cp < 128 || !Character.isLetter(cp) || Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN
                    ? null : table.toLatin(cp);
                if (to != null && to.length() == 1) {
                    cps[i] = Character.isUpperCase(cp) ? Character.toUpperCase(to.charAt(0)) : to.charAt(0);
                }
            }
            return true;
        }
        if (latin >= foreign) {
            return false;
        }
        for (int i = start; i < end; i++) {
            int cp = cps[i];
            if (Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) {
                int lower = Character.toLowerCase(cp);
                allLatinReversible &= lower < 128 && table.fromLatin(other, (char) lower) >= 0;
            }
        }
        if (!allLatinReversible) {
            return false;
        }
        for (int i = start; i < end; i++) {
            int cp = cps[i];
            if (cp < 128 && Character.isLetter(cp)) {
                int to = table.fromLatin(other, (char) Character.toLowerCase(cp));
                cps[i] = Character.isUpperCase(cp) ? Character.toUpperCase(to) : to;
            }
        }
        return true;
    }
}
