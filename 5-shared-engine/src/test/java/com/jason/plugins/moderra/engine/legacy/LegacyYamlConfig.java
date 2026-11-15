package com.jason.plugins.moderra.engine.legacy;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/** Minimal read-only view of a YAML document with Bukkit-style dotted paths ({@code filters.scan.chat}). */
public final class LegacyYamlConfig {
    private final Map<String, Object> root;

    @SuppressWarnings("unchecked")
    public LegacyYamlConfig(Reader reader) {
        Object loaded = new Yaml().load(reader);
        this.root = loaded instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public Object get(String path) {
        Object node = root;
        for (String part : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(part);
        }
        return node;
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return s.equalsIgnoreCase("true") || (!s.equalsIgnoreCase("false") && def);
        }
        return def;
    }

    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v instanceof String s ? Integer.parseInt(s.trim()) : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public String getString(String path, String def) {
        Object v = get(path);
        return v == null || v instanceof Map<?, ?> || v instanceof List<?> ? def : String.valueOf(v);
    }

    public List<String> getStringList(String path) {
        List<String> out = new ArrayList<>();
        if (get(path) instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }

    /** Child keys of a section in file order; empty when the path is not a section. */
    public Set<String> keys(String path) {
        Set<String> out = new LinkedHashSet<>();
        if (get(path) instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                out.add(String.valueOf(key));
            }
        }
        return out;
    }
}
