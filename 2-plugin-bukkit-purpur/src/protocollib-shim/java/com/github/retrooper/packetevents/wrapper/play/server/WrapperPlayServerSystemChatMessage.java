package com.github.retrooper.packetevents.wrapper.play.server;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import net.kyori.adventure.text.Component;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class WrapperPlayServerSystemChatMessage extends PacketWrapper<WrapperPlayServerSystemChatMessage> {
    public WrapperPlayServerSystemChatMessage(PacketSendEvent event) {
    }

    public WrapperPlayServerSystemChatMessage(boolean overlay, Component message) {
    }

    public WrapperPlayServerSystemChatMessage(boolean overlay, String messageJson) {
    }

    public Component getMessage() {
        return null;
    }

    public String getMessageJson() {
        return null;
    }

    public void setMessageJson(String json) {
    }
}
