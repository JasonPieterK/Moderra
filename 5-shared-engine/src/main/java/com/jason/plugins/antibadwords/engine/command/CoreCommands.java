package com.jason.plugins.antibadwords.engine.command;

import static com.jason.plugins.antibadwords.engine.command.Chat.GOLD;
import static com.jason.plugins.antibadwords.engine.command.Chat.GRAY;
import static com.jason.plugins.antibadwords.engine.command.Chat.GREEN;
import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.WHITE;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.WordMatcher;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.PlayerRef;
import com.jason.plugins.antibadwords.engine.config.ConfigProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** The sub-commands the plugin always had (same words, same answers), plus {@code lang} and {@code whitelist}. */
public final class CoreCommands {
    private CoreCommands() {
    }

    public static void register(CommandRegistry r) {
        r.register(simple("reload", List.of(), CoreCommands::reload, null));
        r.register(simple("addword", List.of(), CoreCommands::addWord, null));
        r.register(simple("removeword", List.of(), CoreCommands::removeWord,
            c -> c.args().length == 1 ? CommandRegistry.prefix(c.engine().customWords(), c.args()[0]) : List.of()));
        r.register(simple("listwords", List.of("list"), CoreCommands::listWords,
            c -> c.args().length == 1 ? CommandRegistry.prefix(c.engine().availableLists(), c.args()[0]) : List.of()));
        r.register(simple("check", List.of(), CoreCommands::check, null));
        r.register(simple("violations", List.of(), CoreCommands::violations, CoreCommands::playerNames));
        r.register(simple("resetviolations", List.of(), CoreCommands::resetViolations, CoreCommands::playerNames));
        r.register(simple("info", List.of(), CoreCommands::info, null));
        r.register(new LangCommand());
        r.register(new WhitelistCommand());
    }

    private static SubCommand simple(String name, List<String> aliases, Consumer<CommandContext> run,
                                     Function<CommandContext, List<String>> completer) {
        return new SubCommand() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<String> aliases() {
                return aliases;
            }

            @Override
            public void execute(CommandContext context) {
                run.accept(context);
            }

            @Override
            public List<String> complete(CommandContext context) {
                return completer == null ? List.of() : completer.apply(context);
            }
        };
    }

    private static void reload(CommandContext c) {
        List<ConfigProblem> problems = c.engine().reload();
        c.send(GREEN + "AntiBadWords config reloaded (" + c.engine().settings().matcher().size() + " blocked words active).");
        if (!problems.isEmpty()) {
            c.send(YELLOW + "" + problems.size() + " config problem(s) were found (full list in the console):");
            problems.stream().limit(5).forEach(p -> c.send(RED + "- " + p));
        }
        c.audit();
    }

    private static void addWord(CommandContext c) {
        if (c.args().length < 1) {
            c.usage("<word or phrase>");
            return;
        }
        String word = c.joined(0);
        if (!WordMatcher.isValidEntry(word)) {
            c.send(RED + "That is not a valid word (use at least 2 letters or digits).");
        } else if (c.engine().addCustomWord(word)) {
            c.send(GREEN + "Added custom blocked word.");
            c.audit();
        } else {
            c.send(RED + "Word already exists.");
        }
    }

    private static void removeWord(CommandContext c) {
        if (c.args().length < 1) {
            c.usage("<word or phrase>");
            return;
        }
        boolean ok = c.engine().removeCustomWord(c.joined(0));
        c.send(ok ? GREEN + "Removed custom blocked word." : RED + "Word not found in the custom list.");
        if (ok) {
            c.audit();
        }
    }

    private static void listWords(CommandContext c) {
        String listName = c.args().length >= 1 ? c.args()[0] : "active";
        List<String> words = c.engine().listWords(listName);
        if (words.isEmpty()) {
            c.send(RED + "No words found for list: " + listName);
            return;
        }
        c.send(GOLD + listName.toUpperCase(Locale.ROOT) + " words (" + words.size() + "):");
        c.send(GRAY + String.join(", ", words));
    }

    private static void check(CommandContext c) {
        if (c.args().length < 1) {
            c.usage("<text>");
            return;
        }
        String matched = c.engine().find(c.joined(0));
        c.send(matched == null ? GREEN + "Clean: no blocked word found." : RED + "Blocked: matches '" + matched + "'.");
    }

    private static void violations(CommandContext c) {
        PlayerRef target = requireOnlinePlayer(c);
        if (target != null) {
            c.send(YELLOW + target.name() + " violations: " + c.engine().strikes().get(target.id()) + "/"
                + c.engine().settings().legacy().violationsBeforePunish());
        }
    }

    private static void resetViolations(CommandContext c) {
        PlayerRef target = requireOnlinePlayer(c);
        if (target != null) {
            c.engine().strikes().reset(target.id());
            c.send(GREEN + "Violations reset for " + target.name() + ".");
            c.audit();
        }
    }

    private static void info(CommandContext c) {
        Engine engine = c.engine();
        AntiBadWordsSettings s = engine.settings().legacy();
        c.send(GOLD + "AntiBadWords " + engine.version());
        c.send(GRAY + "Blocked words: " + WHITE + engine.settings().matcher().size() + GRAY + " | Punishment: " + WHITE
            + (s.punishmentEnabled() ? "on" : "off") + GRAY + " after " + WHITE + s.violationsBeforePunish() + GRAY + " violations");
        c.send(GRAY + "Scanning: " + WHITE + scanSummary(s));
    }

    private static PlayerRef requireOnlinePlayer(CommandContext c) {
        if (c.args().length < 1) {
            c.usage("<player>");
            return null;
        }
        PlayerRef target = c.engine().platform().findPlayer(c.args()[0]).orElse(null);
        if (target == null) {
            c.send(RED + "Player not online.");
        }
        return target;
    }

    private static List<String> playerNames(CommandContext c) {
        if (c.args().length != 1) {
            return List.of();
        }
        return CommandRegistry.prefix(c.engine().platform().onlinePlayers().stream().map(PlayerRef::name).toList(), c.args()[0]);
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
}
