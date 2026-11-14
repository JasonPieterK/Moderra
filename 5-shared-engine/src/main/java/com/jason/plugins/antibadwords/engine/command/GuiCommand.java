package com.jason.plugins.antibadwords.engine.command;

import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.gui.Menu;

/**
 * {@code /abw gui}: opens the settings menu for a player who has {@code antibadwords.gui}. A bare {@code /abw} does the
 * same (see {@link CommandRegistry#execute}). The optional page argument of the handoff is not used: the menu always
 * starts on its main page.
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
        open(c.engine(), c.sender());
    }

    static void open(Engine engine, CommandSender sender) {
        if (sender.id() == null) {
            sender.send(RED + "The menu can only be opened by a player.");
        } else if (!sender.has(Menu.PERMISSION)) {
            sender.send(RED + "No permission.");
        } else if (!engine.platform().supportsMenu() || !engine.platform().openMenu(sender.id())) {
            sender.send(YELLOW + "The settings menu is not available on this server. Edit the files in the config folder instead.");
        }
    }
}
