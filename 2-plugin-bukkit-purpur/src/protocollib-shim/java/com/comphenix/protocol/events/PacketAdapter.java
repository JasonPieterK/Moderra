package com.comphenix.protocol.events;

import com.comphenix.protocol.PacketType;
import org.bukkit.plugin.Plugin;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public abstract class PacketAdapter implements PacketListener {
    public PacketAdapter(Plugin plugin, PacketType... types) {
    }

    public void onPacketReceiving(PacketEvent event) {
    }

    public void onPacketSending(PacketEvent event) {
    }
}
