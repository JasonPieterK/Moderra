package com.jason.plugins.moderra;

import com.jason.plugins.moderra.engine.PermissionNodes;
import org.bukkit.permissions.Permissible;

/**
 * Every Bukkit permission check of the plugin goes through here, so the legacy node fallback (see {@link PermissionNodes}) applies
 * everywhere. An old node counts only when it is explicitly set: Bukkit answers {@code true} for an unknown node when the player is an
 * operator, which must not make operators bypass the filter.
 */
public final class BukkitPermissions {
    private BukkitPermissions() {
    }

    public static boolean has(Permissible who, String node) {
        return PermissionNodes.has(who::hasPermission, legacy -> who.isPermissionSet(legacy) && who.hasPermission(legacy), node);
    }
}
