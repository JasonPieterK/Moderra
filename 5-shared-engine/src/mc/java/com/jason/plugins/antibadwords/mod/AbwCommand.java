package com.jason.plugins.antibadwords.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /antibadwords} (aliases {@code /abw}, {@code /antiwords}). Brigadier only forwards the raw argument text
 * to {@link CommandLogic}, so messages, sub-commands, permission handling and tab completion are exactly the
 * plugin's.
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
        new CommandLogic(ModRuntime.core).execute(actor(c.getSource()), label, raw.isEmpty() ? new String[0] : raw.split(" "));
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            CommandContext<CommandSourceStack> c, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        List<String> completions = new CommandLogic(ModRuntime.core).complete(actor(c.getSource()), remaining.split(" ", -1));
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        completions.forEach(word::suggest);
        return word.buildFuture();
    }

    private static CommandLogic.Actor actor(CommandSourceStack source) {
        return new CommandLogic.Actor() {
            @Override
            public boolean isAdmin() {
                // The console and command blocks have every permission, like Bukkit's console sender.
                return !(source.getEntity() instanceof ServerPlayer p) || ModRuntime.has(p, ModCore.PERMISSION_ADMIN);
            }

            @Override
            public void send(String message) {
                source.sendSuccess(() -> Component.literal(message), false);
            }

            @Override
            public CommandLogic.Player findOnlinePlayer(String name) {
                ServerPlayer p = source.getServer().getPlayerList().getPlayerByName(name);
                return p == null ? null : new CommandLogic.Player(p.getUUID(), p.getName().getString());
            }

            @Override
            public List<String> onlinePlayerNames() {
                return source.getServer().getPlayerList().getPlayers().stream().map(p -> p.getName().getString()).toList();
            }
        };
    }
}
