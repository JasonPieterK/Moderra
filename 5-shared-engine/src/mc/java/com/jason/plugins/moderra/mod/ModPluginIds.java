package com.jason.plugins.moderra.mod;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the plugin names the engine asks for ({@code Platform.hasPlugin("LuckPerms")}) to mod ids. The default is the name in
 * lower case without spaces, which is the mod id of most mods. The entries below are the ones that differ or are known.
 * A name with no mod behind it simply gives "not installed".
 */
public final class ModPluginIds {
    private static final Map<String, List<String>> KNOWN = Map.of(
        "placeholderapi", List.of("placeholder-api"), // Fabric "Text Placeholder API"
        "styledchat", List.of("styledchat"),
        "luckperms", List.of("luckperms"),
        "floodgate", List.of("floodgate"));

    private ModPluginIds() {
    }

    public static List<String> idsFor(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        List<String> known = KNOWN.get(key);
        return known != null ? known : List.of(key);
    }
}
