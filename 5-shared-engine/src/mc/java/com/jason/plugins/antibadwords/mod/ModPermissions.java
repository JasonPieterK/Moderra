package com.jason.plugins.antibadwords.mod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission nodes {@code antibadwords.admin} / {@code .notify} / {@code .bypass}, defaults identical to the
 * plugin's plugin.yml (admin and notify: operators, bypass: nobody). Each loader supplies an implementation
 * that asks the permission mod (LuckPerms etc.) and falls back to {@link #fallback}.
 */
public interface ModPermissions {
    boolean has(ServerPlayer player, String node);

    /** Defaults used when no permission mod answers. */
    static boolean fallback(ServerPlayer player, String node) {
        return node.equals(ModCore.PERMISSION_BYPASS) ? false : isOperator(player.createCommandSourceStack());
    }

    /** Operator check (command level 2 = "game master"), using the permission system introduced in 1.21.11. */
    static boolean isOperator(CommandSourceStack source) {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }
}
