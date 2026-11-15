package github.scarsz.discordsrv.api.commands;

import java.util.Set;

/** COMPILE-TIME SHIM, not part of the plugin jar. */
public interface SlashCommandProvider {
    Set<PluginSlashCommand> getSlashCommands();
}
