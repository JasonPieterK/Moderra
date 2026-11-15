package com.jason.plugins.moderra.engine.command;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.gui.Menu;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /moderra}: finds the sub-command, checks its permission and
 * runs it. The loaders only forward the typed words; all texts and rules live here, so every server type answers the same.
 */
public final class CommandRegistry {
    private final Engine engine;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();

    public CommandRegistry(Engine engine) {
        this.engine = engine;
    }

    /** Adds (or replaces) a sub-command. The order of registration is the order in the usage line. */
    public void register(SubCommand command) {
        commands.put(command.name().toLowerCase(Locale.ROOT), command);
    }

    /** Names of all sub-commands in registration order. */
    public List<String> names() {
        return new ArrayList<>(commands.keySet());
    }

    public SubCommand find(String word) {
        String key = word.toLowerCase(Locale.ROOT);
        SubCommand direct = commands.get(key);
        if (direct != null) {
            return direct;
        }
        return commands.values().stream().filter(c -> c.aliases().contains(key)).findFirst().orElse(null);
    }

    private List<String> allowedNames(CommandSender sender) {
        return commands.values().stream().filter(c -> sender.has(c.permission())).map(SubCommand::name).toList();
    }

    /**
     * Runs {@code /label args...}.
     *
     * @param args the words after the root command (empty array for a bare {@code /moderra})
     */
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 && sender.id() != null && sender.has(Menu.PERMISSION) && engine.platform().supportsMenu()) {
            GuiCommand.open(engine, sender);
            return;
        }
        List<String> allowed = allowedNames(sender);
        if (allowed.isEmpty()) {
            sender.send(Chat.error("No permission."));
            return;
        }
        if (args.length == 0) {
            sender.send(Chat.usage("/" + label + " <" + String.join("|", allowed) + ">"));
            return;
        }
        SubCommand command = find(args[0]);
        if (command == null) {
            sender.send(Chat.warn("Unknown subcommand."));
            return;
        }
        if (!sender.has(command.permission())) {
            sender.send(Chat.error("No permission."));
            return;
        }
        try {
            command.execute(new CommandContext(engine, sender, label, args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length)));
        } catch (RuntimeException ex) {
            sender.send(Chat.error("The command failed: " + ex.getMessage()));
            engine.platform().logger().warn("Command /" + label + " " + args[0] + " failed: " + ex);
        }
    }

    /**
     * Tab completion.
     *
     * @param args the words typed so far after the root command; the last one is the word being completed (may be "")
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(allowedNames(sender), args[0]);
        }
        SubCommand command = find(args[0]);
        if (command == null || !sender.has(command.permission())) {
            return List.of();
        }
        return command.complete(new CommandContext(engine, sender, "moderra", args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length)));
    }

    /** The values that start with what was typed (case is ignored). */
    public static List<String> prefix(List<String> values, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
