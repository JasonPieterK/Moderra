package com.jason.plugins.antibadwords.mod;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Carries out a {@link ModCore.Verdict} using Minecraft classes. Shared by the Fabric and Forge mods
 * (both use Mojang names). NOT compiled by the Maven test module; see mods/README.md.
 *
 * <p>Everything that tends to change between Minecraft releases (permission checks, running commands)
 * is isolated in {@link #isOperator} and {@link #runCommand} so a future fix is a one-line change.
 */
public final class ModActions {
    private ModActions() {
    }

    /** Handle a blocked message: warn, count the strike and punish when the limit is reached. */
    public static void handle(MinecraftServer server, ServerPlayer player, ModCore core, ModCore.Verdict verdict, String source) {
        // Always continue on the server thread, whichever thread the chat event fired on.
        server.execute(() -> {
            ModConfig cfg = core.config();
            ModConfig.Messages msg = cfg.messages;
            String name = player.getName().getString();
            Map<String, String> values = new HashMap<>(ModCore.values(name, verdict, source));
            values.put("punishments", "punishment");

            if (verdict.count() < verdict.limit()) {
                if (msg.broadcastWarnings) {
                    broadcast(server, ModCore.format(msg.warningBroadcastFormat, values));
                }
                player.sendSystemMessage(Component.literal(ModCore.format(msg.warningPlayer, values)));
            } else if (!verdict.punish()) {
                player.sendSystemMessage(Component.literal(ModCore.format(msg.punishmentDisabledPlayer, values)));
            } else {
                for (String command : verdict.commands()) {
                    runCommand(server, ModCore.format(command.replace("{player}", name), values));
                }
                if (!verdict.finalCommand().isEmpty()) {
                    runCommand(server, ModCore.format(verdict.finalCommand().replace("{player}", name), values));
                }
                broadcast(server, ModCore.format(msg.punishmentBroadcastFormat, values));
                player.sendSystemMessage(Component.literal(ModCore.format(msg.punishmentPlayer, values)));
            }

            if (msg.notifyStaff) {
                Component staff = Component.literal(ModCore.format(msg.staffNotifyFormat, values));
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (isOperator(other.createCommandSourceStack())) {
                        other.sendSystemMessage(staff);
                    }
                }
            }
        });
    }

    public static void broadcast(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    /** Runs a command as the server console (no permission checks, no feedback to chat). */
    public static void runCommand(MinecraftServer server, String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), line);
    }

    /** True for operators (permission level 2+). The permission API changed in 1.21.11 - adjust here if needed. */
    public static boolean isOperator(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
