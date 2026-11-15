package com.jason.plugins.antibadwords.engine.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * One change of shared state, one JSON line. Kinds: {@code mute} (name, until, reason, by), {@code unmute},
 * {@code strike} (weight), {@code reset}; {@code hello} and {@code ack} for the bridge check.
 *
 * @param origin the server that made the change
 * @param ts     wall clock milliseconds of the change (decides conflicts)
 * @param until  mute end (negative = permanent), mutes only
 */
public record SyncRecord(String origin, long ts, String kind, UUID player, String name, long until, String reason, String by, int weight) {
    public static final String MUTE = "mute";
    public static final String UNMUTE = "unmute";
    public static final String STRIKE = "strike";
    public static final String RESET = "reset";
    /** Start-up check of the plugin messaging bridge: "is anybody there?" and the answer. Carry no state. */
    public static final String HELLO = "hello";
    public static final String ACK = "ack";
    private static final UUID NOBODY = new UUID(0L, 0L);

    public static SyncRecord hello(String origin, long ts) {
        return new SyncRecord(origin, ts, HELLO, NOBODY, "", 0, "", "", 0);
    }

    public static SyncRecord ack(String origin, long ts) {
        return new SyncRecord(origin, ts, ACK, NOBODY, "", 0, "", "", 0);
    }

    public static SyncRecord mute(String origin, long ts, UUID player, String name, long until, String reason, String by) {
        return new SyncRecord(origin, ts, MUTE, player, name, until, reason, by, 0);
    }

    public static SyncRecord unmute(String origin, long ts, UUID player) {
        return new SyncRecord(origin, ts, UNMUTE, player, "", 0, "", "", 0);
    }

    public static SyncRecord strike(String origin, long ts, UUID player, int weight) {
        return new SyncRecord(origin, ts, STRIKE, player, "", 0, "", "", weight);
    }

    public static SyncRecord reset(String origin, long ts, UUID player) {
        return new SyncRecord(origin, ts, RESET, player, "", 0, "", "", 0);
    }

    /** Compact single-line JSON (no line breaks, so one record is one line of a journal). */
    public String toLine() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("o", origin);
        m.put("t", ts);
        m.put("k", kind);
        m.put("p", player.toString());
        if (MUTE.equals(kind)) {
            m.put("n", name);
            m.put("u", until);
            m.put("r", reason);
            m.put("b", by);
        } else if (STRIKE.equals(kind)) {
            m.put("w", weight);
        }
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(e.getKey()).append("\":");
            if (e.getValue() instanceof Number) {
                out.append(e.getValue());
            } else {
                quote(out, String.valueOf(e.getValue()));
            }
        }
        return out.append('}').toString();
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }

    /** @return the record, or null when the line cannot be understood */
    public static SyncRecord parse(String line) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(0);
            Object root = new Yaml(new SafeConstructor(options)).load(line);
            if (!(root instanceof Map<?, ?> m)) {
                return null;
            }
            String kind = String.valueOf(m.get("k"));
            if (!MUTE.equals(kind) && !UNMUTE.equals(kind) && !STRIKE.equals(kind) && !RESET.equals(kind)
                && !HELLO.equals(kind) && !ACK.equals(kind)) {
                return null;
            }
            return new SyncRecord(String.valueOf(m.get("o")), ((Number) m.get("t")).longValue(), kind,
                UUID.fromString(String.valueOf(m.get("p"))), str(m.get("n")), m.get("u") instanceof Number u ? u.longValue() : 0,
                str(m.get("r")), str(m.get("b")), m.get("w") instanceof Number w ? w.intValue() : 0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
