package github.scarsz.discordsrv.api.commands;

import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import org.bukkit.plugin.Plugin;

/** COMPILE-TIME SHIM, not part of the plugin jar. Only the constructor {@code (Plugin, CommandData)} of the real final class. */
public final class PluginSlashCommand {
    public PluginSlashCommand(Plugin plugin, CommandData commandData) {
    }
}
