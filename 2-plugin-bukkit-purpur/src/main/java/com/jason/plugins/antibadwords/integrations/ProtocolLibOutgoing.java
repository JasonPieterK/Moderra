package com.jason.plugins.antibadwords.integrations;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScan;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScanSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Outgoing-text scan through ProtocolLib: reads the chat component of system chat and player chat packets that other plugins send
 * and logs them or cancels the packet. ProtocolLib cannot change the text here (the write-back classes are not in the verified API
 * dump), so the action "mask" logs instead. The component is read field by field: a String that looks like JSON, or any other field
 * value that {@code WrappedChatComponent.fromHandle} accepts; fields that are not components are skipped.
 */
final class ProtocolLibOutgoing extends PacketAdapter {
    private final OutgoingScan scan;

    private ProtocolLibOutgoing(Plugin plugin, OutgoingScan scan) {
        super(plugin, PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Server.CHAT, PacketType.Play.Server.DISGUISED_CHAT);
        this.scan = scan;
    }

    static void start(Plugin plugin, OutgoingScan scan) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new ProtocolLibOutgoing(plugin, scan));
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null || event.isCancelled()) {
                return;
            }
            String source = event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT
                ? OutgoingScanSettings.SOURCE_SYSTEM_CHAT : OutgoingScanSettings.SOURCE_PLAYER_CHAT;
            if (!scan.covers(source)) {
                return;
            }
            String json = componentJson(event.getPacket().getModifier());
            if (json == null) {
                return;
            }
            if (scan.handle(source, player.getName(), json, false).kind() == OutgoingScan.Kind.CANCEL) {
                event.setCancelled(true);
            }
        } catch (RuntimeException ex) {
            // a packet we cannot read is left alone; never break the connection
        }
    }

    private static String componentJson(StructureModifier<Object> fields) {
        if (fields == null) {
            return null;
        }
        for (int i = 0; i < fields.size(); i++) {
            Object value = fields.read(i);
            if (value == null || value instanceof Boolean || value instanceof Number || value instanceof Enum<?>) {
                continue;
            }
            if (value instanceof String s) {
                if (s.startsWith("{") || s.startsWith("[") || s.startsWith("\"")) {
                    return s;
                }
                continue;
            }
            try {
                String json = WrappedChatComponent.fromHandle(value).getJson();
                if (json != null && !json.isEmpty()) {
                    return json;
                }
            } catch (RuntimeException ex) {
                // not a chat component: next field
            }
        }
        return null;
    }
}
