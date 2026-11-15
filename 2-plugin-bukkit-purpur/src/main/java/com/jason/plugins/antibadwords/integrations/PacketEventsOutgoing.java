package com.jason.plugins.antibadwords.integrations;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScan;
import com.jason.plugins.antibadwords.engine.integrations.OutgoingScanSettings;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Outgoing-text scan through PacketEvents. It looks at the packets other plugins send and logs, changes or cancels them as
 * {@code integrations.outgoing-scan.action} says: system chat, player chat, team display name / prefix / suffix (TAB, NametagEdit)
 * and tab-list display names. Team and tab-list packets are changed, never cancelled. Every name used here comes from the
 * PacketEvents API dump of the plugin-api-dump job. A Component is turned into JSON and back with the system chat wrapper
 * ({@code new WrapperPlayServerSystemChatMessage(false, component).getMessageJson()}), so no Adventure serializer name is needed.
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
            if (type == null || event.isCancelled()) {
                return;
            }
            String source;
            if (type.equals(PacketType.Play.Server.SYSTEM_CHAT_MESSAGE)) {
                source = OutgoingScanSettings.SOURCE_SYSTEM_CHAT;
            } else if (type.equals(PacketType.Play.Server.CHAT_MESSAGE)) {
                source = OutgoingScanSettings.SOURCE_PLAYER_CHAT;
            } else if (type.equals(PacketType.Play.Server.TEAMS)) {
                source = OutgoingScanSettings.SOURCE_TEAM;
            } else if (type.equals(PacketType.Play.Server.PLAYER_INFO_UPDATE)) {
                source = OutgoingScanSettings.SOURCE_TAB_LIST;
            } else {
                return;
            }
            if (!scan.covers(source)) {
                return;
            }
            Object who = event.getPlayer();
            if (!(who instanceof Player player)) {
                return;
            }
            switch (source) {
                case OutgoingScanSettings.SOURCE_SYSTEM_CHAT -> systemChat(event, player);
                case OutgoingScanSettings.SOURCE_PLAYER_CHAT -> playerChat(event, player);
                case OutgoingScanSettings.SOURCE_TEAM -> team(event, player);
                default -> tabList(event, player);
            }
        } catch (RuntimeException | LinkageError ex) {
            // a packet we cannot read is left alone; never break the connection
        }
    }

    private void systemChat(PacketSendEvent event, Player player) {
        WrapperPlayServerSystemChatMessage wrapper = new WrapperPlayServerSystemChatMessage(event);
        OutgoingScan.Outcome outcome = scan.handle(OutgoingScanSettings.SOURCE_SYSTEM_CHAT, player.getName(), wrapper.getMessageJson(), true);
        if (outcome.kind() == OutgoingScan.Kind.CANCEL) {
            event.setCancelled(true);
        } else if (outcome.kind() == OutgoingScan.Kind.REWRITE) {
            wrapper.setMessageJson(outcome.json());
            event.markForReEncode(true);
        }
    }

    private void playerChat(PacketSendEvent event, Player player) {
        WrapperPlayServerChatMessage wrapper = new WrapperPlayServerChatMessage(event);
        ChatMessage message = wrapper.getMessage();
        if (message == null) {
            return;
        }
        ClientVersion version = event.getClientVersion();
        OutgoingScan.Outcome outcome = scan.handle(OutgoingScanSettings.SOURCE_PLAYER_CHAT, player.getName(), message.getChatContentJson(version), true);
        if (outcome.kind() == OutgoingScan.Kind.CANCEL) {
            event.setCancelled(true);
        } else if (outcome.kind() == OutgoingScan.Kind.REWRITE) {
            message.setChatContentJson(version, outcome.json());
            wrapper.setMessage(message);
            event.markForReEncode(true);
        }
    }

    private void team(PacketSendEvent event, Player player) {
        WrapperPlayServerTeams wrapper = new WrapperPlayServerTeams(event);
        Optional<WrapperPlayServerTeams.ScoreBoardTeamInfo> present = wrapper.getTeamInfo();
        if (present == null || present.isEmpty()) {
            return;
        }
        WrapperPlayServerTeams.ScoreBoardTeamInfo info = present.get();
        boolean changed = false;
        Component newDisplay = judge(OutgoingScanSettings.SOURCE_TEAM, player, info.getDisplayName());
        if (newDisplay != null) {
            info.setDisplayName(newDisplay);
            changed = true;
        }
        Component newPrefix = judge(OutgoingScanSettings.SOURCE_TEAM, player, info.getPrefix());
        if (newPrefix != null) {
            info.setPrefix(newPrefix);
            changed = true;
        }
        Component newSuffix = judge(OutgoingScanSettings.SOURCE_TEAM, player, info.getSuffix());
        if (newSuffix != null) {
            info.setSuffix(newSuffix);
            changed = true;
        }
        if (changed) {
            wrapper.setTeamInfo(info);
            event.markForReEncode(true);
        }
    }

    private void tabList(PacketSendEvent event, Player player) {
        WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = wrapper.getEntries();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : entries) {
            Component replaced = judge(OutgoingScanSettings.SOURCE_TAB_LIST, player, entry.getDisplayName());
            if (replaced != null) {
                entry.setDisplayName(replaced);
                changed = true;
            }
        }
        if (changed) {
            wrapper.setEntries(entries);
            event.markForReEncode(true);
        }
    }

    /** The changed component, or {@code null} when the component is absent or stays as it is. */
    private Component judge(String source, Player player, Component component) {
        if (component == null) {
            return null;
        }
        String json = new WrapperPlayServerSystemChatMessage(false, component).getMessageJson();
        OutgoingScan.Outcome outcome = scan.handle(source, player.getName(), json, true);
        if (outcome.kind() != OutgoingScan.Kind.REWRITE) {
            return null;
        }
        return new WrapperPlayServerSystemChatMessage(false, outcome.json()).getMessage();
    }
}
