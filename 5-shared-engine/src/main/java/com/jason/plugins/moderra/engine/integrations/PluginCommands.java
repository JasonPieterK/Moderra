package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.mod.Names;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scans text that players type into the commands of mcMMO, Towny, Factions, Citizens and DecentHolograms (party / town / nation
 * chat, names, boards, NPC names, hologram lines). Works only on the command line, so no reflection and no event guessing:
 * a plugin counts as "found" when {@code Platform.hasPlugin} says so. Their normal (toggled) chat modes arrive as ordinary chat events.
 */
public final class PluginCommands {
    /** One integration: display name, plugin names to look for, switch, command list. */
    private record Group(String name, List<String> plugins, Setting<Boolean> enabled, Setting<List<String>> commands) {
    }

    private static final List<Group> GROUPS = List.of(
        new Group("mcMMO", List.of("mcMMO"), PluginCommandSettings.MCMMO, PluginCommandSettings.MCMMO_COMMANDS),
        new Group("Towny", List.of("Towny"), PluginCommandSettings.TOWNY, PluginCommandSettings.TOWNY_COMMANDS),
        new Group("Factions", List.of("Factions", "FactionsUUID", "SaberFactions", "FactionsKore"), PluginCommandSettings.FACTIONS,
            PluginCommandSettings.FACTIONS_COMMANDS),
        new Group("Citizens", List.of("Citizens"), PluginCommandSettings.CITIZENS, PluginCommandSettings.CITIZENS_COMMANDS),
        new Group("DecentHolograms", List.of("DecentHolograms"), PluginCommandSettings.DECENTHOLOGRAMS,
            PluginCommandSettings.DECENTHOLOGRAMS_COMMANDS));

    /** The plugin names that can be looked for. */
    public static final List<String> PLUGIN_NAMES = GROUPS.stream().flatMap(g -> g.plugins().stream()).toList();

    private final Engine engine;

    public PluginCommands(Engine engine) {
        this.engine = engine;
    }

    /** Names of the integrations that are switched on and whose plugin is installed. */
    public List<String> active() {
        List<String> out = new ArrayList<>();
        for (Group g : GROUPS) {
            if (isActive(g)) {
                out.add(g.name());
            }
        }
        return out;
    }

    /** One startup log line when something was found (nothing otherwise). */
    public void logFound() {
        List<String> found = active();
        if (!found.isEmpty()) {
            engine.platform().logger().info("Integrations: scanning the text commands of " + String.join(", ", found) + ".");
        }
    }

    private boolean isActive(Group g) {
        if (!engine.value(g.enabled())) {
            return false;
        }
        for (String plugin : g.plugins()) {
            if (engine.platform().hasPlugin(plugin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For a command line such as {@code /npc rename Bob}: the text that must be scanned, or {@code null} when no active integration
     * lists it. {@code commands.ignored-commands} wins.
     */
    public String extraCommandText(Set<String> ignoredCommands, String commandLine) {
        String[] words = commandLine.strip().split("\\s+");
        if (words.length < 2) {
            return null;
        }
        String label = Names.commandLabel(words[0]);
        if (ignoredCommands.contains(label)) {
            return null;
        }
        for (Group g : GROUPS) {
            if (!isActive(g)) {
                continue;
            }
            for (String entry : engine.value(g.commands())) {
                String[] parts = entry.strip().toLowerCase(Locale.ROOT).split("\\s+");
                if (parts[0].isEmpty() || !parts[0].equals(label) || parts.length >= words.length) {
                    continue;
                }
                boolean same = true;
                for (int i = 1; i < parts.length; i++) {
                    if (!parts[i].equals(words[i].toLowerCase(Locale.ROOT))) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    return String.join(" ", Arrays.copyOfRange(words, parts.length, words.length));
                }
            }
        }
        return null;
    }
}
