package com.jason.plugins.moderra.engine.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for the raw (map and list) content of free-form config sections. */
final class Sections {
    private Sections() {
    }

    /** A deep copy; maps keep their order. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> copy(Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, copyValue(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object v) {
        if (v instanceof Map<?, ?> m) {
            return copy((Map<String, Object>) m);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(i -> out.add(copyValue(i)));
            return out;
        }
        return v;
    }

    /** Equal content AND the same order of the top-level keys (the order of world rules decides which one wins). */
    static boolean sameWithOrder(Map<String, Object> a, Map<String, Object> b) {
        return a.equals(b) && new ArrayList<>(a.keySet()).equals(new ArrayList<>(b.keySet()));
    }

    /** The map with String keys (a shallow copy; nested maps are copied too). */
    static Map<String, Object> strings(Map<?, ?> map) {
        return com.jason.plugins.moderra.engine.config.YamlTree.stringKeys(map);
    }

    /** A child map, or an empty (detached) one when missing or not a map. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> child(Map<String, Object> map, String key) {
        return map.get(key) instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    static int intOf(Object raw, int fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String text = raw == null ? "" : String.valueOf(raw).trim();
        return text.matches("\\d{1,9}") ? Integer.parseInt(text) : fallback;
    }

    /** The {@code repeat} value as a number of strikes (0 = once). */
    static int repeatOf(Object raw) {
        if (raw == null) {
            return 0;
        }
        String text = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
        if (text.startsWith("every")) {
            text = text.substring(5).trim();
        }
        return text.matches("\\d{1,9}") ? Integer.parseInt(text) : 0;
    }

    @SuppressWarnings("unchecked")
    static List<Object> actionsOf(Map<String, Object> step) {
        Object raw = step.get("actions");
        if (raw instanceof List<?> l) {
            return new ArrayList<>((List<Object>) l);
        }
        List<Object> out = new ArrayList<>();
        if (raw != null) {
            out.add(raw);
        }
        return out;
    }
}
