package com.jason.plugins.antibadwords.engine.integrations;

/** Decides which packet library (if any) scans anvil, sign and book text. Pure logic; the plugin supplies the facts. */
public final class PacketScanPlan {
    public enum Backend { NONE, PROTOCOLLIB, PACKETEVENTS }

    public record Decision(Backend backend, String reason) {
    }

    private PacketScanPlan() {
    }

    public static Decision choose(boolean bukkitEventsPresent, boolean onlyWhenMissing, boolean protocolLibAllowed, boolean protocolLibInstalled,
                                  boolean packetEventsAllowed, boolean packetEventsInstalled) {
        if (bukkitEventsPresent && onlyWhenMissing) {
            return new Decision(Backend.NONE, "the Bukkit events for anvil, sign and book exist, so no packet scan is needed");
        }
        if (protocolLibAllowed && protocolLibInstalled) {
            return new Decision(Backend.PROTOCOLLIB, "ProtocolLib found");
        }
        if (packetEventsAllowed && packetEventsInstalled) {
            return new Decision(Backend.PACKETEVENTS, "PacketEvents found");
        }
        if (!protocolLibInstalled && !packetEventsInstalled) {
            return new Decision(Backend.NONE, "neither ProtocolLib nor PacketEvents is installed");
        }
        return new Decision(Backend.NONE, "the installed packet library is switched off in integrations.yml");
    }
}
