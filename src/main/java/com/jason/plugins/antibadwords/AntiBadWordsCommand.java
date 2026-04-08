package com.jason.plugins.antibadwords;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AntiBadWordsCommand implements CommandExecutor, TabCompleter {
    private final AntiBadWordsPlugin plugin;

    public AntiBadWordsCommand(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isAdmin = sender.hasPermission("antibadwords.admin");

        if (args.length == 0) {
            if (isAdmin) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <reload|addword|removeword|listwords|violations|resetviolations>");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " listwords [list-name]");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!isAdmin) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.loadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "AntiBadWords config reloaded.");
            }
            case "addword" -> {
                if (!isAdmin) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " addword <word>");
                    return true;
                }
                boolean ok = plugin.addCustomWord(args[1]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Added custom blocked word." : ChatColor.RED + "Word already exists or invalid.");
            }
            case "removeword" -> {
                if (!isAdmin) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " removeword <word>");
                    return true;
                }
                boolean ok = plugin.removeCustomWord(args[1]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Removed custom blocked word." : ChatColor.RED + "Word not found.");
            }
            case "listwords", "list" -> {
                String listName = args.length >= 2 ? args[1] : "active";
                List<String> words = plugin.listWords(listName);
                if (words.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No words found for list: " + listName);
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + listName.toUpperCase(Locale.ROOT) + " words (" + words.size() + "):");
                sender.sendMessage(ChatColor.GRAY + String.join(", ", words));
            }
            case "violations" -> {
                if (!isAdmin) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " violations <player>");
                    return true;
                }
                Player target = plugin.findOnlinePlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not online.");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " violations: " + plugin.getViolation(target.getUniqueId()));
            }
            case "resetviolations" -> {
                if (!isAdmin) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " resetviolations <player>");
                    return true;
                }
                Player target = plugin.findOnlinePlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not online.");
                    return true;
                }
                plugin.resetViolation(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Violations reset for " + target.getName() + ".");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Unknown subcommand.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("antibadwords.admin");
        if (args.length == 1) {
            if (isAdmin) {
                return prefix(Arrays.asList("reload", "addword", "removeword", "listwords", "list", "violations", "resetviolations"), args[0]);
            }
            return prefix(Arrays.asList("listwords", "list"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("listwords") || args[0].equalsIgnoreCase("list"))) {
            return prefix(plugin.getAvailableLists(), args[1]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> values, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.startsWith(lower)).collect(Collectors.toList());
    }
}
