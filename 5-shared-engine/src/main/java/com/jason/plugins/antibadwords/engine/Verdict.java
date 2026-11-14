package com.jason.plugins.antibadwords.engine;

import java.util.List;

/**
 * The engine's decision about one {@link Message}: let it through, block it, or deliver a cleaned text instead.
 * The reasons are the {@link Hit}s. Apply it like this:
 * <pre>
 *   Verdict v = engine.inspect(message);
 *   if (v.blocked()) cancel the event;  else if (v.censored()) replace the text with v.text();
 *   if (v.hasHits()) engine.onViolation(message, v);   // strikes, messages, punishments, events
 * </pre>
 * A loader that cannot rewrite text at some place treats {@link #censored()} like {@link #blocked()} there.
 */
public final class Verdict {
    public enum Type {
        ALLOW, BLOCK, CENSOR
    }

    private final Type type;
    private final String text;
    private final List<Hit> hits;
    private final EffectiveSettings settings;
    private final boolean captured;

    Verdict(Type type, String text, List<Hit> hits, EffectiveSettings settings) {
        this(type, text, hits, settings, false);
    }

    private Verdict(Type type, String text, List<Hit> hits, EffectiveSettings settings, boolean captured) {
        this.captured = captured;
        this.type = type;
        this.text = text;
        this.hits = List.copyOf(hits);
        this.settings = settings;
    }

    static Verdict allow(EffectiveSettings settings) {
        return new Verdict(Type.ALLOW, null, List.of(), settings);
    }

    /**
     * The message was taken by a text prompt (the player's answer to a menu question). The type is BLOCK with no hits, so
     * a loader that does not know this still cancels the message. A loader that does should: not broadcast it, not scan
     * it, not store it, not publish events, not call {@code onViolation}.
     */
    static Verdict captured(EffectiveSettings settings) {
        return new Verdict(Type.BLOCK, null, List.of(), settings, true);
    }

    /** {@code true} when a text prompt consumed this message; apply nothing else (see {@link #captured(EffectiveSettings)}). */
    public boolean captured() {
        return captured;
    }

    public Type type() {
        return type;
    }

    public boolean allowed() {
        return type == Type.ALLOW;
    }

    public boolean blocked() {
        return type == Type.BLOCK;
    }

    public boolean censored() {
        return type == Type.CENSOR;
    }

    /** The cleaned text for {@link Type#CENSOR}; {@code null} otherwise. */
    public String text() {
        return text;
    }

    /** Every reason, in the order the checks ran (may be non-empty even for ALLOW: log-only hits). */
    public List<Hit> hits() {
        return hits;
    }

    public boolean hasHits() {
        return !hits.isEmpty();
    }

    /**
     * The hit that decided the verdict: the blocking one; else, when the text was changed (or blocked because this
     * place cannot deliver a changed text), the first censoring one; else the first. {@code null} when there are none.
     */
    public Hit primary() {
        for (Hit hit : hits) {
            if (hit.action() == Hit.Action.BLOCK) {
                return hit;
            }
        }
        if (type != Type.ALLOW) {
            for (Hit hit : hits) {
                if (hit.action() == Hit.Action.CENSOR) {
                    return hit;
                }
            }
        }
        return hits.isEmpty() ? null : hits.get(0);
    }

    /** The settings (profile) this message was judged with. */
    public EffectiveSettings settings() {
        return settings;
    }
}
