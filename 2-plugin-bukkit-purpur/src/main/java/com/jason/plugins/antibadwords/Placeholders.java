package com.jason.plugins.antibadwords;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny, case-insensitive {@code {placeholder}} replacer.
 *
 * <p>Both {@code {player}} and {@code {PLAYER}} work, and unknown placeholders are left untouched.
 */
public final class Placeholders {
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z_]+)}");

    private Placeholders() {
    }

    /**
     * @param template text containing placeholders
     * @param values   placeholder name (lower-case, without braces) to replacement
     */
    public static String apply(String template, Map<String, String> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.get(matcher.group(1).toLowerCase(Locale.ROOT));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
