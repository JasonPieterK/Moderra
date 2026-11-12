package com.jason.plugins.antibadwords.engine.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes the default text of a config file (keys, default values and comments) from the {@link SettingsRegistry}. */
public final class DefaultFiles {
    private static final int WIDTH = 108;

    private DefaultFiles() {
    }

    /** The complete default content of one file. */
    public static String render(SettingsRegistry registry, String file) {
        List<String> out = new ArrayList<>();
        String header = registry.fileHeader(file);
        if (!header.isEmpty()) {
            String bar = "# " + "=".repeat(WIDTH - 2);
            out.add(bar);
            for (String line : header.split("\n", -1)) {
                out.add(line.isEmpty() ? "#" : "#  " + line);
            }
            out.add(bar);
            out.add("");
        }
        Map<String, Object> tree = new LinkedHashMap<>();
        for (ConfigEntry entry : registry.inFile(file)) {
            put(tree, List.of(entry.key().split("\\.")), entry);
        }
        emit(out, tree, 0);
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return String.join("\n", out) + "\n";
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> tree, List<String> path, ConfigEntry entry) {
        for (int i = 0; i < path.size() - 1; i++) {
            tree = (Map<String, Object>) tree.computeIfAbsent(path.get(i), k -> new LinkedHashMap<String, Object>());
        }
        tree.put(path.get(path.size() - 1), entry);
    }

    @SuppressWarnings("unchecked")
    private static void emit(List<String> out, Map<String, Object> tree, int indent) {
        String pad = " ".repeat(indent);
        for (Map.Entry<String, Object> e : tree.entrySet()) {
            String keyLine = pad + YamlEditor.keyText(e.getKey()) + ":";
            if (e.getValue() instanceof Map<?, ?> group) {
                out.add(keyLine);
                emit(out, (Map<String, Object>) group, indent + 2);
            } else if (e.getValue() instanceof Setting<?> setting) {
                comment(out, pad, setting.description());
                if (setting.type() == SettingType.INT || setting.type() == SettingType.DOUBLE
                    || setting.type() == SettingType.ENUM || setting.type() == SettingType.DURATION) {
                    comment(out, pad, "(" + setting.constraintText() + ")");
                }
                YamlEditor.render(out, keyLine, indent, yamlDefault(setting), "", -1);
            } else if (e.getValue() instanceof Section section) {
                comment(out, pad, section.description());
                String body = section.defaultYaml().strip();
                if (body.isEmpty() || body.equals("{}")) {
                    out.add(keyLine + " {}");
                } else {
                    out.add(keyLine);
                    for (String line : section.defaultYaml().stripTrailing().split("\n", -1)) {
                        out.add(line.isBlank() ? "" : pad + "  " + line);
                    }
                }
            }
            if (indent == 0) {
                out.add("");
            }
        }
    }

    private static <T> Object yamlDefault(Setting<T> setting) {
        return setting.toYaml(setting.defaultValue());
    }

    /** Adds the text as {@code #} comment lines, wrapping long lines at spaces. Line breaks in the text are kept. */
    private static void comment(List<String> out, String pad, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String paragraph : text.split("\n", -1)) {
            String rest = paragraph.stripTrailing();
            int width = Math.max(40, WIDTH - pad.length() - 2);
            String leading = rest.substring(0, rest.length() - rest.stripLeading().length());
            while (rest.length() > width) {
                int cut = rest.lastIndexOf(' ', width);
                if (cut <= leading.length()) {
                    break;
                }
                out.add(pad + "# " + rest.substring(0, cut));
                rest = leading + rest.substring(cut + 1);
            }
            out.add(rest.isEmpty() ? pad + "#" : pad + "# " + rest);
        }
    }
}
