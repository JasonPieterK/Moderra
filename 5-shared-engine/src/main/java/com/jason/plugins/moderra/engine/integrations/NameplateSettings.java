package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import java.util.List;

/** Settings for Vault, TAB and NametagEdit ({@code integrations.yml}). All optional; each does nothing when the plugin is missing. */
public final class NameplateSettings {
    public static final String FILE = "integrations.yml";
    private static final String CATEGORY = "Integrations";
    private static final String BUKKIT = "BUKKIT FAMILY ONLY. ";

    public static final Setting<Boolean> VAULT = s(Setting.bool("integrations.vault.enabled", true).title("Vault hook")
        .description(BUKKIT + "Use Vault (and the permission plugin behind it) for the player's group and chat prefix when LuckPerms is not\n"
            + "connected: rule profiles by group (the map integrations.luckperms.group-profiles is used) and the {prefix} / {group}\n"
            + "placeholders below. Meta values and offline permission checks need LuckPerms. Needs a restart."));
    public static final Setting<Boolean> MESSAGE_PLACEHOLDERS = s(Setting.bool("integrations.vault.message-placeholders", true)
        .title("{prefix} and {group} in messages")
        .description("The lines people read (warnings, broadcasts, staff notices) may use {prefix} (the chat prefix from Vault or LuckPerms)\n"
            + "and {group} (the primary group). Both are empty when no permission plugin is connected. Never used in commands."));
    public static final Setting<Boolean> TAB = s(Setting.bool("integrations.tab.enabled", true).title("TAB: scan its commands")
        .description(BUKKIT + "While the TAB plugin is installed, the arguments of the commands below are checked like chat (a blocked command never\n"
            + "runs). Only its commands are scanned: no TAB event for player-set text is used."));
    public static final Setting<List<String>> TAB_COMMANDS = s(Setting.list("integrations.tab.scan-commands", List.of("tab"))
        .title("TAB commands that are scanned")
        .description(BUKKIT + "Command labels (without /), for example /tab player Bob tabprefix <text>. commands.ignored-commands still wins."));
    public static final Setting<Boolean> NAMETAGEDIT = s(Setting.bool("integrations.nametagedit.enabled", true)
        .title("NametagEdit: scan its commands")
        .description(BUKKIT + "While NametagEdit is installed, the arguments of the commands below are checked like chat (prefix and suffix texts)."));
    public static final Setting<List<String>> NAMETAGEDIT_COMMANDS = s(Setting.list("integrations.nametagedit.scan-commands",
            List.of("nte", "nametagedit"))
        .title("NametagEdit commands that are scanned")
        .description(BUKKIT + "Command labels (without /), for example /nte player Bob prefix <text>. commands.ignored-commands still wins."));

    private NameplateSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry r) {
        List.of(VAULT, MESSAGE_PLACEHOLDERS, TAB, TAB_COMMANDS, NAMETAGEDIT, NAMETAGEDIT_COMMANDS).forEach(r::register);
    }
}
