package com.jason.plugins.antibadwords;

import java.util.ArrayList;
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

/**
 * {@code /antibadwords} (aliases {@code /abw}, {@code /antiwords}).
 *
 * <p>Every sub-command (including {@code listwords}) needs {@code antibadwords.admin}.
 */
public final class AntiBadWordsCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
        "reload", "addword", "removeword", "listwords", "check", "violations", "resetviolations", "info");

    private final AntiBadWordsPlugin plugin;

    public AntiBadWordsCommand(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isAdmin = sender.hasPermission(AntiBadWordsPlugin.PERMISSION_ADMIN);

        if (args.length == 0) {
            sender.sendMessage(isAdmin
                ? ChatColor.YELLOW + "Usage: /" + label + " <" + String.join("|", ADMIN_SUBCOMMANDS) + ">"
                : ChatColor.RED + "No permission.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        // "list" is an accepted shortcut for "listwords".
        if (sub.equals("list")) {
            sub = "listwords";
        }
        if (!isAdmin) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        switch (sub) {
            case "reload" -> {
                plugin.loadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "AntiBadWords config reloaded ("
                    + plugin.getMatcher().size() + " blocked words active).");
            }
            case "addword" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " addword <word or phrase>");
                    return true;
                }
                String word = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (!WordMatcher.isValidEntry(word)) {
                    sender.sendMessage(ChatColor.RED + "That is not a valid word (use at least 2 letters or digits).");
                } else if (plugin.addCustomWord(word)) {
                    sender.sendMessage(ChatColor.GREEN + "Added custom blocked word.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Word already exists.");
                }
            }
            case "removeword" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " removeword <word or phrase>");
                    return true;
                }
                String word = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                boolean ok = plugin.removeCustomWord(word);
                sender.sendMessage(ok ? ChatColor.GREEN + "Removed custom blocked word."
                    : ChatColor.RED + "Word not found in the custom list.");
            }
            case "listwords" -> {
                String listName = args.length >= 2 ? args[1] : "active";
                List<String> words = plugin.listWords(listName);
                if (words.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No words found for list: " + listName);
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + listName.toUpperCase(Locale.ROOT) + " words (" + words.size() + "):");
                sender.sendMessage(ChatColor.GRAY + String.join(", ", words));
            }
            case "check" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " check <text>");
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String matched = plugin.getMatcher().find(text);
                sender.sendMessage(matched == null
                    ? ChatColor.GREEN + "Clean: no blocked word found."
                    : ChatColor.RED + "Blocked: matches '" + matched + "'.");
            }
            case "violations" -> {
                Player target = requireOnlinePlayer(sender, label, "violations", args);
                if (target != null) {
                    sender.sendMessage(ChatColor.YELLOW + target.getName() + " violations: "
                        + plugin.getViolation(target.getUniqueId()) + "/" + plugin.getSettings().violationsBeforePunish());
                }
            }
            case "resetviolations" -> {
                Player target = requireOnlinePlayer(sender, label, "resetviolations", args);
                if (target != null) {
                    plugin.resetViolation(target.getUniqueId());
                    sender.sendMessage(ChatColor.GREEN + "Violations reset for " + target.getName() + ".");
                }
            }
            case "info" -> {
                AntiBadWordsSettings s = plugin.getSettings();
                sender.sendMessage(ChatColor.GOLD + "AntiBadWords " + plugin.getPluginMeta().getVersion());
                sender.sendMessage(ChatColor.GRAY + "Blocked words: " + ChatColor.WHITE + plugin.getMatcher().size()
                    + ChatColor.GRAY + " | Punishment: " + ChatColor.WHITE + (s.punishmentEnabled() ? "on" : "off")
                    + ChatColor.GRAY + " after " + ChatColor.WHITE + s.violationsBeforePunish() + ChatColor.GRAY + " violations");
                sender.sendMessage(ChatColor.GRAY + "Scanning: " + ChatColor.WHITE + scanSummary(s));
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Unknown subcommand.");
        }
        return true;
    }

    private Player requireOnlinePlayer(CommandSender sender, String label, String sub, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " " + sub + " <player>");
            return null;
        }
        Player target = plugin.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not online.");
        }
        return target;
    }

    private static String scanSummary(AntiBadWordsSettings s) {
        List<String> on = new ArrayList<>();
        if (s.scanChat()) on.add("chat");
        if (s.scanCommands()) on.add("commands");
        if (s.scanSigns()) on.add("signs");
        if (s.scanBooks()) on.add("books");
        if (s.scanAnvil()) on.add("anvil");
        return on.isEmpty() ? "nothing" : String.join(", ", on);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission(AntiBadWordsPlugin.PERMISSION_ADMIN);
        if (args.length == 1) {
            return isAdmin ? prefix(ADMIN_SUBCOMMANDS, args[0]) : List.of();
        }
        if (!isAdmin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("listwords") || sub.equals("list"))) {
            return prefix(plugin.getAvailableLists(), args[1]);
        }
        if (args.length == 2 && sub.equals("removeword")) {
            return prefix(plugin.listWords("custom"), args[1]);
        }
        if (args.length == 2 && (sub.equals("violations") || sub.equals("resetviolations"))) {
            return prefix(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> values, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
