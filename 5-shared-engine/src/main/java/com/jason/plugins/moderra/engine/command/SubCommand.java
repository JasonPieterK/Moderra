package com.jason.plugins.moderra.engine.command;

import com.jason.plugins.moderra.engine.Engine;
import java.util.List;

/**
 * One {@code /moderra <name>} sub-command. To add a command, write one class that implements this and register it with
 * {@code engine.commands().register(new MyCommand())}; permission check, usage line and tab completion of the name
 * are handled by the {@link CommandRegistry}.
 */
public interface SubCommand {
    /** The word after {@code /moderra}, lower-case. */
    String name();

    /** Other words that run the same command (not shown in the usage line or in tab completion). */
    default List<String> aliases() {
        return List.of();
    }

    /** Permission needed to run it (default: {@code moderra.admin}). */
    default String permission() {
        return Engine.PERMISSION_ADMIN;
    }

    void execute(CommandContext context);

    /** Suggestions for the last word of {@code context.args()} (the word being typed; may be empty). */
    default List<String> complete(CommandContext context) {
        return List.of();
    }
}
