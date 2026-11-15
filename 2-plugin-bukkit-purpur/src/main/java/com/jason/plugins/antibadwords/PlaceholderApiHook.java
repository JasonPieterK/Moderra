package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.integrations.PapiLuckPermsSettings;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlaceholderAPI hook (Bukkit family, soft dependency). Two parts, both switched in {@code integrations.yml}:
 * <ul>
 *   <li>the expansion {@code antibadwords} ({@link AbwPlaceholderExpansion}, loaded only when PlaceholderAPI is there),</li>
 *   <li>optionally replacing other plugins' placeholders in the messages we send to one player. That call is made by
 *       REFLECTION ({@code PlaceholderAPI#setPlaceholders(OfflinePlayer, String)}, a public static method).</li>
 * </ul>
 * Any failure is one log line and the feature stays off; the plugin never fails because of PlaceholderAPI.
 */
final class PlaceholderApiHook {
    static final String PLUGIN_NAME = "PlaceholderAPI";
    private final Engine engine;
    private final EngineLog log;
    private Object expansion;
    private Method unregister;
    private Method setPlaceholders;
    private boolean warnedParse;

    private PlaceholderApiHook(Engine engine, EngineLog log) {
        this.engine = engine;
        this.log = log;
    }

    /** @return the hook, or {@code null} when it is switched off or PlaceholderAPI is not installed */
    static PlaceholderApiHook start(JavaPlugin plugin, Engine engine, EngineLog log) {
        if (!engine.value(PapiLuckPermsSettings.PAPI_ENABLED)) {
            log.info("PlaceholderAPI hook: switched off (integrations.placeholderapi.enabled).");
            return null;
        }
        Plugin papi = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (papi == null || !papi.isEnabled()) {
            return null; // not installed: nothing to report
        }
        PlaceholderApiHook hook = new PlaceholderApiHook(engine, log);
        hook.register(plugin);
        hook.findParser(papi);
        return hook;
    }

    private void register(JavaPlugin plugin) {
        try {
            AbwPlaceholderExpansion e = new AbwPlaceholderExpansion(engine, plugin.getDescription().getVersion(),
                plugin.getDescription().getAuthors());
            Method register = e.getClass().getMethod("register");
            Object ok = register.invoke(e);
            if (Boolean.FALSE.equals(ok)) {
                log.warn("PlaceholderAPI hook: PlaceholderAPI refused the expansion 'antibadwords' (already registered?).");
                return;
            }
            expansion = e;
            unregister = e.getClass().getMethod("unregister");
            log.info("PlaceholderAPI hook: registered %antibadwords_<" + String.join("|", com.jason.plugins.antibadwords.engine.integrations
                .PlaceholderValues.NAMES) + ">%.");
        } catch (Throwable ex) {
            log.warn("PlaceholderAPI hook: the expansion could not be registered (" + ex.getClass().getSimpleName() + "); placeholders are off.");
        }
    }

    private void findParser(Plugin papi) {
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
            setPlaceholders = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            log.warn("PlaceholderAPI hook: PlaceholderAPI#setPlaceholders(OfflinePlayer, String) was not found; "
                + "integrations.placeholderapi.parse-in-messages cannot work.");
        }
    }

    /** Replaces placeholders in a message for one player when the admin switched that on; otherwise returns the text unchanged. */
    String parse(Player player, String message) {
        if (setPlaceholders == null || !engine.value(PapiLuckPermsSettings.PAPI_PARSE) || message == null || message.indexOf('%') < 0) {
            return message;
        }
        try {
            Object result = setPlaceholders.invoke(null, player, message);
            return result instanceof String s ? s : message;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warnedParse) {
                warnedParse = true;
                log.warn("PlaceholderAPI hook: replacing placeholders failed once (" + ex.getClass().getSimpleName() + "); the text is sent as it is.");
            }
            return message;
        }
    }

    void stop() {
        try {
            if (expansion != null && unregister != null) {
                unregister.invoke(expansion);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            // server is stopping; nothing useful to do
        }
        expansion = null;
        setPlaceholders = null;
    }

    static List<String> placeholderNames() {
        return com.jason.plugins.antibadwords.engine.integrations.PlaceholderValues.NAMES;
    }
}
