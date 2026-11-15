package com.jason.plugins.antibadwords.integrations;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScan;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScanSettings;
import org.bukkit.entity.Player;

/**
 * Outgoing-text scan through PacketEvents: looks at the system chat packets other plugins send (VentureChat, HuskChat, broadcasts)
 * and logs, changes or cancels them as {@code integrations.outgoing-scan.action} says. This is the only backend that can change
 * the text. Player chat, team and player-info packets are not covered here: their wrapper classes are not in the verified API dump.
 */
final class PacketEventsOutgoing extends PacketListenerAbstract {
    private final OutgoingScan scan;

    private PacketEventsOutgoing(OutgoingScan scan) {
        this.scan = scan;
    }

    static void start(OutgoingScan scan) {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null) {
            throw new IllegalStateException("PacketEvents API not ready");
        }
        api.getEventManager().registerListener(new PacketEventsOutgoing(scan));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            PacketTypeCommon type = event.getPacketType();
            if (type == null || event.isCancelled() || !type.equals(PacketType.Play.Server.SYSTEM_CHAT_MESSAGE)
                || !scan.covers(OutgoingScanSettings.SOURCE_SYSTEM_CHAT)) {
                return;
            }
            Object who = event.getPlayer();
            if (!(who instanceof Player player)) {
                return;
            }
            WrapperPlayServerSystemChatMessage wrapper = new WrapperPlayServerSystemChatMessage(event);
            String json = wrapper.getMessageJson();
            OutgoingScan.Outcome outcome = scan.handle(OutgoingScanSettings.SOURCE_SYSTEM_CHAT, player.getName(), json, true);
            if (outcome.kind() == OutgoingScan.Kind.CANCEL) {
                event.setCancelled(true);
            } else if (outcome.kind() == OutgoingScan.Kind.REWRITE) {
                wrapper.setMessageJson(outcome.json());
                event.markForReEncode(true);
            }
        } catch (RuntimeException ex) {
            // a packet we cannot read is left alone; never break the connection
        }
    }
}
