package com.jason.plugins.moderra;

import com.jason.plugins.moderra.engine.gui.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /moderra} (no aliases). Only forwards the typed words to the shared engine
 * ({@code engine.commands()}), which holds every sub-command, message, permission rule and tab completion.
 */
public final class ModerraCommand implements CommandExecutor, TabCompleter {
    private final ModerraPlugin plugin;

    public ModerraCommand(ModerraPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // The menu: bare /moderra or /moderra gui [page], players with moderra.gui only. Everyone else (console, players
        // without the permission) keeps the text commands of the engine.
        if (sender instanceof Player player) {
            boolean bare = args.length == 0;
            boolean gui = args.length >= 1 && args[0].equalsIgnoreCase("gui");
            if ((bare || gui) && BukkitPermissions.has(player, Menu.PERMISSION)) {
                plugin.getGui().open(player, gui && args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "");
                return true;
            }
            if (gui) {
                player.sendMessage("\u00a7cNo permission.");
                return true;
            }
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("gui")) {
            sender.sendMessage("\u00a7eThe menu can only be opened by a player in the game.");
            return true;
        }
        plugin.getEngine().commands().execute(wrap(sender), label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>(plugin.getEngine().commands().complete(wrap(sender), args));
        if (args.length == 1 && sender instanceof Player && BukkitPermissions.has(sender, Menu.PERMISSION)
            && "gui".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) && !out.contains("gui")) {
            out.add("gui");
        }
        return out;
    }

    public static com.jason.plugins.moderra.engine.command.CommandSender wrap(CommandSender sender) {
        return new com.jason.plugins.moderra.engine.command.CommandSender() {
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
                return BukkitPermissions.has(sender, permission);
            }

            @Override
            public void send(String message) {
                sender.sendMessage(message);
            }
        };
    }
}
