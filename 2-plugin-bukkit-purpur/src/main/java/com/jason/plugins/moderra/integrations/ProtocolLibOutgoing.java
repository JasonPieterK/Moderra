package com.jason.plugins.moderra.integrations;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.jason.plugins.moderra.engine.integrations.OutgoingScan;
import com.jason.plugins.moderra.engine.integrations.OutgoingScanSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Outgoing-text scan through ProtocolLib: system chat, player chat, team display name / prefix / suffix and tab-list display names.
 * Action "mask" writes the changed component back into the packet; team and tab-list packets are changed, never cancelled.
 * Chat packets are read field by field: a String that looks like JSON, or any other field value that
 * {@code WrappedChatComponent.fromHandle} accepts; fields that are not components are skipped. The write-back puts a String
 * (or the handle of {@code WrappedChatComponent.fromJson}) into the same field. Team packets use
 * {@code getOptionalStructures().read(0)} and {@code InternalStructure.getChatComponents()}; tab-list packets use
 * {@code getPlayerInfoDataLists()} and a new {@code PlayerInfoData} with the changed display name.
 */
final class ProtocolLibOutgoing extends PacketAdapter {
    private final OutgoingScan scan;

    private ProtocolLibOutgoing(Plugin plugin, OutgoingScan scan) {
        super(plugin, PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Server.CHAT, PacketType.Play.Server.DISGUISED_CHAT,
            PacketType.Play.Server.SCOREBOARD_TEAM, PacketType.Play.Server.PLAYER_INFO);
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
            PacketType type = event.getPacketType();
            if (type == PacketType.Play.Server.SCOREBOARD_TEAM) {
                if (scan.covers(OutgoingScanSettings.SOURCE_TEAM)) {
                    team(event.getPacket(), player);
                }
                return;
            }
            if (type == PacketType.Play.Server.PLAYER_INFO) {
                if (scan.covers(OutgoingScanSettings.SOURCE_TAB_LIST)) {
                    tabList(event.getPacket(), player);
                }
                return;
            }
            String source = type == PacketType.Play.Server.SYSTEM_CHAT
                ? OutgoingScanSettings.SOURCE_SYSTEM_CHAT : OutgoingScanSettings.SOURCE_PLAYER_CHAT;
            if (scan.covers(source)) {
                chat(event, player, source);
            }
        } catch (RuntimeException | LinkageError ex) {
            // a packet we cannot read is left alone; never break the connection
        }
    }

    private void chat(PacketEvent event, Player player, String source) {
        StructureModifier<Object> fields = event.getPacket().getModifier();
        if (fields == null) {
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            Object value = fields.read(i);
            if (value == null || value instanceof Boolean || value instanceof Number || value instanceof Enum<?>) {
                continue;
            }
            boolean text = value instanceof String;
            String json;
            if (text) {
                String s = (String) value;
                if (!(s.startsWith("{") || s.startsWith("[") || s.startsWith("\""))) {
                    continue;
                }
                json = s;
            } else {
                try {
                    json = WrappedChatComponent.fromHandle(value).getJson();
                } catch (RuntimeException ex) {
                    continue; // not a chat component: next field
                }
                if (json == null || json.isEmpty()) {
                    continue;
                }
            }
            OutgoingScan.Outcome outcome = scan.handle(source, player.getName(), json, true);
            if (outcome.kind() == OutgoingScan.Kind.CANCEL) {
                event.setCancelled(true);
            } else if (outcome.kind() == OutgoingScan.Kind.REWRITE) {
                fields.write(i, text ? outcome.json() : WrappedChatComponent.fromJson(outcome.json()).getHandle());
            }
            return; // one component per packet is enough: the first one is the message
        }
    }

    private void team(PacketContainer packet, Player player) {
        StructureModifier<Optional<InternalStructure>> optional = packet.getOptionalStructures();
        if (optional != null && optional.size() > 0) {
            Optional<InternalStructure> params = optional.read(0);
            if (params != null && params.isPresent()) {
                maskComponents(params.get().getChatComponents(), player);
            }
            return;
        }
        maskComponents(packet.getChatComponents(), player); // old servers keep the components in the packet itself
    }

    private void maskComponents(StructureModifier<WrappedChatComponent> components, Player player) {
        if (components == null) {
            return;
        }
        for (int i = 0; i < components.size(); i++) {
            WrappedChatComponent component = components.read(i);
            if (component == null) {
                continue;
            }
            OutgoingScan.Outcome outcome = scan.handle(OutgoingScanSettings.SOURCE_TEAM, player.getName(), component.getJson(), true);
            if (outcome.kind() == OutgoingScan.Kind.REWRITE) {
                components.write(i, WrappedChatComponent.fromJson(outcome.json()));
            }
        }
    }

    private void tabList(PacketContainer packet, Player player) {
        StructureModifier<List<PlayerInfoData>> lists = packet.getPlayerInfoDataLists();
        if (lists == null) {
            return;
        }
        for (int i = 0; i < lists.size(); i++) {
            List<PlayerInfoData> entries;
            try {
                entries = lists.read(i);
            } catch (RuntimeException ex) {
                continue; // this list field does not hold player entries in this server version
            }
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            List<PlayerInfoData> result = new ArrayList<>(entries.size());
            boolean changed = false;
            for (PlayerInfoData data : entries) {
                PlayerInfoData replaced = judge(data, player);
                changed |= replaced != data;
                result.add(replaced);
            }
            if (changed) {
                lists.write(i, result);
            }
        }
    }

    /** The entry with a changed display name, or the same entry when nothing changes. */
    private PlayerInfoData judge(PlayerInfoData data, Player player) {
        try {
            WrappedChatComponent name = data.getDisplayName();
            if (name == null) {
                return data;
            }
            OutgoingScan.Outcome outcome = scan.handle(OutgoingScanSettings.SOURCE_TAB_LIST, player.getName(), name.getJson(), true);
            if (outcome.kind() != OutgoingScan.Kind.REWRITE) {
                return data;
            }
            return new PlayerInfoData(data.getProfileId(), data.getLatency(), data.isListed(), data.getGameMode(), data.getProfile(),
                WrappedChatComponent.fromJson(outcome.json()));
        } catch (RuntimeException ex) {
            return data;
        }
    }
}
