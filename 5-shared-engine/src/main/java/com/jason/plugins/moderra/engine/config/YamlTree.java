package com.jason.plugins.moderra.engine.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/** Read-only view of a YAML document with dotted paths ({@code filters.scan.chat}). Keys are always read as text. */
public final class YamlTree {
    private final Map<String, Object> root;

    private YamlTree(Map<String, Object> root) {
        this.root = root;
    }

    /**
     * @throws RuntimeException when the text is not valid YAML (from SnakeYAML), or is not made of {@code key: value}
     *                          lines at the top (an empty text is fine and gives an empty tree)
     */
    public static YamlTree parse(String text) {
        Object loaded = new Yaml().load(text);
        if (loaded != null && !(loaded instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("the file must contain 'key: value' lines, but it starts with something else");
        }
        return new YamlTree(loaded instanceof Map<?, ?> m ? stringKeys(m) : new LinkedHashMap<>());
    }

    public static YamlTree of(Map<String, Object> map) {
        return new YamlTree(stringKeys(map));
    }

    /** Copies a map so every key is a String ({@code no: x} would otherwise have the Boolean key false). */
    public static Map<String, Object> stringKeys(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object v = e.getValue();
            out.put(String.valueOf(e.getKey()), v instanceof Map<?, ?> m ? stringKeys(m) : v);
        }
        return out;
    }

    public Map<String, Object> root() {
        return root;
    }

    public Object get(String path) {
        return get(List.of(path.split("\\.")));
    }

    public Object get(List<String> path) {
        Object node = root;
        for (String part : path) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(part);
        }
        return node;
    }

    public boolean getBoolean(String path, boolean def) {
        return get(path) instanceof Boolean b ? b : def;
    }

    public String getString(String path, String def) {
        Object v = get(path);
        return v == null || v instanceof Map<?, ?> || v instanceof List<?> ? def : String.valueOf(v);
    }

    public List<String> getStringList(String path) {
        List<String> out = new ArrayList<>();
        if (get(path) instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !(o instanceof Map<?, ?>) && !(o instanceof List<?>)) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String path) {
        return get(path) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Child keys of a section in file order; empty when the path is not a section. */
    public Set<String> keys(String path) {
        return new LinkedHashSet<>(getMap(path).keySet());
    }
}
