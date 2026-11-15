package com.jason.plugins.moderra.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** All word-list files of the config folder, by language code. Immutable; a new one is built on every reload. */
public final class WordLists {
    public static final String FOLDER = "wordlists";
    public static final String CUSTOM = "custom";

    /** Names used by the old single-file config, mapped to the new file codes. */
    private static final Map<String, String> LEGACY_NAMES = Map.of(
        "english", "en", "uk_english", "en-gb", "indonesian", "id", "portuguese_brazil", "pt-br",
        "russian", "ru", "german", "de", "chinese", "zh", "custom", "custom");

    private final Map<String, WordList> lists;

    private WordLists(Map<String, WordList> lists) {
        this.lists = lists;
    }

    public static WordLists empty() {
        return new WordLists(Map.of());
    }

    /** {@code english} -> {@code en}; anything else is only lower-cased and trimmed. */
    public static String canonicalCode(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return LEGACY_NAMES.getOrDefault(key, key);
    }

    public static String legacyCode(String oldName) {
        return LEGACY_NAMES.get(oldName.trim().toLowerCase(Locale.ROOT));
    }

    /** Copies every bundled language file that is missing in {@code folder} (first start, or a new language after an update). */
    public static int copyBundled(Path folder) throws IOException {
        int copied = 0;
        Files.createDirectories(folder);
        for (String code : Resources.bundledWordListCodes()) {
            Path target = folder.resolve(code + ".yml");
            if (Files.notExists(target)) {
                String text = Resources.bundledWordList(code);
                if (text != null) {
                    YamlEditor.writeAtomically(target, text);
                    copied++;
                }
            }
        }
        return copied;
    }

    /**
     * Reads every {@code <code>.yml} in the folder. A file that cannot be read keeps its previous content (when there is
     * one) and adds a problem; it never stops the others from loading.
     */
    public static WordLists load(Path folder, WordLists previous, List<ConfigProblem> problems) {
        Map<String, WordList> out = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().forEach(files::add);
            } catch (IOException ex) {
                problems.add(new ConfigProblem(FOLDER, null, "cannot list the folder: " + ex.getMessage()));
            }
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String code = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
            String shown = FOLDER + "/" + fileName;
            try {
                List<String> remarks = new ArrayList<>();
                WordList list = WordList.from(code, YamlTree.parse(Files.readString(file, StandardCharsets.UTF_8)), remarks);
                remarks.forEach(r -> problems.add(new ConfigProblem(shown, null, r)));
                out.put(code, list);
            } catch (IOException | RuntimeException ex) {
                WordList old = previous == null ? null : previous.get(code);
                problems.add(new ConfigProblem(shown, null, "cannot be read (" + firstLine(ex) + "); "
                    + (old == null ? "this language is skipped." : "keeping the previous words of this language.")));
                if (old != null) {
                    out.put(code, old);
                }
            }
        }
        return new WordLists(new LinkedHashMap<>(out));
    }

    static String firstLine(Throwable ex) {
        String message = String.valueOf(ex.getMessage());
        int nl = message.indexOf('\n');
        return ex.getClass().getSimpleName() + ": " + (nl >= 0 ? message.substring(0, nl) : message);
    }

    /** The list for a code (old names such as {@code english} are understood), or {@code null}. */
    public WordList get(String code) {
        return lists.get(canonicalCode(code));
    }

    /** Every code that has a file, sorted, including {@code custom}. */
    public List<String> codes() {
        return new ArrayList<>(lists.keySet());
    }
}
