package com.jason.plugins.antibadwords.integrations;

import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.integration.PermissionSource;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Vault hook (Bukkit family, soft dependency). The Vault API is NOT on Maven Central, so everything is REFLECTION: the
 * {@code ServicesManager} is asked for the registered {@code net.milkbowl.vault.permission.Permission} and
 * {@code net.milkbowl.vault.chat.Chat} services. Names used (believed correct, unverified here):
 * {@code ServicesManager#getRegistration(Class)}, {@code RegisteredServiceProvider#getProvider()},
 * {@code Permission#getPrimaryGroup(String world, OfflinePlayer)}, {@code Permission#playerInGroup(String, OfflinePlayer, String)},
 * {@code Chat#getPlayerPrefix(String, OfflinePlayer)}. Only ONLINE players are asked (a {@code Player} is an {@code OfflinePlayer});
 * world {@code null} = global. If a lookup fails once, the part that failed is switched off with one log line.
 * Offline permission checks and meta need LuckPerms and are not offered here.
 */
public final class VaultHook implements PermissionSource {
    public static final String PLUGIN_NAME = "Vault";
    private final EngineLog log;
    private final Object permission;
    private final Object chat;
    private final Method getPrimaryGroup;
    private final Method playerInGroup;
    private final Method getPlayerPrefix;
    private volatile boolean groupsOk = true;
    private volatile boolean prefixOk = true;

    private VaultHook(EngineLog log, Object permission, Object chat) throws ReflectiveOperationException {
        this.log = log;
        this.permission = permission;
        this.chat = chat;
        Class<?> offline = Class.forName("org.bukkit.OfflinePlayer");
        this.getPrimaryGroup = permission == null ? null : permission.getClass().getMethod("getPrimaryGroup", String.class, offline);
        this.playerInGroup = permission == null ? null : permission.getClass().getMethod("playerInGroup", String.class, offline, String.class);
        this.getPlayerPrefix = chat == null ? null : chat.getClass().getMethod("getPlayerPrefix", String.class, offline);
    }

    /** @return the hook, or {@code null} (with one log line) when Vault has no permission or chat provider */
    public static VaultHook start(EngineLog log) {
        try {
            Object permission = provider("net.milkbowl.vault.permission.Permission");
            Object chat = provider("net.milkbowl.vault.chat.Chat");
            if (permission == null && chat == null) {
                log.info("Vault hook: Vault is installed but no permission or chat plugin is registered with it; skipped.");
                return null;
            }
            VaultHook hook = new VaultHook(log, permission, chat);
            log.info("Vault hook: connected (groups: " + (permission != null) + ", chat prefix: " + (chat != null) + ").");
            return hook;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("The Vault hook could not start (" + ex + "); Vault groups and prefixes are off.");
            return null;
        }
    }

    private static Object provider(String className) throws ReflectiveOperationException {
        Class<?> service = Class.forName(className);
        Object services = Bukkit.class.getMethod("getServicesManager").invoke(null);
        Object registration = services.getClass().getMethod("getRegistration", Class.class).invoke(services, service);
        return registration == null ? null : registration.getClass().getMethod("getProvider").invoke(registration);
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public Optional<String> primaryGroup(UUID id) {
        Player player = groupsOk ? Bukkit.getPlayer(id) : null;
        if (player == null || getPrimaryGroup == null) {
            return Optional.empty();
        }
        try {
            Object group = getPrimaryGroup.invoke(permission, null, player);
            return group == null || group.toString().isBlank() ? Optional.empty() : Optional.of(group.toString());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            groupsOk = false;
            log.warn("Vault group lookup failed (" + ex + "); Vault groups are off.");
            return Optional.empty();
        }
    }

    @Override
    public boolean inGroup(UUID id, String group) {
        Player player = groupsOk ? Bukkit.getPlayer(id) : null;
        if (player == null || playerInGroup == null || group == null || group.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(playerInGroup.invoke(permission, null, player, group));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            groupsOk = false;
            log.warn("Vault group lookup failed (" + ex + "); Vault groups are off.");
            return false;
        }
    }

    @Override
    public Optional<String> meta(UUID player, String key) {
        return Optional.empty(); // Vault has no meta values (only LuckPerms does)
    }

    @Override
    public Optional<String> prefix(UUID id) {
        Player player = prefixOk ? Bukkit.getPlayer(id) : null;
        if (player == null || getPlayerPrefix == null) {
            return Optional.empty();
        }
        try {
            Object prefix = getPlayerPrefix.invoke(chat, null, player);
            return prefix == null || prefix.toString().isBlank() ? Optional.empty() : Optional.of(prefix.toString());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            prefixOk = false;
            log.warn("Vault prefix lookup failed (" + ex + "); Vault prefixes are off.");
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
        return CompletableFuture.completedFuture(false); // not offered through Vault (needs LuckPerms)
    }
}
