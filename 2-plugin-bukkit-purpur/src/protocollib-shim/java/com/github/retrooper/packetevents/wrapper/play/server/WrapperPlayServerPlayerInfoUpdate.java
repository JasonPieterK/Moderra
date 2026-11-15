package com.github.retrooper.packetevents.wrapper.play.server;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import java.util.List;
import net.kyori.adventure.text.Component;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class WrapperPlayServerPlayerInfoUpdate extends PacketWrapper<WrapperPlayServerPlayerInfoUpdate> {
    public WrapperPlayServerPlayerInfoUpdate(PacketSendEvent event) {
    }

    public List<PlayerInfo> getEntries() {
        return null;
    }

    public void setEntries(List<PlayerInfo> entries) {
    }

    public static class PlayerInfo {
        public Component getDisplayName() {
            return null;
        }

        public void setDisplayName(Component name) {
        }
    }
}
