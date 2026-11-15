package com.github.retrooper.packetevents.wrapper.play.server;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class WrapperPlayServerChatMessage extends PacketWrapper<WrapperPlayServerChatMessage> {
    public WrapperPlayServerChatMessage(PacketSendEvent event) {
    }

    public ChatMessage getMessage() {
        return null;
    }

    public void setMessage(ChatMessage message) {
    }
}
