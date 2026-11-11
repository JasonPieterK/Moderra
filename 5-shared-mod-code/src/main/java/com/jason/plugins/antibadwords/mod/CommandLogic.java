package com.jason.plugins.antibadwords.mod;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.WordMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /antibadwords} (aliases {@code /abw}, {@code /antiwords}): same sub-commands, messages and tab
 * completion as the plugin's AntiBadWordsCommand. Every sub-command needs {@code antibadwords.admin}.
 * Colour codes are written with {@code §} like Bukkit's {@code ChatColor}.
 */
public final class CommandLogic {
    /** Who ran the command. */
    public interface Actor {
        boolean isAdmin();

        void send(String message);

        /** Exact-name lookup among online players, or {@code null}. */
        Player findOnlinePlayer(String name);

        List<String> onlinePlayerNames();
    }

    public record Player(UUID id, String name) {
    }

    private static final String YELLOW = "§e";
    private static final String RED = "§c";
    private static final String GREEN = "§a";
    private static final String GOLD = "§6";
    private static final String GRAY = "§7";
    private static final String WHITE = "§f";
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
        "reload", "addword", "removeword", "listwords", "check", "violations", "resetviolations", "info");

    private final ModCore core;

    public CommandLogic(ModCore core) {
        this.core = core;
    }

    public void execute(Actor sender, String label, String[] args) {
        boolean isAdmin = sender.isAdmin();
        if (args.length == 0) {
            sender.send(isAdmin ? YELLOW + "Usage: /" + label + " <" + String.join("|", ADMIN_SUBCOMMANDS) + ">" : RED + "No permission.");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            sub = "listwords";
        }
        if (!isAdmin) {
            sender.send(RED + "No permission.");
            return;
        }
        switch (sub) {
            case "reload" -> {
                core.reload();
                sender.send(GREEN + "AntiBadWords config reloaded (" + core.matcher().size() + " blocked words active).");
            }
            case "addword" -> {
                if (args.length < 2) {
                    sender.send(YELLOW + "Usage: /" + label + " addword <word or phrase>");
                    return;
                }
                String word = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (!WordMatcher.isValidEntry(word)) {
                    sender.send(RED + "That is not a valid word (use at least 2 letters or digits).");
                } else if (core.addCustomWord(word)) {
                    sender.send(GREEN + "Added custom blocked word.");
                } else {
                    sender.send(RED + "Word already exists.");
                }
            }
            case "removeword" -> {
                if (args.length < 2) {
                    sender.send(YELLOW + "Usage: /" + label + " removeword <word or phrase>");
                    return;
                }
                boolean ok = core.removeCustomWord(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                sender.send(ok ? GREEN + "Removed custom blocked word." : RED + "Word not found in the custom list.");
            }
            case "listwords" -> {
                String listName = args.length >= 2 ? args[1] : "active";
                List<String> words = core.listWords(listName);
                if (words.isEmpty()) {
                    sender.send(RED + "No words found for list: " + listName);
                    return;
                }
                sender.send(GOLD + listName.toUpperCase(Locale.ROOT) + " words (" + words.size() + "):");
                sender.send(GRAY + String.join(", ", words));
            }
            case "check" -> {
                if (args.length < 2) {
                    sender.send(YELLOW + "Usage: /" + label + " check <text>");
                    return;
                }
                String matched = core.find(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                sender.send(matched == null ? GREEN + "Clean: no blocked word found." : RED + "Blocked: matches '" + matched + "'.");
            }
            case "violations" -> {
                Player target = requireOnlinePlayer(sender, label, "violations", args);
                if (target != null) {
                    sender.send(YELLOW + target.name() + " violations: " + core.getViolation(target.id()) + "/"
                        + core.settings().violationsBeforePunish());
                }
            }
            case "resetviolations" -> {
                Player target = requireOnlinePlayer(sender, label, "resetviolations", args);
                if (target != null) {
                    core.resetViolation(target.id());
                    sender.send(GREEN + "Violations reset for " + target.name() + ".");
                }
            }
            case "info" -> {
                AntiBadWordsSettings s = core.settings();
                sender.send(GOLD + "AntiBadWords " + core.version());
                sender.send(GRAY + "Blocked words: " + WHITE + core.matcher().size() + GRAY + " | Punishment: " + WHITE
                    + (s.punishmentEnabled() ? "on" : "off") + GRAY + " after " + WHITE + s.violationsBeforePunish()
                    + GRAY + " violations");
                sender.send(GRAY + "Scanning: " + WHITE + scanSummary(s));
            }
            default -> sender.send(YELLOW + "Unknown subcommand.");
        }
    }

    private Player requireOnlinePlayer(Actor sender, String label, String sub, String[] args) {
        if (args.length < 2) {
            sender.send(YELLOW + "Usage: /" + label + " " + sub + " <player>");
            return null;
        }
        Player target = sender.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.send(RED + "Player not online.");
        }
        return target;
    }

    static String scanSummary(AntiBadWordsSettings s) {
        List<String> on = new ArrayList<>();
        if (s.scanChat()) on.add("chat");
        if (s.scanCommands()) on.add("commands");
        if (s.scanSigns()) on.add("signs");
        if (s.scanBooks()) on.add("books");
        if (s.scanAnvil()) on.add("anvil");
        return on.isEmpty() ? "nothing" : String.join(", ", on);
    }

    public List<String> complete(Actor sender, String[] args) {
        boolean isAdmin = sender.isAdmin();
        if (args.length == 1) {
            return isAdmin ? prefix(ADMIN_SUBCOMMANDS, args[0]) : List.of();
        }
        if (!isAdmin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("listwords") || sub.equals("list"))) {
            return prefix(core.getAvailableLists(), args[1]);
        }
        if (args.length == 2 && sub.equals("removeword")) {
            return prefix(core.listWords("custom"), args[1]);
        }
        if (args.length == 2 && (sub.equals("violations") || sub.equals("resetviolations"))) {
            return prefix(sender.onlinePlayerNames(), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> values, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
