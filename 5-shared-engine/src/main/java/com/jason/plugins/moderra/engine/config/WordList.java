package com.jason.plugins.moderra.engine.config;

import com.jason.plugins.moderra.WordMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One language file from the {@code wordlists} folder (format: {@code 7-wordlists/README.md}).
 *
 * @param code         the file name without {@code .yml}, lower-case; this is what admins enable
 * @param name         human name ("German")
 * @param script       writing system named in the file
 * @param reviewStatus {@code unreviewed}, {@code community-reviewed} or {@code native-reviewed}
 * @param tiers        three lists: index 0 = level-1 (mild), 1 = level-2 (rude), 2 = level-3 (severe)
 */
public record WordList(String code, String name, String script, String reviewStatus, List<List<String>> tiers) {

    /**
     * Reads a parsed file. The {@code language:} value is read as text and only compared with the file name (the file
     * name wins), because YAML turns an unquoted {@code no} into {@code false}.
     *
     * @param problems receives remarks about this file (never fatal)
     */
    public static WordList from(String code, YamlTree yaml, List<String> problems) {
        Object language = yaml.get(List.of("language"));
        if (language != null && !String.valueOf(language).equalsIgnoreCase(code)
            && !(code.equals("no") && Boolean.FALSE.equals(language))) {
            problems.add("language is '" + language + "' but the file is named " + code + ".yml; the file name is used.");
        }
        List<List<String>> tiers = new ArrayList<>();
        for (int tier = 1; tier <= 3; tier++) {
            Object raw = yaml.get(List.of("level-" + tier));
            List<String> words = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    String cleaned = o == null ? "" : WordMatcher.cleanEntry(String.valueOf(o));
                    if (WordMatcher.isValidEntry(cleaned)) {
                        words.add(cleaned);
                    }
                }
            } else if (raw != null) {
                problems.add("level-" + tier + " is not a list; it is ignored.");
            }
            tiers.add(List.copyOf(words));
        }
        return new WordList(code, yaml.getString("name", code), yaml.getString("script", ""), yaml.getString("review-status", "unreviewed"),
            List.copyOf(tiers));
    }

    /**
     * The words that count at a protection level: 1 (LOW) = only level-3, 2 (MEDIUM) = level-2 and level-3,
     * 3 (HIGH) = all three tiers. Mildest kept tier first, like the old config reader.
     */
    public List<String> words(int protectionLevel) {
        List<String> out = new ArrayList<>();
        for (int tier = 4 - Math.max(1, Math.min(3, protectionLevel)); tier <= 3; tier++) {
            out.addAll(tiers.get(tier - 1));
        }
        return out;
    }

    public int size() {
        return tiers.get(0).size() + tiers.get(1).size() + tiers.get(2).size();
    }

    /** The YAML text of a word-list file (used by the migration; every word is quoted). */
    public static String render(String code, String name, String script, String source, String notes, Map<Integer, List<String>> tiers) {
        StringBuilder out = new StringBuilder();
        out.append("# Moderra word list. Format: see 7-wordlists/README.md. Offensive words are moderation data.\n");
        out.append("language: ").append(YamlEditor.quote(code)).append('\n');
        out.append("name: ").append(YamlEditor.quote(name)).append('\n');
        out.append("script: ").append(YamlEditor.scalar(script)).append('\n');
        out.append("review-status: unreviewed\n");
        out.append("sources:\n  - ").append(YamlEditor.quote(source)).append('\n');
        out.append("notes: ").append(YamlEditor.quote(notes)).append('\n');
        for (int tier = 1; tier <= 3; tier++) {
            List<String> words = tiers.getOrDefault(tier, List.of());
            if (words.isEmpty()) {
                out.append("level-").append(tier).append(": []\n");
                continue;
            }
            out.append("level-").append(tier).append(":\n");
            for (String word : words) {
                out.append("  - ").append(YamlEditor.quote(word)).append('\n');
            }
        }
        return out.toString();
    }
}
