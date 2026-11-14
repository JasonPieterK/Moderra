package com.jason.plugins.antibadwords.engine.command;

import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.gui.Menu;

/**
 * {@code /abw gui}: opens the settings menu for a player who has {@code antibadwords.gui}. A bare {@code /abw} does the
 * same (see {@link CommandRegistry#execute}). {@code /abw gui <page>} opens at a page: a category name, a special
 * page (ladder, profiles, rules, languages, words, mutes), a setting key or a number; see {@link Menu#open(Engine, String)}.
 */
public final class GuiCommand implements SubCommand {
    @Override
    public String name() {
        return "gui";
    }

    @Override
    public String permission() {
        return Menu.PERMISSION;
    }

    @Override
    public void execute(CommandContext c) {
        open(c.engine(), c.sender(), String.join(" ", c.args()));
    }

    @Override
    public java.util.List<String> complete(CommandContext c) {
        if (c.args().length != 1) {
            return java.util.List.of();
        }
        java.util.List<String> pages = new java.util.ArrayList<>(java.util.List.of("languages", "words", "ladder", "mutes", "profiles", "rules"));
        for (String category : c.engine().registry().categories()) {
            pages.add(category.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
        }
        return CommandRegistry.prefix(pages, c.args()[0]);
    }

    static void open(Engine engine, CommandSender sender) {
        open(engine, sender, "");
    }

    static void open(Engine engine, CommandSender sender, String page) {
        if (sender.id() == null) {
            sender.send(RED + "The menu can only be opened by a player.");
        } else if (!sender.has(Menu.PERMISSION)) {
            sender.send(RED + "No permission.");
        } else if (!engine.platform().supportsMenu()) {
            sender.send(YELLOW + "The settings menu is not available on this server. Edit the files in the config folder instead.");
        } else {
            if (!page.isBlank() && !Menu.opensPage(new com.jason.plugins.antibadwords.engine.gui.EngineConfigAccess(engine), page)) {
                sender.send(YELLOW + "Unknown menu page \"" + page + "\"; opening the main menu. Pages: a category name, languages, words, ladder, "
                    + "mutes, profiles, rules, a setting key or a number.");
            }
            if (!engine.platform().openMenu(sender.id(), page)) {
                sender.send(YELLOW + "The settings menu is not available on this server. Edit the files in the config folder instead.");
            }
        }
    }
}
