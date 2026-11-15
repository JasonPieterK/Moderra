package com.jason.plugins.moderra.forge;

import com.jason.plugins.moderra.engine.PermissionNodes;
import com.jason.plugins.moderra.mod.ModPermissions;
import com.jason.plugins.moderra.mod.ModRuntime;
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
 * Registers {@code moderra.admin}, {@code moderra.notify}, {@code moderra.bypass} (and
 * {@code .bypass.spam}, {@code .bypass.links}, {@code .bypass.evasion}, {@code .bypass.mute}, and the operator
 * nodes {@code .mute}, {@code .history}, {@code .discord}, {@code .gui}) and one
 * {@code moderra.profile.<name>} node per worlds.yml profile with Forge's permission API so permission mods
 * (LuckPerms etc.) can grant them, with the plugin's defaults (admin and notify: operators, bypass and profiles: nobody).
 *
 * <p>Forge collects permission nodes once, when the server starts. A profile that is added to worlds.yml later gets
 * its permission node after the next server restart; until then nobody has it.
 */
final class ForgePermissions implements ModPermissions {
    private static final PermissionNode<Boolean> ADMIN = node("admin");
    private static final PermissionNode<Boolean> NOTIFY = node("notify");
    private static final PermissionNode<Boolean> GUI = node("gui");
    private static final PermissionNode<Boolean> BYPASS = node("bypass");
    private static final PermissionNode<Boolean> BYPASS_SPAM = node("bypass.spam");
    private static final PermissionNode<Boolean> BYPASS_LINKS = node("bypass.links");
    private static final PermissionNode<Boolean> BYPASS_EVASION = node("bypass.evasion");
    private static final PermissionNode<Boolean> BYPASS_MUTE = node("bypass.mute");
    private static final PermissionNode<Boolean> MUTE = node("mute");
    private static final PermissionNode<Boolean> HISTORY = node("history");
    private static final PermissionNode<Boolean> DISCORD = node("discord");
    /** full node name -> registered profile node; filled when Forge gathers the nodes. */
    private static final Map<String, PermissionNode<Boolean>> PROFILES = new ConcurrentHashMap<>();

    /** legacy full node name -> registered legacy node (always false unless a permission mod grants it); see PermissionNodes. */
    private static final Map<String, PermissionNode<Boolean>> LEGACY = new ConcurrentHashMap<>();

    private static PermissionNode<Boolean> legacy(String name) {
        PermissionNode<Boolean> old = new PermissionNode<>(PermissionNodes.LEGACY_NAMESPACE, name, PermissionTypes.BOOLEAN,
            (player, uuid, context) -> false);
        LEGACY.put(PermissionNodes.legacyOf(PermissionNodes.NAMESPACE + "." + name), old);
        return old;
    }

    private static PermissionNode<Boolean> node(String name) {
        return new PermissionNode<>("moderra", name, PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && ModPermissions.fallback(player, "moderra." + name));
    }

    static void register() {
        PermissionGatherEvent.Nodes.BUS.addListener((PermissionGatherEvent.Nodes event) -> {
            List<PermissionNode<?>> nodes = new ArrayList<>(List.of(ADMIN, NOTIFY, BYPASS, BYPASS_SPAM, BYPASS_LINKS, BYPASS_EVASION,
                BYPASS_MUTE, MUTE, HISTORY, DISCORD, GUI));
            if (ModRuntime.engine != null) {
                for (String profile : ModRuntime.engine.profileNames()) {
                    PermissionNode<Boolean> node = node("profile." + profile);
                    PROFILES.put("moderra.profile." + profile, node);
                    nodes.add(node);
                }
            }
            List<String> names = new ArrayList<>(List.of("admin", "notify", "gui", "bypass", "bypass.spam", "bypass.links", "bypass.evasion",
                "bypass.mute", "mute", "history", "discord"));
            if (ModRuntime.engine != null) {
                for (String profile : ModRuntime.engine.profileNames()) {
                    names.add("profile." + profile);
                }
            }
            for (String name : names) {
                nodes.add(legacy(name));
            }
            event.addNodes(nodes.toArray(new PermissionNode<?>[0]));
        });
    }

    @Override
    public boolean has(ServerPlayer player, String node) {
        return switch (node) {
            case "moderra.admin" -> PermissionAPI.getPermission(player, ADMIN);
            case "moderra.notify" -> PermissionAPI.getPermission(player, NOTIFY);
            case "moderra.gui" -> PermissionAPI.getPermission(player, GUI);
            case "moderra.bypass" -> PermissionAPI.getPermission(player, BYPASS);
            case "moderra.bypass.spam" -> PermissionAPI.getPermission(player, BYPASS_SPAM);
            case "moderra.bypass.links" -> PermissionAPI.getPermission(player, BYPASS_LINKS);
            case "moderra.bypass.evasion" -> PermissionAPI.getPermission(player, BYPASS_EVASION);
            case "moderra.bypass.mute" -> PermissionAPI.getPermission(player, BYPASS_MUTE);
            case "moderra.mute" -> PermissionAPI.getPermission(player, MUTE);
            case "moderra.history" -> PermissionAPI.getPermission(player, HISTORY);
            case "moderra.discord" -> PermissionAPI.getPermission(player, DISCORD);
            default -> {
                PermissionNode<Boolean> profile = PROFILES.get(node);
                // nodes Forge does not know (not registered yet) use the same defaults as on the other loaders
                yield profile != null ? PermissionAPI.getPermission(player, profile) : ModPermissions.fallback(player, node);
            }
        };
    }

    @Override
    public boolean hasExplicit(ServerPlayer player, String node) {
        PermissionNode<Boolean> old = LEGACY.get(node);
        return old != null && PermissionAPI.getPermission(player, old);
    }
}
