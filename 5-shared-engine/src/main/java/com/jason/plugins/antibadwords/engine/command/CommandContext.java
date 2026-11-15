package com.jason.plugins.antibadwords.engine.command;

import com.jason.plugins.antibadwords.engine.Engine;
import java.util.Arrays;

/**
 * One run of a sub-command.
 *
 * @param engine the engine
 * @param sender who typed it
 * @param label  the root command as typed ({@code abw}, {@code antibadwords} ...), for usage lines
 * @param sub    the sub-command name as typed
 * @param args   the words after the sub-command
 */
public record CommandContext(Engine engine, CommandSender sender, String label, String sub, String[] args) {
    public void send(String message) {
        sender.send(message);
    }

    /** {@code args[from..]} joined with spaces ("" when there are none). */
    public String joined(int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    /** Sends "Usage: /label sub ..." in yellow. */
    public void usage(String parameters) {
        sender.send(Chat.usage("/" + label + " " + sub + (parameters.isEmpty() ? "" : " " + parameters)));
    }

    /** Records that this command changed something (publishes an {@code admin-command} event). */
    public void audit() {
        if (sender.suppressAudit()) {
            return;
        }
        engine.publishAdminCommand(sender.id(), sender.name(), label + " " + sub + (args.length == 0 ? "" : " " + joined(0)));
    }
}
