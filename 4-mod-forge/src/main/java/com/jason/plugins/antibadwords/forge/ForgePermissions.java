package com.jason.plugins.antibadwords.forge;

import com.jason.plugins.antibadwords.mod.ModPermissions;
import com.jason.plugins.antibadwords.mod.ModRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Registers {@code antibadwords.admin}, {@code antibadwords.notify}, {@code antibadwords.bypass} and one
 * {@code antibadwords.profile.<name>} node per worlds.yml profile with Forge's permission API so permission mods
 * (LuckPerms etc.) can grant them, with the plugin's defaults (admin and notify: operators, bypass and profiles: nobody).
 *
 * <p>Forge collects permission nodes once, when the server starts. A profile that is added to worlds.yml later gets
 * its permission node after the next server restart; until then nobody has it.
 */
final class ForgePermissions implements ModPermissions {
    private static final PermissionNode<Boolean> ADMIN = node("admin");
    private static final PermissionNode<Boolean> NOTIFY = node("notify");
    private static final PermissionNode<Boolean> BYPASS = node("bypass");
    /** full node name -> registered profile node; filled when Forge gathers the nodes. */
    private static final Map<String, PermissionNode<Boolean>> PROFILES = new ConcurrentHashMap<>();

    private static PermissionNode<Boolean> node(String name) {
        return new PermissionNode<>("antibadwords", name, PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && ModPermissions.fallback(player, "antibadwords." + name));
    }

    static void register() {
        PermissionGatherEvent.Nodes.BUS.addListener((PermissionGatherEvent.Nodes event) -> {
            List<PermissionNode<?>> nodes = new ArrayList<>(List.of(ADMIN, NOTIFY, BYPASS));
            if (ModRuntime.engine != null) {
                for (String profile : ModRuntime.engine.profileNames()) {
                    PermissionNode<Boolean> node = node("profile." + profile);
                    PROFILES.put("antibadwords.profile." + profile, node);
                    nodes.add(node);
                }
            }
            event.addNodes(nodes.toArray(new PermissionNode<?>[0]));
        });
    }

    @Override
    public boolean has(ServerPlayer player, String node) {
        return switch (node) {
            case "antibadwords.admin" -> PermissionAPI.getPermission(player, ADMIN);
            case "antibadwords.notify" -> PermissionAPI.getPermission(player, NOTIFY);
            case "antibadwords.bypass" -> PermissionAPI.getPermission(player, BYPASS);
            default -> {
                PermissionNode<Boolean> profile = PROFILES.get(node);
                // nodes Forge does not know (not registered yet) use the same defaults as on the other loaders
                yield profile != null ? PermissionAPI.getPermission(player, profile) : ModPermissions.fallback(player, node);
            }
        };
    }
}
