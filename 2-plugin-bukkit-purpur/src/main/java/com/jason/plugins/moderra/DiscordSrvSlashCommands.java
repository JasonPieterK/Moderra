package com.jason.plugins.moderra;

import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandPriority;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.plugin.Plugin;

/**
 * The slash commands of the DiscordSRV hook: {@code /moderra-mute}, {@code /moderra-unmute}, {@code /moderra-history}, {@code /moderra-strikes},
 * {@code /moderra-check} and {@code /moderra-reload}. DiscordSRV finds the handler methods by its own {@code @SlashCommand} annotation, which cannot
 * be put on a method by reflection, so this class is compiled against name-only shims ({@code src/discordsrv-shim}, not packaged)
 * and is loaded only by {@link DiscordSrvHook} after DiscordSRV was found. All decisions (allow-list, switches, channels, rate limit,
 * audit) are made by the engine in {@code DiscordLink#onSlashCommand}, the same code as for the prefix commands.
 *
 * <p>Every command is a top-level command (no sub-commands) so only {@code CommandData(String,String)} and
 * {@code addOption(OptionType,String,String,boolean)} of the shaded JDA are needed.
 */
public final class DiscordSrvSlashCommands implements SlashCommandProvider {
    /** The answer to a refused call; the same text as the engine uses. */
    static final String REFUSED = "[Moderra] Error. You cannot use this command here.";

    private final DiscordSrvHook hook;
    private final Plugin plugin;

    DiscordSrvSlashCommands(DiscordSrvHook hook, Plugin plugin) {
        this.hook = hook;
        this.plugin = plugin;
    }

    @Override
    public Set<PluginSlashCommand> getSlashCommands() {
        Set<PluginSlashCommand> out = new LinkedHashSet<>();
        out.add(new PluginSlashCommand(plugin, new CommandData("moderra-mute", "Mute a player")
            .addOption(OptionType.STRING, "player", "Player name", true)
            .addOption(OptionType.STRING, "details", "Time and reason, for example: 1h spam", false)));
        out.add(new PluginSlashCommand(plugin, new CommandData("moderra-unmute", "Remove the mute of a player")
            .addOption(OptionType.STRING, "player", "Player name", true)));
        out.add(new PluginSlashCommand(plugin, new CommandData("moderra-history", "Show the violations of a player")
            .addOption(OptionType.STRING, "player", "Player name", true)
            .addOption(OptionType.STRING, "page", "Page number", false)));
        out.add(new PluginSlashCommand(plugin, new CommandData("moderra-strikes", "Show the strike points of a player")
            .addOption(OptionType.STRING, "player", "Player name", true)));
        out.add(new PluginSlashCommand(plugin, new CommandData("moderra-check", "Test a text with the word filter")
            .addOption(OptionType.STRING, "text", "The text to test", true)));
        out.add(new PluginSlashCommand(plugin, new CommandData("moderra-reload", "Reload the plugin configuration")));
        return out;
    }

    /**
     * The values of the options in the order the engine reads them: {@code player} first, then {@code details}, {@code page} or the
     * text. Joined by spaces; the engine checks every word again.
     */
    static String arguments(String word, Map<String, String> options) {
        StringBuilder out = new StringBuilder();
        for (String name : new String[] {"player", "details", "page", "text"}) {
            String value = options.get(name);
            if (value != null && !value.isBlank() && !"null".equals(value)) {
                out.append(out.length() == 0 ? "" : " ").append(value.strip());
            }
        }
        return out.toString();
    }

    @SlashCommand(path = "moderra-mute", priority = SlashCommandPriority.NORMAL, deferReply = false, deferEphemeral = false, ignoreAcknowledged = true)
    public void mute(SlashCommandEvent event) {
        hook.onSlash(event, "mute");
    }

    @SlashCommand(path = "moderra-unmute", priority = SlashCommandPriority.NORMAL, deferReply = false, deferEphemeral = false, ignoreAcknowledged = true)
    public void unmute(SlashCommandEvent event) {
        hook.onSlash(event, "unmute");
    }

    @SlashCommand(path = "moderra-history", priority = SlashCommandPriority.NORMAL, deferReply = false, deferEphemeral = false, ignoreAcknowledged = true)
    public void history(SlashCommandEvent event) {
        hook.onSlash(event, "history");
    }

    @SlashCommand(path = "moderra-strikes", priority = SlashCommandPriority.NORMAL, deferReply = false, deferEphemeral = false, ignoreAcknowledged = true)
    public void strikes(SlashCommandEvent event) {
        hook.onSlash(event, "strikes");
    }

    @SlashCommand(path = "moderra-check", priority = SlashCommandPriority.NORMAL, deferReply = false, deferEphemeral = false, ignoreAcknowledged = true)
    public void check(SlashCommandEvent event) {
        hook.onSlash(event, "check");
    }

    @SlashCommand(path = "moderra-reload", priority = SlashCommandPriority.NORMAL, deferReply = false, deferEphemeral = false, ignoreAcknowledged = true)
    public void reload(SlashCommandEvent event) {
        hook.onSlash(event, "reload");
    }
}
