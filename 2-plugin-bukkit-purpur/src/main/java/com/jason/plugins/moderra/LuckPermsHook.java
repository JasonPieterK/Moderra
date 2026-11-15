package com.jason.plugins.moderra;

import com.jason.plugins.moderra.engine.EngineLog;
import com.jason.plugins.moderra.engine.integrations.PermissionSource;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.model.user.User;

/**
 * LuckPerms hook (Bukkit family, soft dependency). Uses the LuckPerms API ({@code net.luckperms:api}, compile-only, NOT in the
 * jar) directly, so this class must only be loaded when the LuckPerms plugin is installed: {@code ModerraPlugin} checks
 * for the plugin first and starts this inside a try/catch for {@link Throwable}.
 *
 * <p>Thread rules: for ONLINE players {@code UserManager#getUser} and the cached data are in memory and thread safe, so
 * {@link #primaryGroup}, {@link #inGroup} and {@link #meta} are safe on the chat thread. Offline players are loaded with
 * {@code UserManager#loadUser} (a future; it may read the database), never joined on the main thread.
 */
final class LuckPermsHook implements PermissionSource {
    static final String PLUGIN_NAME = "LuckPerms";
    private final LuckPerms api;

    private LuckPermsHook(LuckPerms api) {
        this.api = api;
    }

    /** @throws IllegalStateException when the LuckPerms API is not loaded yet */
    static LuckPermsHook start(EngineLog log) {
        LuckPermsHook hook = new LuckPermsHook(LuckPermsProvider.get());
        log.info("LuckPerms hook: connected (profiles by group or meta, permission checks for offline players).");
        return hook;
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    private User online(UUID player) {
        return api.getUserManager().getUser(player); // null unless loaded (= online)
    }

    @Override
    public Optional<String> primaryGroup(UUID player) {
        User user = online(player);
        return user == null ? Optional.empty() : Optional.ofNullable(user.getPrimaryGroup());
    }

    @Override
    public Optional<String> prefix(UUID player) {
        User user = online(player);
        return user == null ? Optional.empty() : Optional.ofNullable(user.getCachedData().getMetaData().getPrefix());
    }

    @Override
    public boolean inGroup(UUID player, String group) {
        User user = online(player);
        if (user == null || group == null || group.isBlank()) {
            return false;
        }
        // "group.<name>" is how LuckPerms stores membership; the permission check follows inheritance and the player's contexts
        return user.getCachedData().getPermissionData().checkPermission("group." + group.toLowerCase(java.util.Locale.ROOT)).asBoolean();
    }

    @Override
    public Optional<String> meta(UUID player, String key) {
        User user = online(player);
        if (user == null) {
            return Optional.empty();
        }
        CachedDataManager data = user.getCachedData();
        return Optional.ofNullable(data.getMetaData().getMetaValue(key)); // uses the contexts of the player
    }

    @Override
    public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
        try {
            User loaded = online(player);
            if (loaded != null) {
                return CompletableFuture.completedFuture(check(loaded, node));
            }
            return api.getUserManager().loadUser(player).thenApply(user -> user != null && check(user, node)).exceptionally(ex -> false);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private static boolean check(User user, String node) {
        return user.getCachedData().getPermissionData().checkPermission(node).asBoolean();
    }
}
