package com.github.retrooper.packetevents.event;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public abstract class ProtocolPacketEvent {
    public void markForReEncode(boolean encode) {
    }

    public <T> T getPlayer() {
        return null;
    }

    public PacketTypeCommon getPacketType() {
        return null;
    }

    public boolean isCancelled() {
        return false;
    }

    public void setCancelled(boolean cancel) {
    }
}
