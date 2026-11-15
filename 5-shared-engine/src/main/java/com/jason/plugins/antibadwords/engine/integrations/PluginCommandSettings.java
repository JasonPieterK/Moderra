package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;

/**
 * Settings of the "text typed into another plugin's command" integrations (mcMMO, Towny, Factions, Citizens, DecentHolograms),
 * file {@code integrations.yml}, keys {@code integrations.<plugin>.*}. Bukkit family only.
 */
public final class PluginCommandSettings {
    private static final String FILE = "integrations.yml";
    private static final String CATEGORY = "Integrations";
    private static final String SYNTAX = "Each entry is a command label, optionally followed by sub-words (for example \"npc rename\"): the text after it is\n"
        + "checked like chat. A bare label (\"tc\") checks every argument. A blocked command never runs. commands.ignored-commands still wins.\n"
        + "Command names are from memory of each plugin and can be edited; a missing command is simply never matched. Bukkit family only.";

    public static final Setting<Boolean> MCMMO = on("mcmmo", "mcMMO party and admin chat commands");
    public static final Setting<List<String>> MCMMO_COMMANDS = list("mcmmo", "mcMMO commands that are scanned",
        List.of("p", "pc", "a", "ac", "party rename", "party create", "party password"));
    public static final Setting<Boolean> TOWNY = on("towny", "Towny chat and naming commands");
    public static final Setting<List<String>> TOWNY_COMMANDS = list("towny", "Towny commands that are scanned",
        List.of("tc", "nc", "lc", "gc", "townchat", "nationchat", "localchat", "generalchat", "town new", "town rename", "town set board",
            "town set tag", "nation new", "nation rename", "nation set board", "nation set tag"));
    public static final Setting<Boolean> FACTIONS = on("factions", "Factions naming commands");
    public static final Setting<List<String>> FACTIONS_COMMANDS = list("factions", "Factions commands that are scanned",
        List.of("f create", "f tag", "f desc", "f description", "f motd", "f title", "f name", "f rename"));
    public static final Setting<Boolean> CITIZENS = on("citizens", "Citizens NPC name commands");
    public static final Setting<List<String>> CITIZENS_COMMANDS = list("citizens", "Citizens commands that are scanned",
        List.of("npc create", "npc rename", "npc name"));
    public static final Setting<Boolean> DECENTHOLOGRAMS = on("decentholograms", "DecentHolograms line commands");
    public static final Setting<List<String>> DECENTHOLOGRAMS_COMMANDS = list("decentholograms", "DecentHolograms commands that are scanned",
        List.of("dh create", "dh line add", "dh line set", "dh line insert", "dh lines add", "dh lines set", "dh lines insert",
            "holo create", "holo line add", "holo line set", "holo line insert"));

    private PluginCommandSettings() {
    }

    private static Setting<Boolean> on(String plugin, String title) {
        return Setting.bool("integrations." + plugin + ".enabled", true).title(title + " on").file(FILE).category(CATEGORY)
            .description("Master switch. Only does something when the plugin is installed (detected while the server runs, logged at startup).\n"
                + "Bukkit family only; Fabric and Forge ignore it.").build();
    }

    private static Setting<List<String>> list(String plugin, String title, List<String> defaults) {
        return Setting.list("integrations." + plugin + ".scan-commands", defaults).title(title).file(FILE).category(CATEGORY)
            .description(SYNTAX).build();
    }

    public static void register(SettingsRegistry r) {
        List.of(MCMMO, MCMMO_COMMANDS, TOWNY, TOWNY_COMMANDS, FACTIONS, FACTIONS_COMMANDS, CITIZENS, CITIZENS_COMMANDS, DECENTHOLOGRAMS,
            DECENTHOLOGRAMS_COMMANDS).forEach(r::register);
    }
}
