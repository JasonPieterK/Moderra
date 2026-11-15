package github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build;

import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;

/** COMPILE-TIME SHIM, not part of the plugin jar. Only {@code CommandData(String,String)} and {@code addOption(OptionType,String,String,boolean)}. */
public class CommandData {
    public CommandData(String name, String description) {
    }

    public CommandData addOption(OptionType type, String name, String description, boolean required) {
        return this;
    }
}
