package com.jason.plugins.moderra.engine.filters;

import com.jason.plugins.moderra.engine.Hit;
import com.jason.plugins.moderra.engine.Message;
import java.util.List;
import java.util.Locale;

/** Small helpers the spam, link and evasion checks share: the action names used in the config files and source lists. */
public final class FilterAction {
    public static final String BLOCK = "block";
    public static final String CENSOR = "censor";
    public static final String LOG_ONLY = "log-only";
    public static final String OFF = "off";

    private FilterAction() {
    }

    /**
     * Builds the hit for a configured action name.
     *
     * @param cleaned the rewritten text for {@code censor}; when it is {@code null} or blank the message is blocked instead
     */
    public static Hit hit(String check, String detail, String action, int strikeWeight, String cleaned) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case LOG_ONLY -> Hit.logOnly(check, detail, 1, strikeWeight);
            case CENSOR -> cleaned == null || cleaned.isBlank() ? Hit.block(check, detail, 1, strikeWeight)
                : Hit.censor(check, detail, 1, strikeWeight, cleaned);
            default -> Hit.block(check, detail, 1, strikeWeight);
        };
    }

    /** Whether a message from this source is covered by a {@code sources:} list ({@code sign} also covers the back side). */
    public static boolean covers(List<String> sources, Message message) {
        String source = message.source();
        String base = Message.baseSource(source);
        for (String entry : sources) {
            String wanted = entry.trim().toLowerCase(Locale.ROOT);
            if (wanted.equals("*") || wanted.equals(source) || wanted.equals(base) || (wanted + "s").equals(base) || wanted.equals(base + "s")) {
                return true;
            }
        }
        return false;
    }
}
