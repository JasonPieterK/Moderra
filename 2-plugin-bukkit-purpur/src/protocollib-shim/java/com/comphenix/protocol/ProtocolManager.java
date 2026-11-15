package com.comphenix.protocol;

import com.comphenix.protocol.events.PacketListener;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public interface ProtocolManager extends PacketStream {
    void addPacketListener(PacketListener listener);

    void removePacketListener(PacketListener listener);
}
