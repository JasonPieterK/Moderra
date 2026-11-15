package com.comphenix.protocol.events;

import com.comphenix.protocol.PacketType;
import org.bukkit.entity.Player;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class PacketEvent extends java.util.EventObject {
    public PacketEvent(Object source) {
        super(source);
    }

    public PacketContainer getPacket() {
        return null;
    }

    public void setPacket(PacketContainer packet) {
    }

    public PacketType getPacketType() {
        return null;
    }

    public boolean isCancelled() {
        return false;
    }

    public void setCancelled(boolean cancel) {
    }

    public Player getPlayer() {
        return null;
    }
}
