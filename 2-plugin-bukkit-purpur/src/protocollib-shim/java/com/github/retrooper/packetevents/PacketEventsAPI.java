package com.github.retrooper.packetevents;

import com.github.retrooper.packetevents.event.EventManager;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public abstract class PacketEventsAPI<T> {
    public EventManager getEventManager() {
        return null;
    }

    public abstract boolean isInitialized();
}
