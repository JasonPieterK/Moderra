package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.Engine;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bedrock (Geyser / Floodgate) player names: a prefix such as {@code .} in front and sometimes spaces inside. No Floodgate
 * API is used; Floodgate is only detected by its plugin name, and the names are handled as plain text.
 */
public final class BedrockNames {
    public static final String[] PLUGINS = {"floodgate", "Geyser-Spigot"};
    private final Engine engine;

    public BedrockNames(Engine engine) {
        this.engine = engine;
    }

    /** Whether Bedrock handling is on ({@code integrations.bedrock.mode}; auto = Floodgate or Geyser-Spigot is installed). */
    public boolean enabled() {
        String mode = engine.value(BanBedrockSettings.BEDROCK_MODE);
        if (BanBedrockSettings.MODE_ON.equals(mode)) {
            return true;
        }
        if (BanBedrockSettings.MODE_OFF.equals(mode)) {
            return false;
        }
        return detectedPlugin() != null;
    }

    /** The name of the installed Floodgate / Geyser plugin, or null. */
    public String detectedPlugin() {
        for (String name : PLUGINS) {
            if (engine.platform().hasPlugin(name)) {
                return name;
            }
        }
        return null;
    }

    private String prefix() {
        return engine.value(BanBedrockSettings.BEDROCK_PREFIX);
    }

    /** True for a name that starts with the Bedrock prefix (always false while Bedrock handling is off or the prefix is empty). */
    public boolean isBedrock(String name) {
        String prefix = prefix();
        return name != null && !prefix.isEmpty() && name.startsWith(prefix) && name.length() > prefix.length() && enabled();
    }

    /** The text the player-name filter looks at: the name without the Bedrock prefix when that is configured. */
    public String filterName(String name) {
        if (name != null && isBedrock(name) && engine.value(BanBedrockSettings.BEDROCK_STRIP)) {
            return name.substring(prefix().length());
        }
        return name;
    }

    /** The name as it is written in a console command: spaces replaced (Bedrock names only). */
    public String commandName(String name) {
        if (name == null || name.indexOf(' ') < 0 || !enabled()) {
            return name;
        }
        return name.replace(" ", engine.value(BanBedrockSettings.BEDROCK_SPACE));
    }

    /**
     * Other spellings under which the same player may be known: with and without the prefix, spaces and underscores swapped.
     * The typed name itself comes first.
     */
    public List<String> variants(String typed) {
        Set<String> out = new LinkedHashSet<>();
        if (typed == null || typed.isEmpty()) {
            return List.of();
        }
        out.add(typed);
        if (!enabled()) {
            return List.copyOf(out);
        }
        String prefix = prefix();
        List<String> bases = new java.util.ArrayList<>();
        bases.add(typed);
        if (!prefix.isEmpty()) {
            if (typed.startsWith(prefix) && typed.length() > prefix.length()) {
                bases.add(typed.substring(prefix.length()));
            } else {
                bases.add(prefix + typed);
            }
        }
        for (String base : bases) {
            out.add(base);
            out.add(base.replace(' ', '_'));
            out.add(base.replace('_', ' '));
        }
        return List.copyOf(out);
    }
}
