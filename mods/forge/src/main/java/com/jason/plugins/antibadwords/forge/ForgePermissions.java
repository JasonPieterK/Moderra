package com.jason.plugins.antibadwords.forge;

import com.jason.plugins.antibadwords.mod.ModPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Registers {@code antibadwords.admin}, {@code antibadwords.notify} and {@code antibadwords.bypass} with Forge's
 * permission API so permission mods (LuckPerms etc.) can grant them, with the plugin's defaults.
 */
final class ForgePermissions implements ModPermissions {
    private static final PermissionNode<Boolean> ADMIN = node("admin");
    private static final PermissionNode<Boolean> NOTIFY = node("notify");
    private static final PermissionNode<Boolean> BYPASS = node("bypass");

    private static PermissionNode<Boolean> node(String name) {
        return new PermissionNode<>("antibadwords", name, PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && ModPermissions.fallback(player, "antibadwords." + name));
    }

    /** Must be registered on the Forge event bus. */
    public static final class Nodes {
        @SubscribeEvent
        public void onGather(PermissionGatherEvent.Nodes event) {
            event.addNodes(ADMIN, NOTIFY, BYPASS);
        }
    }

    @Override
    public boolean has(ServerPlayer player, String node) {
        return switch (node) {
            case "antibadwords.admin" -> PermissionAPI.getPermission(player, ADMIN);
            case "antibadwords.notify" -> PermissionAPI.getPermission(player, NOTIFY);
            default -> PermissionAPI.getPermission(player, BYPASS);
        };
    }
}
