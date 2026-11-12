package com.jason.plugins.antibadwords.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Changes single values in a YAML file <strong>without touching the rest of the text</strong>: comments, blank lines,
 * key order and the admin's own formatting all stay. Used for every programmatic edit (custom words, whitelist,
 * {@code /abw lang}, migration, GUI saves).
 *
 * <p>It works on the text line by line (block-style mappings with space indentation, which is what the generated files
 * use). After every change the result is parsed with a real YAML parser and compared with the intended value; if that
 * check fails the change is undone and an {@link IllegalStateException} is thrown, so a file is never left damaged.
 *
 * <p>Limits: keys inside flow mappings ({@code a: {b: 1}}) and inside lists of mappings cannot be addressed directly
 * (the enclosing key is rewritten as a block instead); comments <em>inside</em> a value that is replaced as a whole are lost.
 */
public final class YamlEditor {
    private static final Pattern PLAIN_VALUE = Pattern.compile("[A-Za-z_][A-Za-z0-9_\\-./]*(?: [A-Za-z0-9_\\-./]+)*");
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_\\-.]*");
    private static final Set<String> RESERVED = Set.of("true", "false", "yes", "no", "on", "off", "y", "n", "null");

    private List<String> lines;
    private final String eol;
    private final boolean trailingNewline;

    /** One {@code key: value} of a block mapping. {@code end} is the line after its last content line. */
    private record Entry(String key, int line, int indent, int colon, int end, String rest, String comment) {
    }

    private record Location(int depth, Entry entry, int from, int to, int parentIndent) {
    }

    private YamlEditor(String text) {
        this.eol = text.contains("\r\n") ? "\r\n" : "\n";
        this.trailingNewline = text.isEmpty() || text.endsWith("\n");
        this.lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
    }

    public static YamlEditor of(String text) {
        return new YamlEditor(text == null ? "" : text);
    }

    public static YamlEditor load(Path file) throws IOException {
        return of(Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "");
    }

    /** The edited document. */
    public String text() {
        return lines.isEmpty() ? "" : String.join(eol, lines) + (trailingNewline ? eol : "");
    }

    /** Writes the document through a temporary file, so a crash never leaves half a file. */
    public void save(Path file) throws IOException {
        writeAtomically(file, text());
    }

    public static void writeAtomically(Path file, String text) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Public editing API
    // ------------------------------------------------------------------------------------------

    public boolean contains(String dottedKey) {
        return contains(split(dottedKey));
    }

    public boolean contains(List<String> path) {
        return locate(path).depth() == path.size();
    }

    /** Sets {@code a.b.c} (segments must not contain dots; use {@link #set(List, Object)} otherwise). */
    public YamlEditor set(String dottedKey, Object value) {
        return set(split(dottedKey), value);
    }

    /**
     * Sets the value at the path, creating missing parent keys. Supported values: Boolean, Number, String,
     * {@link Duration}, lists and maps of those.
     */
    public YamlEditor set(List<String> path, Object value) {
        List<String> backup = new ArrayList<>(lines);
        try {
            Location loc = locate(path);
            if (loc.depth() == path.size()) {
                replace(loc.entry(), value, itemIndent(loc.entry()));
            } else {
                insertMissing(path, loc, value);
            }
            verify(path, value);
        } catch (RuntimeException ex) {
            lines = backup;
            throw ex instanceof IllegalStateException ? ex
                : new IllegalStateException("Could not edit YAML key " + String.join(".", path) + ": " + ex, ex);
        }
        return this;
    }

    /** Removes a key together with the comment lines directly above it. @return false when the key does not exist */
    public boolean remove(String dottedKey) {
        return remove(split(dottedKey));
    }

    public boolean remove(List<String> path) {
        Location loc = locate(path);
        if (loc.depth() != path.size()) {
            return false;
        }
        List<String> backup = new ArrayList<>(lines);
        Entry e = loc.entry();
        int start = e.line();
        while (start > 0 && isComment(lines.get(start - 1)) && indentOf(lines.get(start - 1)) == e.indent()) {
            start--;
        }
        lines.subList(start, e.end()).clear();
        try {
            if (new YamlTree0(text()).get(path) != null) {
                throw new IllegalStateException("Could not remove YAML key " + String.join(".", path));
            }
        } catch (RuntimeException ex) {
            lines = backup;
            throw ex instanceof IllegalStateException ? ex : new IllegalStateException("Could not remove YAML key: " + ex, ex);
        }
        return true;
    }

    /** Appends one item to a list, leaving the other items (and comments between them) exactly as they are. */
    public YamlEditor listAdd(String dottedKey, String item) {
        return listAdd(split(dottedKey), item);
    }

    public YamlEditor listAdd(List<String> path, String item) {
        List<String> current = readList(path);
        List<String> wanted = new ArrayList<>(current);
        wanted.add(item);
        List<String> backup = new ArrayList<>(lines);
        Location loc = locate(path);
        int[] items = loc.depth() == path.size() ? blockItems(loc.entry()) : null;
        if (items != null && items.length > 0) {
            int last = items[items.length - 1];
            lines.add(last + 1, " ".repeat(indentOf(lines.get(last))) + "- " + scalar(item));
            if (verified(path, wanted)) {
                return this;
            }
            lines = backup;
        }
        return set(path, wanted);
    }

    /** Removes every item equal to {@code item} (compared without case). @return false when the list has no such item */
    public boolean listRemove(String dottedKey, String item) {
        return listRemove(split(dottedKey), item);
    }

    public boolean listRemove(List<String> path, String item) {
        List<String> current = readList(path);
        List<String> wanted = new ArrayList<>(current);
        if (!wanted.removeIf(s -> s.equalsIgnoreCase(item))) {
            return false;
        }
        List<String> backup = new ArrayList<>(lines);
        Location loc = locate(path);
        int[] items = loc.depth() == path.size() ? blockItems(loc.entry()) : null;
        if (items != null && !wanted.isEmpty()) {
            for (int i = items.length - 1; i >= 0; i--) {
                String body = lines.get(items[i]).trim().substring(1).trim();
                if (unquote(splitComment(body)[0]).equalsIgnoreCase(item)) {
                    lines.remove(items[i]);
                }
            }
            if (verified(path, wanted)) {
                return true;
            }
            lines = backup;
        }
        set(path, wanted);
        return true;
    }

    /** The list at the path as texts (empty when missing or not a list). */
    public List<String> readList(List<String> path) {
        List<String> out = new ArrayList<>();
        if (new YamlTree0(text()).get(path) instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Finding keys
    // ------------------------------------------------------------------------------------------

    private static List<String> split(String dottedKey) {
        return List.of(dottedKey.split("\\."));
    }

    /** Walks down the path as far as it exists. */
    private Location locate(List<String> path) {
        int from = 0;
        int to = lines.size();
        int parentIndent = -1;
        Entry found = null;
        for (int depth = 0; depth < path.size(); depth++) {
            Entry next = null;
            for (Entry e : children(from, to, parentIndent)) {
                if (e.key().equals(path.get(depth))) {
                    next = e;
                    break;
                }
            }
            if (next == null) {
                return new Location(depth, found, from, to, parentIndent);
            }
            found = next;
            if (depth + 1 < path.size() && !next.rest().isEmpty()) {
                // the parent has an inline value ("a: {}", "a: 1"): we cannot look inside line by line
                return new Location(depth + 1, found, next.line() + 1, next.end(), next.indent());
            }
            from = next.line() + 1;
            to = next.end();
            parentIndent = next.indent();
        }
        return new Location(path.size(), found, from, to, parentIndent);
    }

    private List<Entry> children(int from, int to, int parentIndent) {
        List<Entry> out = new ArrayList<>();
        int childIndent = -1;
        int i = from;
        while (i < to) {
            String line = lines.get(i);
            if (isBlank(line) || isComment(line)) {
                i++;
                continue;
            }
            int indent = indentOf(line);
            if (indent <= parentIndent) {
                break;
            }
            if (childIndent < 0) {
                childIndent = indent;
            }
            Entry e = indent == childIndent ? parseKeyLine(i, indent, to) : null;
            if (e == null) {
                i++;
                continue;
            }
            out.add(e);
            i = e.end();
        }
        return out;
    }

    private Entry parseKeyLine(int index, int indent, int limit) {
        String line = lines.get(index);
        String s = line.substring(indent);
        if (s.startsWith("- ") || s.equals("-") || s.startsWith("[") || s.startsWith("{") || s.startsWith("---") || s.startsWith("...")) {
            return null;
        }
        String key;
        int colon;
        if (s.startsWith("\"") || s.startsWith("'")) {
            int close = closingQuote(s, 0);
            if (close < 0) {
                return null;
            }
            key = unquote(s.substring(0, close + 1));
            colon = close + 1;
            while (colon < s.length() && s.charAt(colon) == ' ') {
                colon++;
            }
        } else {
            colon = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ':' && (i + 1 == s.length() || Character.isWhitespace(s.charAt(i + 1)))) {
                    colon = i;
                    break;
                }
                if (c == '#' && i > 0 && Character.isWhitespace(s.charAt(i - 1))) {
                    break;
                }
            }
            if (colon <= 0) {
                return null;
            }
            key = s.substring(0, colon).trim();
        }
        if (colon >= s.length() || s.charAt(colon) != ':' || (colon + 1 < s.length() && !Character.isWhitespace(s.charAt(colon + 1)))) {
            return null;
        }
        String[] parts = splitComment(s.substring(colon + 1));
        String rest = parts[0];
        int end = valueEnd(index, indent, indent + colon + 1, rest, limit);
        return new Entry(key, index, indent, indent + colon, end, rest, parts[1]);
    }

    /** Line after the last line that belongs to the value starting on {@code index}. */
    private int valueEnd(int index, int indent, int valueColumn, String rest, int limit) {
        int last = index;
        if (rest.startsWith("[") || rest.startsWith("{") || rest.startsWith("\"") || rest.startsWith("'")) {
            last = Math.min(limit - 1, balancedEnd(index, valueColumn, limit));
        }
        for (int j = last + 1; j < limit; j++) {
            String l = lines.get(j);
            if (isBlank(l) || isComment(l)) {
                continue;
            }
            int ind = indentOf(l);
            boolean belongs = ind > indent || (rest.isEmpty() && ind == indent && (l.startsWith("- ", ind) || l.substring(ind).equals("-")));
            if (!belongs) {
                break;
            }
            last = j;
        }
        return last + 1;
    }

    /** Last line of a flow collection or quoted text that starts on line {@code index} at or after {@code column}. */
    private int balancedEnd(int index, int column, int limit) {
        int depth = 0;
        char quote = 0;
        boolean started = false;
        for (int li = index; li < limit; li++) {
            String l = lines.get(li);
            for (int i = li == index ? column : 0; i < l.length(); i++) {
                char c = l.charAt(i);
                if (quote == '"') {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        quote = 0;
                    }
                } else if (quote == '\'') {
                    if (c == '\'') {
                        if (i + 1 < l.length() && l.charAt(i + 1) == '\'') {
                            i++;
                        } else {
                            quote = 0;
                        }
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                    started = true;
                } else if (c == '[' || c == '{') {
                    depth++;
                    started = true;
                } else if (c == ']' || c == '}') {
                    depth--;
                } else if (c == '#' && (i == 0 || Character.isWhitespace(l.charAt(i - 1)))) {
                    break;
                } else if (!Character.isWhitespace(c)) {
                    started = true;
                }
                if (started && depth <= 0 && quote == 0) {
                    return li;
                }
            }
            if (started && depth <= 0 && quote == 0) {
                return li;
            }
        }
        return limit - 1;
    }

    /** Lines of the items when the entry's value is a plain block list of one-line texts; otherwise {@code null}. */
    private int[] blockItems(Entry e) {
        if (!e.rest().isEmpty()) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        int itemIndent = -1;
        for (int i = e.line() + 1; i < e.end(); i++) {
            String l = lines.get(i);
            if (isBlank(l) || isComment(l)) {
                continue;
            }
            int ind = indentOf(l);
            if (itemIndent < 0) {
                itemIndent = ind;
            }
            String body = l.substring(ind);
            if (ind != itemIndent || !body.startsWith("- ")) {
                return null;
            }
            String value = splitComment(body.substring(2))[0];
            if (value.isEmpty() || "[{|>&*!".indexOf(value.charAt(0)) >= 0 || looksLikeMapping(value)) {
                return null;
            }
            out.add(i);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean looksLikeMapping(String value) {
        if (value.startsWith("\"") || value.startsWith("'")) {
            int close = closingQuote(value, 0);
            return close >= 0 && close + 1 < value.length();
        }
        return value.contains(": ") || value.endsWith(":");
    }

    private int itemIndent(Entry e) {
        int[] items = blockItems(e);
        return items == null || items.length == 0 ? -1 : indentOf(lines.get(items[0]));
    }

    // ------------------------------------------------------------------------------------------
    // Changing lines
    // ------------------------------------------------------------------------------------------

    private void replace(Entry e, Object value, int itemIndent) {
        String prefix = lines.get(e.line()).substring(0, e.colon() + 1);
        List<String> rendered = new ArrayList<>();
        render(rendered, prefix, e.indent(), value, e.comment(), itemIndent);
        lines.subList(e.line(), e.end()).clear();
        lines.addAll(e.line(), rendered);
    }

    private void insertMissing(List<String> path, Location loc, Object value) {
        int depth = loc.depth();
        Entry parent = loc.entry();
        if (parent != null && !parent.rest().isEmpty()) {
            // Parent is written inline ("profiles: {}"): rewrite the parent as a block that contains the new key.
            List<String> parentPath = path.subList(0, depth);
            Map<String, Object> merged = new LinkedHashMap<>();
            if (new YamlTree0(text()).get(parentPath) instanceof Map<?, ?> existing) {
                merged.putAll(YamlTree.stringKeys(existing));
            }
            deepPut(merged, path.subList(depth, path.size()), value);
            replace(parent, merged, -1);
            return;
        }
        List<Entry> siblings = children(loc.from(), loc.to(), loc.parentIndent());
        int indent = !siblings.isEmpty() ? siblings.get(0).indent() : loc.parentIndent() + (parent == null ? 1 : 2);
        int at = !siblings.isEmpty() ? siblings.get(siblings.size() - 1).end() : parent != null ? parent.line() + 1 : lines.size();
        Object nested = value;
        for (int i = path.size() - 1; i > depth; i--) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put(path.get(i), nested);
            nested = wrap;
        }
        List<String> rendered = new ArrayList<>();
        render(rendered, " ".repeat(indent) + keyText(path.get(depth)) + ":", indent, nested, "", -1);
        lines.addAll(at, rendered);
    }

    @SuppressWarnings("unchecked")
    private static void deepPut(Map<String, Object> map, List<String> path, Object value) {
        for (int i = 0; i < path.size() - 1; i++) {
            Object child = map.get(path.get(i));
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                map.put(path.get(i), child);
            }
            map = (Map<String, Object>) child;
        }
        map.put(path.get(path.size() - 1), value);
    }

    /** Renders {@code prefix value} as block YAML. Also used to generate the default files. */
    static void render(List<String> out, String prefix, int indent, Object value, String comment, int itemIndent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.add(prefix + " {}" + comment);
                return;
            }
            out.add(prefix + comment);
            for (Map.Entry<?, ?> e : map.entrySet()) {
                render(out, " ".repeat(indent + 2) + keyText(String.valueOf(e.getKey())) + ":", indent + 2, e.getValue(), "", -1);
            }
        } else if (value instanceof Collection<?> list) {
            if (list.isEmpty()) {
                out.add(prefix + " []" + comment);
                return;
            }
            out.add(prefix + comment);
            String pad = " ".repeat(itemIndent >= 0 ? itemIndent : indent + 2);
            for (Object item : list) {
                out.add(pad + "- " + (item instanceof Map<?, ?> || item instanceof Collection<?> ? flow(item) : scalar(item)));
            }
        } else if (value == null) {
            out.add(prefix + comment);
        } else {
            out.add(prefix + " " + scalar(value) + comment);
        }
    }

    private static String flow(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((k, v) -> parts.add(quote(String.valueOf(k)) + ": " + flow(v)));
            return "{" + String.join(", ", parts) + "}";
        }
        if (value instanceof Collection<?> list) {
            return "[" + String.join(", ", list.stream().map(YamlEditor::flow).toList()) + "]";
        }
        return value == null ? "null" : value instanceof String s ? quote(s) : scalar(value);
    }

    /** One value as YAML text: plain when that is certainly safe, otherwise in double quotes. */
    static String scalar(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Duration d) {
            return quote(DurationText.format(d));
        }
        String s = String.valueOf(value);
        return PLAIN_VALUE.matcher(s).matches() && !RESERVED.contains(s.toLowerCase(Locale.ROOT)) ? s : quote(s);
    }

    static String keyText(String key) {
        return PLAIN_KEY.matcher(key).matches() && !RESERVED.contains(key.toLowerCase(Locale.ROOT)) ? key : quote(key);
    }

    static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 || c == 0xfeff) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ------------------------------------------------------------------------------------------
    // Checking the result
    // ------------------------------------------------------------------------------------------

    private void verify(List<String> path, Object expected) {
        if (!verified(path, expected)) {
            throw new IllegalStateException("Could not edit YAML key " + String.join(".", path)
                + " safely (the file layout is not understood); nothing was changed.");
        }
    }

    private boolean verified(List<String> path, Object expected) {
        try {
            return Objects.equals(normalise(new YamlTree0(text()).get(path)), normalise(expected));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Object normalise(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), normalise(v)));
            return out;
        }
        if (value instanceof Collection<?> list) {
            return list.stream().map(YamlEditor::normalise).toList();
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Duration d) {
            return DurationText.format(d);
        }
        return value;
    }

    /** Parsed document that keeps the raw key objects reachable by their text form. */
    private static final class YamlTree0 {
        private final Object root;

        YamlTree0(String text) {
            this.root = new org.yaml.snakeyaml.Yaml().load(text);
        }

        Object get(List<String> path) {
            Object node = root;
            for (String part : path) {
                if (!(node instanceof Map<?, ?> map)) {
                    return null;
                }
                Object next = null;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (String.valueOf(e.getKey()).equals(part)) {
                        next = e.getValue();
                        break;
                    }
                }
                node = next;
            }
            return node;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Small text helpers
    // ------------------------------------------------------------------------------------------

    private static boolean isBlank(String line) {
        return line.isBlank();
    }

    private static boolean isComment(String line) {
        return line.stripLeading().startsWith("#");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** Index of the quote that closes the quoted text starting at {@code start}, or -1. */
    private static int closingQuote(String s, int start) {
        char q = s.charAt(start);
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q == '"' && c == '\\') {
                i++;
            } else if (c == q) {
                if (q == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Splits "value   # note" into the trimmed value and the comment including the spaces in front of it. */
    private static String[] splitComment(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote == '"') {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    quote = 0;
                }
            } else if (quote == '\'') {
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if ((c == '"' || c == '\'') && text.substring(0, i).isBlank()) {
                quote = c;
            } else if (c == '#' && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                int start = i;
                while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
                    start--;
                }
                return new String[] {text.substring(0, start).trim(), text.substring(start)};
            }
        }
        return new String[] {text.trim(), ""};
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            StringBuilder out = new StringBuilder();
            for (int i = 1; i < s.length() - 1; i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length() - 1) {
                    char n = s.charAt(++i);
                    out.append(switch (n) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        default -> n;
                    });
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
        return s;
    }
}
