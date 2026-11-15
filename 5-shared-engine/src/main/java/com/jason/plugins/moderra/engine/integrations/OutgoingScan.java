package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.Hit;
import com.jason.plugins.moderra.engine.Verdict;
import java.util.List;
import java.util.Locale;

/**
 * Judges chat text that other plugins send to a player (outgoing system or player chat packets). Pure logic: the plugin reads the
 * packet, calls {@link #handle}, and applies the {@link Outcome}. Nothing is counted against the receiving player: the text is
 * judged with {@link Engine#explain(String)}, so there are no strikes and no punishment. Texts that contain one of the
 * {@code ignore-containing} entries (default: the prefix of this plugin) are never judged.
 */
public final class OutgoingScan {
    public enum Kind { PASS, REWRITE, CANCEL }

    /** What to do with the packet. {@code json} is the new component JSON for {@link Kind#REWRITE}, else {@code null}. */
    public record Outcome(Kind kind, String json) {
        static final Outcome PASS = new Outcome(Kind.PASS, null);
    }

    private static final long MINUTE = 60_000L;
    private static final int LOG_TEXT_MAX = 80;

    private final Engine engine;
    private long windowStart = Long.MIN_VALUE;
    private int logged;
    private int suppressed;

    public OutgoingScan(Engine engine) {
        this.engine = engine;
    }

    public boolean enabled() {
        return engine.value(OutgoingScanSettings.ENABLED);
    }

    /** Whether packets of this source ({@code system-chat}, {@code player-chat}) are judged. */
    public boolean covers(String source) {
        for (String s : engine.value(OutgoingScanSettings.SOURCES)) {
            if (s.trim().toLowerCase(Locale.ROOT).equals(source)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param source     {@code system-chat} or {@code player-chat}
     * @param receiver   name of the receiving player (for the log line only)
     * @param json       the chat component as JSON (or plain text)
     * @param canRewrite whether the caller can write a changed component back into the packet
     */
    public synchronized Outcome handle(String source, String receiver, String json, boolean canRewrite) {
        if (!enabled() || !covers(source)) {
            return Outcome.PASS;
        }
        String plain = ChatJson.plain(json);
        if (plain.isBlank() || ignored(plain)) {
            return Outcome.PASS;
        }
        Verdict verdict = engine.explain(plain);
        if (verdict.allowed() && !verdict.hasHits()) {
            return Outcome.PASS;
        }
        if (verdict.allowed()) {
            return Outcome.PASS; // log-only hits of a check: nothing to apply to a foreign message
        }
        String action = engine.value(OutgoingScanSettings.ACTION);
        if (OutgoingScanSettings.ACTION_CANCEL.equals(action) && keepsPacket(source)) {
            action = OutgoingScanSettings.ACTION_MASK; // a team or tab-list packet is never dropped: the client would lose its state
        }
        if (OutgoingScanSettings.ACTION_CANCEL.equals(action)) {
            log("Outgoing scan: a " + source + " packet for " + receiver + " was not sent. It contains a word that is not permitted: " + detail(verdict)
                + ". Text: " + shorten(plain));
            return new Outcome(Kind.CANCEL, null);
        }
        if (OutgoingScanSettings.ACTION_MASK.equals(action) && canRewrite) {
            String rewritten = ChatJson.mapText(json, this::mask);
            if (!rewritten.equals(json)) {
                log("Outgoing scan: a " + source + " packet for " + receiver + " was changed. It contained a word that is not permitted: "
                    + detail(verdict) + ".");
                return new Outcome(Kind.REWRITE, rewritten);
            }
        }
        log("Outgoing scan: a " + source + " packet for " + receiver + " contains a word that is not permitted: " + detail(verdict)
            + ". The packet is sent unchanged. Text: " + shorten(plain));
        return Outcome.PASS;
    }

    private static boolean keepsPacket(String source) {
        return OutgoingScanSettings.SOURCE_TEAM.equals(source) || OutgoingScanSettings.SOURCE_TAB_LIST.equals(source);
    }

    private boolean ignored(String plain) {
        for (String marker : engine.value(OutgoingScanSettings.IGNORE)) {
            if (!marker.isEmpty() && plain.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** One text part with the offending text replaced: the censored text, or * for every letter when the whole text is blocked. */
    private String mask(String part) {
        Verdict v = engine.explain(part);
        if (v.censored() && v.text() != null) {
            return v.text();
        }
        if (v.blocked()) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                b.append(Character.isWhitespace(c) ? c : '*');
            }
            return b.toString();
        }
        return part;
    }

    private static String detail(Verdict verdict) {
        Hit hit = verdict.primary();
        return hit == null ? "unknown" : hit.check() + (hit.detail() == null || hit.detail().isEmpty() ? "" : " (" + hit.detail() + ")");
    }

    private static String shorten(String text) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= LOG_TEXT_MAX ? flat : flat.substring(0, LOG_TEXT_MAX) + "...";
    }

    private void log(String line) {
        long now = engine.now();
        if (windowStart == Long.MIN_VALUE || now - windowStart >= MINUTE) {
            if (suppressed > 0) {
                engine.platform().logger().info("Outgoing scan: " + suppressed + " more hits in the last minute were not written to the log.");
            }
            windowStart = now;
            logged = 0;
            suppressed = 0;
        }
        if (logged < engine.value(OutgoingScanSettings.LOG_LIMIT)) {
            logged++;
            engine.platform().logger().info(line);
        } else {
            suppressed++;
        }
    }

    /** The packet sources this scan knows; the plugin layer uses these names. */
    public static List<String> knownSources() {
        return List.of(OutgoingScanSettings.SOURCE_SYSTEM_CHAT, OutgoingScanSettings.SOURCE_PLAYER_CHAT, OutgoingScanSettings.SOURCE_TEAM,
            OutgoingScanSettings.SOURCE_TAB_LIST);
    }
}
