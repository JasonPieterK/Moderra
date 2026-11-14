package com.jason.plugins.antibadwords.integrations.essentials;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.integrations.EssentialsBridge;
import com.jason.plugins.antibadwords.engine.integrations.IntegrationSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Connects the engine to EssentialsX (Bukkit family only, soft dependency). EssentialsX is touched by REFLECTION only, so
 * the jar loads and works without it and never needs its API at compile time. All decisions are made by the engine
 * ({@code EssentialsLink}); this class only translates. Any failure means "feature off" plus one log line.
 *
 * <p>EssentialsX API used (names I am confident about; nothing else is called):
 * <ul>
 *   <li>{@code Essentials#getUser(Player)} and {@code #getUser(UUID)} (found by name and parameter type) returning {@code User}</li>
 *   <li>{@code User#isMuted()}, {@code #setMuted(boolean)}, {@code #getMuteTimeout()}, {@code #setMuteTimeout(long)}
 *       (timeout = end in ms since 1970, 0 = no end): REQUIRED for the mute sync, otherwise it is switched off</li>
 *   <li>{@code User#getMuteReason()}, {@code #setMuteReason(String)}: optional (older versions have no reason)</li>
 *   <li>{@code User#getNickname()}, {@code #setNickname(String)}, {@code #setDisplayNick()}: optional; without getNickname the
 *       nickname features are off</li>
 *   <li>{@code User#isVanished()}: optional; the Bukkit metadata key {@code "vanished"} is always checked as well</li>
 * </ul>
 */
public final class EssentialsHook implements EssentialsBridge {
    public static final String PLUGIN_NAME = "Essentials";
    public static final String CHAT_PLUGIN_NAME = "EssentialsChat";

    private final EngineLog log;
    private final Plugin essentials;
    private final Method getUserByPlayer;
    private final Method getUserByUuid;
    private final Class<?> userClass;
    private final Method isMuted;
    private final Method setMuted;
    private final Method getMuteTimeout;
    private final Method setMuteTimeout;
    private final Method getMuteReason;
    private final Method setMuteReason;
    private final Method getNickname;
    private final Method setNickname;
    private final Method setDisplayNick;
    private final Method isVanished;

    private EssentialsHook(Engine engine, Plugin essentials) throws ReflectiveOperationException {
        this.log = engine.platform().logger();
        this.essentials = essentials;
        Method byPlayer = null;
        Method byUuid = null;
        for (Method m : essentials.getClass().getMethods()) {
            if (m.getName().equals("getUser") && m.getParameterCount() == 1) {
                Class<?> p = m.getParameterTypes()[0];
                if (p == UUID.class) {
                    byUuid = m;
                } else if (p.isAssignableFrom(Player.class) && p != Object.class) {
                    byPlayer = m;
                }
            }
        }
        if (byPlayer == null && byUuid == null) {
            throw new NoSuchMethodException("getUser");
        }
        this.getUserByPlayer = byPlayer;
        this.getUserByUuid = byUuid;
        this.userClass = (byUuid != null ? byUuid : byPlayer).getReturnType();
        this.isMuted = userClass.getMethod("isMuted");
        this.setMuted = userClass.getMethod("setMuted", boolean.class);
        this.getMuteTimeout = userClass.getMethod("getMuteTimeout");
        this.setMuteTimeout = userClass.getMethod("setMuteTimeout", long.class);
        this.getMuteReason = optional("getMuteReason");
        this.setMuteReason = optional("setMuteReason", String.class);
        this.getNickname = optional("getNickname");
        this.setNickname = optional("setNickname", String.class);
        this.setDisplayNick = optional("setDisplayNick");
        this.isVanished = optional("isVanished");
    }

    private Method optional(String name, Class<?>... parameters) {
        try {
            return userClass.getMethod(name, parameters);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * Looks for EssentialsX and connects the engine to it. Never throws: returns null when it is not there, switched off in
     * {@code integrations.essentials.enabled}, or does not have the API this hook needs (and says why in the log).
     */
    public static EssentialsHook start(Plugin plugin, Engine engine) {
        EngineLog log = engine.platform().logger();
        if (!engine.value(IntegrationSettings.ESSENTIALS)) {
            log.info("EssentialsX hook: switched off (integrations.essentials.enabled).");
            return null;
        }
        PluginManager manager = Bukkit.getPluginManager();
        Plugin found = manager.getPlugin(PLUGIN_NAME);
        if (found == null || !found.isEnabled()) {
            log.info("EssentialsX not found: the EssentialsX hook is off.");
            return null;
        }
        try {
            EssentialsHook hook = new EssentialsHook(engine, found);
            engine.essentials().attach(hook);
            log.info("EssentialsX " + found.getDescription().getVersion() + " found. mute-mode: " + engine.value(IntegrationSettings.MUTE_MODE)
                + ", nicknames in messages: " + engine.value(IntegrationSettings.NICKNAME_IN_MESSAGES)
                + ", hide vanished: " + engine.value(IntegrationSettings.HIDE_VANISHED)
                + ", nickname check: " + (hook.getNickname != null ? engine.value(IntegrationSettings.CHECK_NICKNAME) : "unavailable")
                + ", mute reasons: " + (hook.setMuteReason != null ? "yes" : "not supported by this EssentialsX") + ".");
            return hook;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            log.warn("EssentialsX was found but its API does not look like EssentialsX 2.x (" + ex.getClass().getSimpleName() + ": "
                + ex.getMessage() + "). The EssentialsX hook is off; mutes stay in the built-in mute.");
            return null;
        }
    }

    /** The priority for the chat check: LOWEST with EssentialsX Chat (it formats from LOWEST on), else the old HIGHEST. */
    public static EventPriority chatPriority(Engine engine) {
        String setting = engine.value(IntegrationSettings.CHAT_PRIORITY);
        if (IntegrationSettings.PRIORITY_LOWEST.equals(setting)) {
            return EventPriority.LOWEST;
        }
        if (IntegrationSettings.PRIORITY_HIGHEST.equals(setting)) {
            return EventPriority.HIGHEST;
        }
        boolean chat = engine.value(IntegrationSettings.ESSENTIALS) && Bukkit.getPluginManager().getPlugin(CHAT_PLUGIN_NAME) != null;
        return chat ? EventPriority.LOWEST : EventPriority.HIGHEST;
    }

    public void stop(Engine engine) {
        engine.essentials().detach();
    }

    // ------------------------------------------------------------------------------------------
    // EssentialsBridge
    // ------------------------------------------------------------------------------------------

    private Object user(UUID id) throws ReflectiveOperationException {
        Player online = Bukkit.getPlayer(id);
        if (online != null && getUserByPlayer != null) {
            return getUserByPlayer.invoke(essentials, online);
        }
        return getUserByUuid == null ? null : getUserByUuid.invoke(essentials, id);
    }

    private static RuntimeException wrap(ReflectiveOperationException ex) {
        Throwable cause = ex instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : ex;
        return new IllegalStateException(cause.toString(), cause);
    }

    @Override
    public Optional<ExternalMute> mute(UUID player) {
        try {
            Object user = user(player);
            if (user == null || !(Boolean) isMuted.invoke(user)) {
                return Optional.empty();
            }
            long timeout = ((Number) getMuteTimeout.invoke(user)).longValue();
            String reason = getMuteReason == null ? "" : String.valueOf(getMuteReason.invoke(user) == null ? "" : getMuteReason.invoke(user));
            return Optional.of(new ExternalMute(timeout <= 0 ? -1 : timeout, reason));
        } catch (ReflectiveOperationException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public boolean setMute(UUID player, String name, long until, String reason) {
        try {
            Object user = user(player);
            if (user == null) {
                return false;
            }
            setMuted.invoke(user, true);
            setMuteTimeout.invoke(user, until < 0 ? 0L : until);
            if (setMuteReason != null) {
                setMuteReason.invoke(user, reason == null || reason.isBlank() ? null : reason);
            }
            return true;
        } catch (ReflectiveOperationException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public boolean clearMute(UUID player) {
        try {
            Object user = user(player);
            if (user == null || !(Boolean) isMuted.invoke(user)) {
                return false;
            }
            setMuted.invoke(user, false);
            setMuteTimeout.invoke(user, 0L);
            if (setMuteReason != null) {
                try {
                    setMuteReason.invoke(user, (Object) null);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // the reason is cosmetic
                }
            }
            return true;
        } catch (ReflectiveOperationException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public String nickname(UUID player) {
        if (getNickname == null) {
            return "";
        }
        try {
            Object user = user(player);
            Object nick = user == null ? null : getNickname.invoke(user);
            return nick == null ? "" : String.valueOf(nick);
        } catch (ReflectiveOperationException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public boolean resetNickname(UUID player) {
        if (setNickname == null) {
            return false;
        }
        try {
            Object user = user(player);
            if (user == null) {
                return false;
            }
            setNickname.invoke(user, (Object) null);
            if (setDisplayNick != null) {
                setDisplayNick.invoke(user);
            }
            return true;
        } catch (ReflectiveOperationException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public boolean vanished(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return false;
        }
        for (MetadataValue value : online.getMetadata("vanished")) { // set by Essentials and by most vanish plugins
            if (value.asBoolean()) {
                return true;
            }
        }
        if (isVanished == null) {
            return false;
        }
        try {
            Object user = user(player);
            return user != null && (Boolean) isVanished.invoke(user);
        } catch (ReflectiveOperationException ex) {
            throw wrap(ex);
        }
    }
}
