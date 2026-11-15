package com.jason.plugins.moderra.engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Files bundled inside the jar (default sections and the word lists). */
public final class Resources {
    private Resources() {
    }

    /** Text of a bundled file, or {@code null} when the jar does not contain it. */
    public static String text(String path) {
        try (InputStream in = Resources.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }

    /** Codes of the word lists inside the jar ({@code wordlists/<code>.yml}), from {@code wordlists-index.txt}. */
    public static List<String> bundledWordListCodes() {
        List<String> out = new ArrayList<>();
        String index = text("/wordlists-index.txt");
        if (index != null) {
            for (String line : index.split("\n")) {
                String code = line.trim();
                if (!code.isEmpty() && !code.startsWith("#")) {
                    out.add(code);
                }
            }
        }
        return out;
    }

    public static String bundledWordList(String code) {
        return text("/wordlists/" + code + ".yml");
    }
}
