package com.jason.plugins.antibadwords;

import java.util.List;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /antibadwords} (aliases {@code /abw}, {@code /antiwords}). Only forwards the typed words to the shared engine
 * ({@code engine.commands()}), which holds every sub-command, message, permission rule and tab completion.
 */
public final class AntiBadWordsCommand implements CommandExecutor, TabCompleter {
    private final AntiBadWordsPlugin plugin;

    public AntiBadWordsCommand(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getEngine().commands().execute(wrap(sender), label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return plugin.getEngine().commands().complete(wrap(sender), args);
    }

    private static com.jason.plugins.antibadwords.engine.command.CommandSender wrap(CommandSender sender) {
        return new com.jason.plugins.antibadwords.engine.command.CommandSender() {
            @Override
            public String name() {
                return sender.getName();
            }

            @Override
            public UUID id() {
                return sender instanceof Player player ? player.getUniqueId() : null;
            }

            @Override
            public boolean has(String permission) {
                return sender.hasPermission(permission);
            }

            @Override
            public void send(String message) {
                sender.sendMessage(message);
            }
        };
    }
}
