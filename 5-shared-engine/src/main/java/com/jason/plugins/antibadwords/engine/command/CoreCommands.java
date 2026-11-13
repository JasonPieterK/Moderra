package com.jason.plugins.antibadwords.engine.command;

import static com.jason.plugins.antibadwords.engine.command.Chat.GOLD;
import static com.jason.plugins.antibadwords.engine.command.Chat.GRAY;
import static com.jason.plugins.antibadwords.engine.command.Chat.GREEN;
import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.WHITE;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.WordMatcher;
import com.jason.plugins.antibadwords.engine.BadWordCheck;
import com.jason.plugins.antibadwords.engine.EffectiveSettings;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Hit;
import com.jason.plugins.antibadwords.engine.TextCheck;
import com.jason.plugins.antibadwords.engine.Verdict;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.filters.EvasionSettings;
import com.jason.plugins.antibadwords.engine.filters.LinkSettings;
import com.jason.plugins.antibadwords.engine.filters.SpamSettings;
import com.jason.plugins.antibadwords.engine.PlayerRef;
import com.jason.plugins.antibadwords.engine.config.ConfigProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** The sub-commands the plugin always had (same words, same answers), plus {@code lang} and {@code whitelist}. */
public final class CoreCommands {
    /** Tab completion offers at most this many offline names (the list of known players can be long). */
    private static final int MAX_OFFLINE_SUGGESTIONS = 50;

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
        Engine engine = c.engine();
        Verdict verdict = engine.explain(c.joined(0));
        if (!verdict.hasHits()) {
            c.send(GREEN + "Clean: no blocked word found.");
        }
        for (Hit hit : verdict.hits()) {
            if (BadWordCheck.ID.equals(hit.check())) {
                c.send(RED + "Blocked: matches '" + hit.detail() + "'.");
                c.send(GRAY + "Check: " + WHITE + hit.check() + GRAY + " (word list entry '" + hit.detail() + "', level "
                    + verdict.settings().tierOf(hit.detail()) + ")");
                continue;
            }
            switch (hit.action()) {
                case BLOCK -> c.send(RED + "Blocked by check " + WHITE + hit.check() + RED + ": " + hit.detail() + ".");
                case CENSOR -> {
                    c.send(YELLOW + "Cleaned by check " + WHITE + hit.check() + YELLOW + ": " + hit.detail() + ".");
                    c.send(GRAY + "Text after cleaning: " + WHITE + hit.replacement());
                }
                default -> c.send(YELLOW + "Noted (not stopped) by check " + WHITE + hit.check() + YELLOW + ": " + hit.detail() + ".");
            }
        }
        List<String> skipped = engine.checks(verdict.settings()).stream()
            .filter(check -> check.id().startsWith("spam-") && check.enabled(verdict.settings()))
            .map(TextCheck::id).filter(id -> id.equals("spam-rate") || id.equals("spam-repeat") || id.equals("spam-commands")).toList();
        if (!skipped.isEmpty()) {
            c.send(GRAY + "Not tested here (they count real messages over time): " + String.join(", ", skipped) + ".");
        }
    }

    private static void violations(CommandContext c) {
        PlayerRef target = requireKnownPlayer(c);
        if (target != null) {
            c.send(YELLOW + target.name() + " violations: " + c.engine().strikes().get(target.id()) + "/"
                + c.engine().settings().legacy().violationsBeforePunish() + offlineNote(c, target));
        }
    }

    private static void resetViolations(CommandContext c) {
        PlayerRef target = requireKnownPlayer(c);
        if (target != null) {
            c.engine().strikes().reset(target.id());
            c.send(GREEN + "Violations reset for " + target.name() + "." + offlineNote(c, target));
            c.audit();
        }
    }

    private static void info(CommandContext c) {
        Engine engine = c.engine();
        AntiBadWordsSettings s = engine.settings().legacy();
        c.send(GOLD + "AntiBadWords " + engine.version());
        c.send(GRAY + "Blocked words: " + WHITE + engine.settings().matcher().size() + GRAY + " | Punishment: " + WHITE
            + (s.punishmentEnabled() ? "on" : "off") + GRAY + " after " + WHITE + s.violationsBeforePunish() + GRAY + " violations");
        c.send(GRAY + "Scanning: " + WHITE + scanSummary(engine.settings()));
        EffectiveSettings e = engine.settings();
        String spam = !e.get(SpamSettings.ENABLED) ? "off" : "on (" + engine.checkIds().stream().filter(id -> id.startsWith("spam-"))
            .filter(id -> engine.check(id).enabled(e)).map(id -> id.substring(5)).collect(java.util.stream.Collectors.joining(", ")) + ")";
        c.send(GRAY + "Filters: spam " + WHITE + spam + GRAY + " | links " + WHITE + e.get(LinkSettings.MODE) + GRAY + " | bypass tricks "
            + WHITE + e.get(EvasionSettings.DETECT_ACTION));
        c.send(GRAY + "Check order: " + WHITE + engine.checks(e).stream().map(TextCheck::id).collect(java.util.stream.Collectors.joining(", ")));
    }

    /** The player named by the first argument: online, or offline but seen before (data/players.json). */
    private static PlayerRef requireKnownPlayer(CommandContext c) {
        if (c.args().length < 1) {
            c.usage("<player>");
            return null;
        }
        PlayerRef target = c.engine().resolvePlayer(c.args()[0]).orElse(null);
        if (target == null) {
            c.send(RED + "Player not found (not online and never seen on this server).");
        }
        return target;
    }

    private static String offlineNote(CommandContext c, PlayerRef target) {
        return c.engine().isOnline(target.id()) ? "" : GRAY + " (offline)";
    }

    /** Online players first, then known offline players. */
    private static List<String> playerNames(CommandContext c) {
        if (c.args().length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>(
            CommandRegistry.prefix(c.engine().platform().onlinePlayers().stream().map(PlayerRef::name).toList(), c.args()[0]));
        for (String known : c.engine().players().names(c.args()[0], MAX_OFFLINE_SUGGESTIONS)) {
            if (names.stream().noneMatch(known::equalsIgnoreCase)) {
                names.add(known);
            }
        }
        return names;
    }

    static String scanSummary(EffectiveSettings e) {
        AntiBadWordsSettings s = e.legacy();
        List<String> on = new ArrayList<>();
        if (s.scanChat()) on.add("chat");
        if (s.scanCommands()) on.add("commands");
        if (s.scanSigns()) on.add(e.get(CoreSettings.SCAN_SIGN_BACK) ? "signs" : "signs (front side only)");
        if (s.scanBooks()) on.add("books");
        if (s.scanAnvil()) on.add("anvil");
        if (e.get(CoreSettings.SCAN_JOIN)) on.add("player names on join (" + e.get(CoreSettings.JOIN_ACTION) + ")");
        if (e.get(CoreSettings.SCAN_COMMAND_BLOCKS)) on.add("command blocks");
        return on.isEmpty() ? "nothing" : String.join(", ", on);
    }
}
