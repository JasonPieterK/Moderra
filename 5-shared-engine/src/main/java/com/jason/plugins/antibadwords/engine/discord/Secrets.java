package com.jason.plugins.antibadwords.engine.discord;

import java.util.Collection;
import java.util.regex.Pattern;

/** Hides webhook URLs (and the token inside them) from every text that may be logged or shown. */
public final class Secrets {
    public static final String MASK = "********";
    private static final Pattern WEBHOOK_URL = Pattern.compile("(?i)https?://[^\\s\"'<>]*/webhooks/[^\\s\"'<>]*");
    private static final String MARKER = "/webhooks/";

    private Secrets() {
    }

    /**
     * Replaces every known secret and anything that looks like a webhook URL.
     *
     * @param text   the text about to be logged or shown (may be {@code null})
     * @param secret known secret values (URLs as written in the file and as resolved); empty ones are ignored
     */
    public static String mask(String text, Collection<String> secret) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String out = text;
        for (String value : secret) {
            if (value == null || value.isBlank()) {
                continue;
            }
            out = out.replace(value, MASK);
            int at = value.indexOf(MARKER);
            if (at >= 0) {
                String tail = value.substring(at + MARKER.length());
                if (tail.length() >= 8) {
                    out = out.replace(tail, MASK);
                }
                int slash = tail.indexOf('/');
                if (slash >= 0 && tail.length() - slash - 1 >= 8) {
                    out = out.replace(tail.substring(slash + 1), MASK);
                }
            }
        }
        return WEBHOOK_URL.matcher(out).replaceAll(MASK);
    }
}
