package com.jason.plugins.antibadwords.engine.command;

import static com.jason.plugins.antibadwords.engine.command.Chat.GOLD;
import static com.jason.plugins.antibadwords.engine.command.Chat.GRAY;
import static com.jason.plugins.antibadwords.engine.command.Chat.WHITE;
import static com.jason.plugins.antibadwords.engine.command.Chat.GREEN;
import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.WordMatcher;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * {@code /abw whitelist add <word> | remove <word> | list [page]}: words that are never blocked
 * ({@code filters.whitelist} in config.yml). Case does not matter; a change works at once, without a restart.
 */
public final class WhitelistCommand implements SubCommand {
    @Override
    public String name() {
        return "whitelist";
    }

    @Override
    public void execute(CommandContext c) {
        String action = c.args().length == 0 ? "" : c.args()[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> add(c);
            case "remove" -> remove(c);
            case "list" -> list(c);
            default -> c.usage("<add|remove|list> [word or page]");
        }
    }

    private static boolean contains(Engine engine, String word) {
        return engine.value(CoreSettings.WHITELIST).stream().anyMatch(w -> w.trim().equalsIgnoreCase(word));
    }

    private static void add(CommandContext c) {
        String word = WordMatcher.cleanEntry(c.joined(1));
        if (word.isEmpty()) {
            c.usage("add <word or phrase>");
        } else if (!WordMatcher.isValidEntry(word)) {
            c.send(Chat.error("That is not a valid word (use at least 2 letters or digits)."));
        } else if (contains(c.engine(), word)) {
            c.send(Chat.error("This word is already on the whitelist: " + Chat.val(word) + "."));
        } else {
            try {
                c.engine().addToList(CoreSettings.WHITELIST, word);
                c.send(Chat.ok("The word is added to the whitelist: " + Chat.val(word) + "."));
                c.audit();
            } catch (IOException | RuntimeException ex) {
                c.send(Chat.error("The plugin cannot save config.yml: " + ex.getMessage()));
            }
        }
    }

    private static void remove(CommandContext c) {
        String word = WordMatcher.cleanEntry(c.joined(1));
        if (word.isEmpty()) {
            c.usage("remove <word or phrase>");
            return;
        }
        try {
            if (c.engine().removeFromList(CoreSettings.WHITELIST, w -> w.trim().equalsIgnoreCase(word))) {
                c.send(Chat.ok("The word is removed from the whitelist: " + Chat.val(word) + "."));
                c.audit();
            } else {
                c.send(Chat.error("This word is not on the whitelist: " + Chat.val(word) + "."));
            }
        } catch (IOException | RuntimeException ex) {
            c.send(Chat.error("The plugin cannot save config.yml: " + ex.getMessage()));
        }
    }

    private static void list(CommandContext c) {
        List<String> words = c.engine().value(CoreSettings.WHITELIST);
        if (words.isEmpty()) {
            c.send(Chat.warn("The whitelist is empty."));
            return;
        }
        int pageSize = c.engine().value(CoreSettings.LIST_PAGE_SIZE);
        int pages = (words.size() + pageSize - 1) / pageSize;
        int page = 1;
        if (c.args().length >= 2) {
            try {
                page = Integer.parseInt(c.args()[1]);
            } catch (NumberFormatException ex) {
                c.usage("list [page number]");
                return;
            }
        }
        page = Math.max(1, Math.min(pages, page));
        c.send(Chat.heading("Whitelist") + GRAY + " (" + WHITE + words.size() + GRAY + " words), page " + WHITE + page + GRAY + " of " + WHITE + pages + GRAY + ":");
        c.send(GRAY + String.join(", ", words.subList((page - 1) * pageSize, Math.min(words.size(), page * pageSize))));
        if (page < pages) {
            c.send(Chat.hint("Next page: /" + c.label() + " whitelist list " + (page + 1)));
        }
    }

    @Override
    public List<String> complete(CommandContext c) {
        if (c.args().length == 1) {
            return CommandRegistry.prefix(List.of("add", "remove", "list"), c.args()[0]);
        }
        if (c.args().length == 2 && c.args()[0].equalsIgnoreCase("remove")) {
            return CommandRegistry.prefix(c.engine().value(CoreSettings.WHITELIST), c.args()[1]);
        }
        return List.of();
    }
}
