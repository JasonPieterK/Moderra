package com.jason.plugins.moderra.integrations;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.EngineLog;
import com.jason.plugins.moderra.engine.integrations.BlockedEditLog;
import com.jason.plugins.moderra.engine.integrations.ExtraIntegrationSettings;
import com.jason.plugins.moderra.engine.integrations.OutgoingScan;
import com.jason.plugins.moderra.engine.integrations.OutgoingScanSettings;
import com.jason.plugins.moderra.engine.integrations.PacketScanPlan;
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
 * <p>ProtocolLib: {@link ProtocolLibScan} (shim-compiled), only when the Bukkit events are missing (setting). PacketEvents: detected,
 * {@link PacketEventsScan}. Outgoing scan of other plugins' chat: {@link ProtocolLibOutgoing} / {@link PacketEventsOutgoing}. Plan: {@link PlanHook}.
 */
public final class ExtraHooks {
    private ExtraHooks() {
    }

    public static void start(Engine engine) {
        EngineLog log = engine.platform().logger();
        startPackets(engine, log);
        startOutgoing(engine, log);
        startCoreProtect(engine, log);
        if (engine.platform().hasPlugin("Plan") && engine.value(ExtraIntegrationSettings.PLAN)) {
            try {
                PlanHook.start(engine, log);
            } catch (Throwable ex) {
                log.warn("Plan hook could not start (" + ex.getClass().getSimpleName() + ").");
            }
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
            log.info("Packet scan: off. Reason: " + d.reason() + ".");
        } else if (d.backend() == PacketScanPlan.Backend.PROTOCOLLIB) {
            Plugin self = Bukkit.getPluginManager().getPlugin("Moderra");
            try {
                if (self == null) {
                    throw new IllegalStateException("own plugin not found");
                }
                ProtocolLibScan.start(self, engine);
                log.info("Packet scan: on. Reason: " + d.reason() + ". ProtocolLib scans the sign, book and anvil packets.");
            } catch (Throwable ex) {
                log.warn("Packet scan: error. The ProtocolLib listener did not start (" + ex.getClass().getSimpleName() + ").");
            }
        } else {
            try {
                PacketEventsScan.start(engine);
                log.info("Packet scan: on. Reason: " + d.reason() + ". PacketEvents scans the sign, book and anvil packets.");
            } catch (Throwable ex) {
                log.warn("Packet scan: error. The PacketEvents listener did not start (" + ex.getClass().getSimpleName() + ").");
            }
        }
    }

    /** Outgoing-text scan (integrations.outgoing-scan.*): ProtocolLib is preferred, PacketEvents is the other choice. */
    private static void startOutgoing(Engine engine, EngineLog log) {
        OutgoingScan scan = new OutgoingScan(engine);
        if (!scan.enabled()) {
            return;
        }
        boolean lib = engine.platform().hasPlugin("ProtocolLib");
        boolean pe = engine.platform().hasPlugin("packetevents");
        PacketScanPlan.Decision d = PacketScanPlan.choose(false, false, engine.value(ExtraIntegrationSettings.PROTOCOLLIB), lib,
            engine.value(ExtraIntegrationSettings.PACKETEVENTS), pe);
        try {
            if (d.backend() == PacketScanPlan.Backend.PROTOCOLLIB) {
                Plugin self = Bukkit.getPluginManager().getPlugin("Moderra");
                if (self == null) {
                    throw new IllegalStateException("own plugin not found");
                }
                ProtocolLibOutgoing.start(self, scan);
                log.info("Outgoing scan: on. ProtocolLib is the packet library. Action: " + engine.value(OutgoingScanSettings.ACTION)
                    + ". Sources: " + engine.value(OutgoingScanSettings.SOURCES) + ".");
            } else if (d.backend() == PacketScanPlan.Backend.PACKETEVENTS) {
                PacketEventsOutgoing.start(scan);
                log.info("Outgoing scan: on. PacketEvents is the packet library. Action: " + engine.value(OutgoingScanSettings.ACTION)
                    + ". Sources: " + engine.value(OutgoingScanSettings.SOURCES) + ".");
            } else {
                log.warn("Outgoing scan: error. The scan is on, but " + d.reason() + ". Nothing is scanned.");
            }
        } catch (Throwable ex) {
            log.warn("Outgoing scan: error. The packet listener did not start (" + ex.getClass().getSimpleName() + ").");
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
