package com.jason.plugins.moderra.engine.integrations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Reads and rewrites the JSON of a Minecraft chat component (system chat, team prefix, ...) without a JSON library.
 * Only the {@code text} values and the {@code translate} arguments are visible; every other key is kept as it is.
 * A text that is not valid JSON is treated as one plain text.
 */
public final class ChatJson {
    private ChatJson() {
    }

    /** The visible text of a component, all parts joined (no colours). Empty when there is none. */
    public static String plain(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Object tree;
        try {
            tree = new Parser(json).parse();
        } catch (RuntimeException ex) {
            return json;
        }
        StringBuilder out = new StringBuilder();
        collect(tree, out);
        return out.toString();
    }

    /** The same component with every visible text part replaced by {@code fn(part)}; the JSON text itself when nothing changed. */
    public static String mapText(String json, UnaryOperator<String> fn) {
        if (json == null || json.isBlank()) {
            return json;
        }
        Object tree;
        try {
            tree = new Parser(json).parse();
        } catch (RuntimeException ex) {
            String mapped = fn.apply(json);
            return mapped == null ? json : mapped;
        }
        boolean[] changed = {false};
        Object mapped = map(tree, fn, changed);
        if (!changed[0]) {
            return json;
        }
        StringBuilder out = new StringBuilder();
        write(mapped, out);
        return out.toString();
    }

    private static void collect(Object node, StringBuilder out) {
        if (node instanceof String s) {
            out.append(s);
        } else if (node instanceof List<?> list) {
            list.forEach(o -> collect(o, out));
        } else if (node instanceof Map<?, ?> map) {
            if (map.get("text") instanceof String s) {
                out.append(s);
            }
            if (map.get("with") instanceof List<?> l) {
                l.forEach(o -> collect(o, out));
            }
            if (map.get("extra") instanceof List<?> l) {
                l.forEach(o -> collect(o, out));
            }
        }
    }

    private static Object map(Object node, UnaryOperator<String> fn, boolean[] changed) {
        if (node instanceof String s) {
            return apply(s, fn, changed);
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) {
                out.add(map(o, fn, changed));
            }
            return out;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object v = e.getValue();
                if ("text".equals(key) && v instanceof String s) {
                    out.put(key, apply(s, fn, changed));
                } else if ("with".equals(key) || "extra".equals(key)) {
                    out.put(key, map(v, fn, changed));
                } else {
                    out.put(key, v);
                }
            }
            return out;
        }
        return node;
    }

    private static String apply(String s, UnaryOperator<String> fn, boolean[] changed) {
        String r = s.isEmpty() ? s : fn.apply(s);
        if (r == null || r.equals(s)) {
            return s;
        }
        changed[0] = true;
        return r;
    }

    private static void write(Object node, StringBuilder out) {
        if (node instanceof String s) {
            out.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        } else if (node instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(list.get(i), out);
            }
            out.append(']');
        } else if (node instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(String.valueOf(e.getKey()), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
        } else if (node instanceof Raw raw) {
            out.append(raw.text);
        } else {
            out.append("null");
        }
    }

    /** A number, true, false or null, kept as written. */
    private record Raw(String text) {
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object parse() {
            Object v = value();
            ws();
            if (i != s.length()) {
                throw new IllegalArgumentException("trailing data");
            }
            return v;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object value() {
            ws();
            if (i >= s.length()) {
                throw new IllegalArgumentException("end");
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> map = new LinkedHashMap<>();
                ws();
                if (peek('}')) {
                    return map;
                }
                do {
                    ws();
                    String key = string();
                    ws();
                    expect(':');
                    map.put(key, value());
                    ws();
                } while (peek(','));
                expect('}');
                return map;
            }
            if (c == '[') {
                i++;
                List<Object> list = new ArrayList<>();
                ws();
                if (peek(']')) {
                    return list;
                }
                do {
                    list.add(value());
                    ws();
                } while (peek(','));
                expect(']');
                return list;
            }
            if (c == '"') {
                return string();
            }
            int start = i;
            while (i < s.length() && ",]} \t\r\n".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("value");
            }
            return new Raw(s.substring(start, i));
        }

        private boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("expected " + c);
            }
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw new IllegalArgumentException("escape");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw new IllegalArgumentException("unicode");
                        }
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }
    }
}
