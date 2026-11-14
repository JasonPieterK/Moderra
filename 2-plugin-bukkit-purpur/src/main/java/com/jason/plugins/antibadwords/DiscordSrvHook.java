package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.discord.DiscordBridge;
import com.jason.plugins.antibadwords.engine.discord.DiscordLink;
import com.jason.plugins.antibadwords.engine.discord.DiscordSettings;
import com.jason.plugins.antibadwords.engine.discord.DiscordSrvSettings;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Connects the engine to DiscordSRV (Bukkit family only, soft dependency). DiscordSRV is touched by REFLECTION only, so the jar
 * loads and works without it; the one exception is {@link DiscordSrvListener}, which is loaded only after DiscordSRV was found.
 * All decisions are made by the engine ({@link DiscordLink}); this class only translates.
 *
 * <p>DiscordSRV 1.x API used (everything else is deliberately NOT used, see the log lines at startup):
 * <ul>
 *   <li>{@code github.scarsz.discordsrv.DiscordSRV#api} (static field) and {@code ApiManager#subscribe / #unsubscribe(Object)}</li>
 *   <li>{@code DiscordSRV#getPlugin()} and {@code #getDestinationTextChannelForGameChannelName(String)} (a JDA {@code TextChannel} or null)</li>
 *   <li>{@code DiscordUtil#sendMessage(TextChannel, String)} and {@code DiscordUtil#privateMessage(User, String)} (static)</li>
 *   <li>{@code DiscordGuildMessagePreProcessEvent}: {@code getAuthor()}, {@code getMember()}, {@code getChannel()},
 *       {@code getMessage()}, {@code setCancelled(boolean)}; JDA: {@code getId()}, {@code getName()}, {@code getRoles()},
 *       {@code getContentRaw()}</li>
 * </ul>
 * Not used: slash commands (annotation based, cannot be done by reflection), rewriting the text of a Discord message (no
 * confident API), so the {@code censor} action becomes {@code block} here.
 * Nothing here ever logs a webhook URL or any other secret.
 */
final class DiscordSrvHook implements DiscordBridge {
    private static final int DISCORD_LIMIT = 1900;
    private static final int USER_CACHE = 200;
    static final String PLUGIN_NAME = "DiscordSRV";

    private final Engine engine;
    private final EngineLog log;
    private final Plugin discordSrv;
    private final ClassLoader loader;
    private final Object api;
    private final Object srv;
    private final Method destination;
    private final Class<?> util;
    private DiscordLink link;
    private DiscordSrvListener listener;
    /** Recent Discord users by id, so a direct message can be sent later (the engine only knows ids). */
    private final Map<String, Object> users = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > USER_CACHE;
        }
    };
    private final Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DiscordSrvHook(JavaPlugin plugin, Engine engine, Plugin discordSrv) throws ReflectiveOperationException {
        this.engine = engine;
        this.log = engine.platform().logger();
        this.discordSrv = discordSrv;
        this.loader = discordSrv.getClass().getClassLoader();
        Class<?> main = Class.forName("github.scarsz.discordsrv.DiscordSRV", true, loader);
        this.api = main.getField("api").get(null);
        this.srv = main.getMethod("getPlugin").invoke(null);
        this.destination = main.getMethod("getDestinationTextChannelForGameChannelName", String.class);
        this.util = Class.forName("github.scarsz.discordsrv.util.DiscordUtil", true, loader);
        if (api == null || srv == null) {
            throw new IllegalStateException("DiscordSRV is not ready");
        }
    }

    /**
     * Looks for DiscordSRV and connects to it. Never throws: returns null when DiscordSRV is not there or does not look like
     * DiscordSRV 1.x (and says why in the log).
     */
    static DiscordSrvHook start(JavaPlugin plugin, Engine engine) {
        EngineLog log = engine.platform().logger();
        String mode = engine.value(DiscordSettings.MODE);
        Plugin found = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (found == null || !found.isEnabled()) {
            if (DiscordSettings.MODE_DISCORDSRV.equals(mode)) {
                log.warn("discord.mode is 'discordsrv' but the DiscordSRV plugin is not installed or not enabled: nothing will be sent to Discord.");
            } else {
                log.info("DiscordSRV not found: Discord alerts use the webhooks (discord.mode: " + mode + ").");
            }
            return null;
        }
        try {
            DiscordSrvHook hook = new DiscordSrvHook(plugin, engine, found);
            hook.connect();
            return hook;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            log.warn("DiscordSRV was found but its API does not look like DiscordSRV 1.x (" + ex.getClass().getSimpleName()
                + "). The DiscordSRV features are switched off; webhooks still work.");
            return null;
        }
    }

    private void connect() {
        link = engine.discord().attachBridge(this);
        boolean inbound = false;
        try {
            listener = new DiscordSrvListener(this);
            api.getClass().getMethod("subscribe", Object.class).invoke(api, listener);
            inbound = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            listener = null;
            log.warn("Could not subscribe to DiscordSRV events (" + ex.getClass().getSimpleName()
                + "): filtering Discord messages and Discord commands are OFF. Alerts still work.");
        }
        log.info("DiscordSRV " + version() + " found. discord.mode: " + engine.value(DiscordSettings.MODE)
            + ". Alerts go to the DiscordSRV channel '" + engine.value(DiscordSrvSettings.ALERT_CHANNEL) + "' when discord.enabled is true.");
        log.info("Discord to Minecraft filter: " + (inbound ? (engine.value(DiscordSrvSettings.INBOUND_ENABLED) ? "ON" : "off (discord.inbound.enabled)")
            : "unavailable") + "; commands typed in Discord: " + (inbound ? "prefix '" + engine.value(DiscordSrvSettings.PREFIX)
            + "' (each command is off until switched on and a role/user is listed)" : "unavailable") + ".");
        log.info("Not supported in this version: DiscordSRV slash commands (use the prefix commands) and cleaning the text of a Discord "
            + "message (action 'censor' blocks instead).");
    }

    /** Disconnects (server stop or plugin disable): the webhooks take over again. */
    void stop() {
        try {
            if (listener != null) {
                api.getClass().getMethod("unsubscribe", Object.class).invoke(api, listener);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            // DiscordSRV may already be shutting down; nothing to repair
        }
        listener = null;
        engine.discord().detachBridge();
    }

    private String version() {
        try {
            return discordSrv.getDescription().getVersion();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    // ------------------------------------------------------------------------------------------
    // DiscordBridge
    // ------------------------------------------------------------------------------------------

    @Override
    public String name() {
        return "DiscordSRV " + version();
    }

    @Override
    public boolean sendToChannel(String channelName, String text) {
        try {
            Object channel = destination.invoke(srv, channelName);
            return channel != null && send(channel, text);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnOnce("send", "Sending to DiscordSRV failed (" + ex.getClass().getSimpleName() + "); is the DiscordSRV bot connected?");
            return false;
        }
    }

    @Override
    public void notifyUser(String discordUserId, String text) {
        Object user;
        synchronized (users) {
            user = users.get(discordUserId);
        }
        if (user == null) {
            return;
        }
        try {
            Method m = staticMethod("privateMessage", user);
            if (m == null) {
                warnOnce("dm", "This DiscordSRV version has no DiscordUtil.privateMessage: senders are not told in Discord.");
                return;
            }
            m.invoke(null, user, cut(text));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnOnce("dm", "Could not send a direct message through DiscordSRV (" + ex.getClass().getSimpleName() + ").");
        }
    }

    /** DiscordSRV hands the text to Minecraft itself and offers no safe way to change it, so a changed text is not possible. */
    @Override
    public boolean canRewrite() {
        return false;
    }

    private boolean send(Object channel, String text) throws ReflectiveOperationException {
        Method m = staticMethod("sendMessage", channel);
        if (m == null) {
            warnOnce("util", "This DiscordSRV version has no DiscordUtil.sendMessage(channel, text): nothing can be sent through it.");
            return false;
        }
        m.invoke(null, channel, cut(text));
        return true;
    }

    /** The static DiscordUtil method {@code name(<something the target fits>, String)}. */
    private Method staticMethod(String name, Object target) {
        for (Method m : util.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals(name) && Modifier.isStatic(m.getModifiers()) && p.length == 2 && p[1] == String.class
                && p[0].isInstance(target)) {
                return m;
            }
        }
        return null;
    }

    private static String cut(String text) {
        return text.length() <= DISCORD_LIMIT ? text : text.substring(0, DISCORD_LIMIT) + "...";
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.warn(message);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Messages from Discord (DiscordGuildMessagePreProcessEvent)
    // ------------------------------------------------------------------------------------------

    /** Called by {@link DiscordSrvListener}; the event type is only known as Object here. Any failure lets the message through. */
    void onMessage(Object event) {
        try {
            DiscordLink current = link;
            if (current == null) {
                return;
            }
            Object author = call(event, "getAuthor");
            Object channel = call(event, "getChannel");
            Object message = call(event, "getMessage");
            if (author == null || message == null) {
                return;
            }
            String userId = String.valueOf(call(author, "getId"));
            Set<String> roles = new HashSet<>();
            Object member = call(event, "getMember");
            if (member != null && call(member, "getRoles") instanceof List<?> list) {
                for (Object role : list) {
                    roles.add(String.valueOf(call(role, "getId")));
                }
            }
            synchronized (users) {
                users.put(userId, author);
            }
            DiscordLink.Incoming incoming = new DiscordLink.Incoming(channel == null ? "" : String.valueOf(call(channel, "getId")),
                channel == null ? "" : String.valueOf(call(channel, "getName")), userId, String.valueOf(call(author, "getName")), roles,
                String.valueOf(call(message, "getContentRaw")));
            DiscordLink.Decision decision = current.onMessage(incoming, reply -> {
                if (channel != null) {
                    try {
                        send(channel, reply);
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        warnOnce("reply", "Could not answer in Discord (" + ex.getClass().getSimpleName() + ").");
                    }
                }
            });
            if (decision.type() != DiscordLink.Decision.Type.ALLOW) {
                // BLOCK; REPLACE cannot happen because canRewrite() is false
                event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnOnce("event", "A DiscordSRV message could not be checked (" + ex.getClass().getSimpleName()
                + "); it was let through. Does this DiscordSRV version differ from 1.x?");
        }
    }

    /** Calls a public no-argument method by name; looks at public interfaces and classes first (JDA's implementations are not public). */
    private static Object call(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        ArrayDeque<Class<?>> todo = new ArrayDeque<>();
        todo.add(target.getClass());
        Set<Class<?>> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            Class<?> c = todo.poll();
            if (c == null || !seen.add(c)) {
                continue;
            }
            if (Modifier.isPublic(c.getModifiers())) {
                try {
                    return c.getMethod(name).invoke(target);
                } catch (NoSuchMethodException ex) {
                    // keep looking in the other types
                }
            }
            todo.add(c.getSuperclass());
            todo.addAll(List.of(c.getInterfaces()));
        }
        Method m = target.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(target);
    }
}
