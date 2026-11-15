package com.jason.plugins.antibadwords.mod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission nodes {@code antibadwords.admin} / {@code .notify} / {@code .mute} / {@code .history} / {@code .discord} / {@code .gui} / {@code .bypass(.*)} / {@code .profile.<name>} / {@code .gui.edit.<category>}, defaults
 * identical to the plugin (admin, notify and other nodes: operators; bypass and profile nodes: nobody; gui.edit nodes: true). Each loader supplies an implementation
 * that asks the permission mod (LuckPerms etc.) and falls back to {@link #fallback}.
 */
public interface ModPermissions {
    boolean has(ServerPlayer player, String node);

    /** Defaults used when no permission mod answers. */
    static boolean fallback(ServerPlayer player, String node) {
        if (node.startsWith("antibadwords.bypass") || node.startsWith("antibadwords.profile.")) {
            return false; // nobody has these unless a permission mod grants them
        }
        if (node.startsWith(com.jason.plugins.antibadwords.engine.gui.Menu.EDIT_PREFIX)) {
            return true; // menu edit rights: everybody who may open the menu, until a permission mod takes a node away
        }
        return isOperator(player.createCommandSourceStack());
    }

    /** Operator check (command level 2 = "game master"), using the permission system introduced in 1.21.11. */
    static boolean isOperator(CommandSourceStack source) {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }
}
