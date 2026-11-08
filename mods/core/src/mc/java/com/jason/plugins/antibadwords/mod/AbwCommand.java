package com.jason.plugins.antibadwords.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /abw} (alias {@code /antibadwords}) for the Fabric and Forge mods. Operators only. */
public final class AbwCommand {
    private AbwCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModCore core) {
        for (String root : new String[] {"abw", "antibadwords"}) {
            dispatcher.register(build(root, core));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String root, ModCore core) {
        return Commands.literal(root)
            .requires(ModActions::isOperator)
            .then(Commands.literal("reload").executes(c -> {
                core.reload();
                return ok(c, "§aAntiBadWords config reloaded (" + core.wordCount() + " words).");
            }))
            .then(Commands.literal("info").executes(c -> ok(c, "§6AntiBadWords: §f" + core.wordCount() + " blocked words, protection level "
                + core.config().protectionLevel + ", punish after " + core.config().punishment.violationsBeforePunish + " violations.")))
            .then(Commands.literal("addword").then(Commands.argument("word", StringArgumentType.greedyString()).executes(c -> {
                boolean added = core.addCustomWord(StringArgumentType.getString(c, "word"));
                return added ? ok(c, "§aAdded custom blocked word.") : fail(c, "Word already exists or is invalid.");
            })))
            .then(Commands.literal("removeword").then(Commands.argument("word", StringArgumentType.greedyString()).executes(c -> {
                boolean removed = core.removeCustomWord(StringArgumentType.getString(c, "word"));
                return removed ? ok(c, "§aRemoved custom blocked word.") : fail(c, "Word not found in the custom list.");
            })))
            .then(Commands.literal("check").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                String hit = core.find(StringArgumentType.getString(c, "text"));
                return hit == null ? ok(c, "§aClean - this text would not be blocked.") : ok(c, "§cWould be blocked (matched '" + hit + "').");
            })))
            .then(Commands.literal("violations").then(Commands.argument("player", StringArgumentType.word()).executes(c ->
                withPlayer(c, core, p -> "§e" + p.getName().getString() + " violations: " + core.violations(p.getUUID())))))
            .then(Commands.literal("resetviolations").then(Commands.argument("player", StringArgumentType.word()).executes(c ->
                withPlayer(c, core, p -> {
                    core.resetViolations(p.getUUID());
                    return "§aViolations reset for " + p.getName().getString() + ".";
                }))));
    }

    private static int withPlayer(CommandContext<CommandSourceStack> c, ModCore core, Function<ServerPlayer, String> action) {
        String name = StringArgumentType.getString(c, "player");
        ServerPlayer target = c.getSource().getServer().getPlayerList().getPlayerByName(name);
        return target == null ? fail(c, "Player not online.") : ok(c, action.apply(target));
    }

    private static int ok(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendFailure(Component.literal(text));
        return 0;
    }
}
