package com.jason.plugins.antibadwords.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /antibadwords} (aliases {@code /abw}, {@code /antiwords}). Brigadier only forwards the raw argument text
 * to the shared engine ({@code engine.commands()}), so messages, sub-commands, permission handling and tab completion
 * are exactly the plugin's.
 */
public final class AbwCommand {
    private AbwCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String root : new String[] {"antibadwords", "abw", "antiwords"}) {
            dispatcher.register(build(root));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String root) {
        return Commands.literal(root)
            .executes(c -> run(c, root, ""))
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests((c, b) -> suggest(c, b))
                .executes(c -> run(c, root, StringArgumentType.getString(c, "args"))));
    }

    private static int run(CommandContext<CommandSourceStack> c, String label, String raw) {
        ModRuntime.engine.commands().execute(sender(c.getSource()), label, raw.isEmpty() ? new String[0] : raw.split(" "));
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            CommandContext<CommandSourceStack> c, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        List<String> completions = ModRuntime.engine.commands().complete(sender(c.getSource()), remaining.split(" ", -1));
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        completions.forEach(word::suggest);
        return word.buildFuture();
    }

    private static CommandSender sender(CommandSourceStack source) {
        ModRuntime.remember(source.getServer());
        return new CommandSender() {
            @Override
            public String name() {
                return source.getEntity() instanceof ServerPlayer p ? p.getName().getString() : "CONSOLE";
            }

            @Override
            public UUID id() {
                return source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
            }

            @Override
            public boolean has(String permission) {
                // The console and command blocks have every permission, like Bukkit's console sender.
                return !(source.getEntity() instanceof ServerPlayer p) || ModRuntime.has(p, permission);
            }

            @Override
            public void send(String message) {
                source.sendSuccess(() -> Component.literal(message), false);
            }
        };
    }
}
