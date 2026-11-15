package com.jason.plugins.moderra.fabric;

import com.jason.plugins.moderra.mod.ModPermissions;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asks the Fabric Permissions API (lucko's fabric-permissions-api, used by LuckPerms and others) when it is
 * installed, so {@code moderra.*} nodes can be granted like on a Bukkit server. Without it the plugin's
 * defaults apply (admin, notify, mute, history, discord, gui: operators; every bypass node: nobody). New nodes need no code
 * here: every node is passed through to the API with the fallback from {@link ModPermissions#fallback}. Accessed by reflection so it is an optional dependency.
 */
final class FabricPermissions implements ModPermissions {
    private static final Method CHECK = find();

    private static Method find() {
        try {
            Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return api.getMethod("check", net.minecraft.world.entity.Entity.class, String.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return null;
        }
    }

    @Override
    public boolean has(ServerPlayer player, String node) {
        boolean fallback = ModPermissions.fallback(player, node);
        if (CHECK == null) {
            return fallback;
        }
        try {
            return (boolean) CHECK.invoke(null, player, node, fallback);
        } catch (ReflectiveOperationException ex) {
            return fallback;
        }
    }

    @Override
    public boolean hasExplicit(ServerPlayer player, String node) {
        if (CHECK == null) {
            return false;
        }
        try {
            return (boolean) CHECK.invoke(null, player, node, false);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
