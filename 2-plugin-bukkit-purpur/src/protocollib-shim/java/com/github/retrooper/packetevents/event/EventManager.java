package com.github.retrooper.packetevents.event;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class EventManager {
    public PacketListenerCommon registerListener(PacketListenerCommon listener) {
        return listener;
    }

    public void unregisterListener(PacketListenerCommon listener) {
    }
}
