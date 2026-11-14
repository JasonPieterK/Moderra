package com.jason.plugins.antibadwords.integrations;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.engine.integrations.BlockedEditLog;
import com.jason.plugins.antibadwords.engine.integrations.ExtraIntegrationSettings;
import com.jason.plugins.antibadwords.engine.integrations.PacketScanPlan;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Optional hooks for ProtocolLib / PacketEvents, CoreProtect and Plan (Bukkit family). Nothing here is required; everything
 * is touched by reflection and any problem is one log line.
 *
 * <p>CoreProtect (reflection, API names probed at start, none is assumed): plugin method {@code getAPI()}, API methods
 * {@code isEnabled()} (optional), {@code logChat(Player, String)} and {@code logCommand(Player, String)}. If neither log method
 * exists the hook switches itself off.
 *
 * <p>ProtocolLib / PacketEvents: only detected and reported. The packet listener itself is NOT implemented, because the
 * ProtocolLib and PacketEvents APIs are not available to this build and their packet field layouts differ per Minecraft version
 * (sign text, book pages, anvil name); guessing them would be unsafe. The Bukkit events are used everywhere they exist.
 * Plan: not implemented (its extension API needs compile-time annotations); see the progress note.
 */
public final class ExtraHooks {
    private ExtraHooks() {
    }

    public static void start(Engine engine) {
        EngineLog log = engine.platform().logger();
        startPackets(engine, log);
        startCoreProtect(engine, log);
        if (engine.platform().hasPlugin("Plan") && engine.value(ExtraIntegrationSettings.PLAN)) {
            log.info("Plan found: no data extension is exposed by this version (violation counts are available as PlaceholderAPI values).");
        }
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name, false, ExtraHooks.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    private static void startPackets(Engine engine, EngineLog log) {
        boolean events = hasClass("org.bukkit.event.inventory.PrepareAnvilEvent") && hasClass("org.bukkit.event.block.SignChangeEvent")
            && hasClass("org.bukkit.event.player.PlayerEditBookEvent");
        boolean lib = engine.platform().hasPlugin("ProtocolLib");
        boolean pe = engine.platform().hasPlugin("packetevents");
        if (!lib && !pe) {
            return;
        }
        PacketScanPlan.Decision d = PacketScanPlan.choose(events, engine.value(ExtraIntegrationSettings.PACKET_ONLY_WHEN_MISSING),
            engine.value(ExtraIntegrationSettings.PROTOCOLLIB), lib, engine.value(ExtraIntegrationSettings.PACKETEVENTS), pe);
        if (d.backend() == PacketScanPlan.Backend.NONE) {
            log.info("Packet scan: not used (" + d.reason() + ").");
        } else {
            log.warn("Packet scan: " + d.reason() + ", but the packet-level scan is not implemented in this version; anvil, sign and book "
                + "edits are only checked where the Bukkit events exist.");
        }
    }

    private static void startCoreProtect(Engine engine, EngineLog log) {
        if (!engine.value(ExtraIntegrationSettings.COREPROTECT)) {
            return;
        }
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled()) {
            return;
        }
        try {
            Object api = cp.getClass().getMethod("getAPI").invoke(cp);
            if (api == null) {
                log.warn("CoreProtect hook: getAPI() returned nothing; blocked edits are not logged there.");
                return;
            }
            try {
                Method enabled = api.getClass().getMethod("isEnabled");
                if (!Boolean.TRUE.equals(enabled.invoke(api))) {
                    log.warn("CoreProtect hook: its API says it is not enabled; blocked edits are not logged there.");
                    return;
                }
            } catch (NoSuchMethodException ex) {
                // optional check
            }
            Method chat = find(api, "logChat");
            Method command = find(api, "logCommand");
            boolean preferChat = ExtraIntegrationSettings.LOG_AS_CHAT.equals(engine.value(ExtraIntegrationSettings.COREPROTECT_LOG_AS));
            Method use = preferChat ? (chat != null ? chat : command) : (command != null ? command : chat);
            if (use == null) {
                log.warn("CoreProtect hook: its API has neither logChat nor logCommand; blocked edits are not logged there.");
                return;
            }
            BlockedEditLog.install(engine, (id, name, line) -> {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    return false;
                }
                try {
                    use.invoke(api, player, line);
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    log.warn("CoreProtect hook: logging failed (" + ex.getClass().getSimpleName() + ").");
                    return false;
                }
            });
            log.info("CoreProtect hook: blocked sign/book edits are logged with " + use.getName() + ".");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("CoreProtect hook could not start (" + ex.getClass().getSimpleName() + "); blocked edits are not logged there.");
        }
    }

    private static Method find(Object api, String name) {
        try {
            return api.getClass().getMethod(name, Player.class, String.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
