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
 * Registers {@code antibadwords.admin}, {@code antibadwords.notify}, {@code antibadwords.bypass} (and
 * {@code .bypass.spam}, {@code .bypass.links}, {@code .bypass.evasion}, {@code .bypass.mute}, and the operator
 * nodes {@code .mute}, {@code .history}, {@code .discord}, {@code .gui}) and one
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
    private static final PermissionNode<Boolean> BYPASS_SPAM = node("bypass.spam");
    private static final PermissionNode<Boolean> BYPASS_LINKS = node("bypass.links");
    private static final PermissionNode<Boolean> BYPASS_EVASION = node("bypass.evasion");
    private static final PermissionNode<Boolean> BYPASS_MUTE = node("bypass.mute");
    private static final PermissionNode<Boolean> MUTE = node("mute");
    private static final PermissionNode<Boolean> HISTORY = node("history");
    private static final PermissionNode<Boolean> DISCORD = node("discord");
    private static final PermissionNode<Boolean> GUI = node("gui");
    /** full node name -> registered profile node; filled when Forge gathers the nodes. */
    private static final Map<String, PermissionNode<Boolean>> PROFILES = new ConcurrentHashMap<>();

    private static PermissionNode<Boolean> node(String name) {
        return new PermissionNode<>("antibadwords", name, PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && ModPermissions.fallback(player, "antibadwords." + name));
    }

    static void register() {
        PermissionGatherEvent.Nodes.BUS.addListener((PermissionGatherEvent.Nodes event) -> {
            List<PermissionNode<?>> nodes = new ArrayList<>(List.of(ADMIN, NOTIFY, BYPASS, BYPASS_SPAM, BYPASS_LINKS, BYPASS_EVASION,
                BYPASS_MUTE, MUTE, HISTORY, DISCORD, GUI));
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
            case "antibadwords.bypass.spam" -> PermissionAPI.getPermission(player, BYPASS_SPAM);
            case "antibadwords.bypass.links" -> PermissionAPI.getPermission(player, BYPASS_LINKS);
            case "antibadwords.bypass.evasion" -> PermissionAPI.getPermission(player, BYPASS_EVASION);
            case "antibadwords.bypass.mute" -> PermissionAPI.getPermission(player, BYPASS_MUTE);
            case "antibadwords.mute" -> PermissionAPI.getPermission(player, MUTE);
            case "antibadwords.history" -> PermissionAPI.getPermission(player, HISTORY);
            case "antibadwords.discord" -> PermissionAPI.getPermission(player, DISCORD);
            case "antibadwords.gui" -> PermissionAPI.getPermission(player, GUI);
            default -> {
                PermissionNode<Boolean> profile = PROFILES.get(node);
                // nodes Forge does not know (not registered yet) use the same defaults as on the other loaders
                yield profile != null ? PermissionAPI.getPermission(player, profile) : ModPermissions.fallback(player, node);
            }
        };
    }
}
