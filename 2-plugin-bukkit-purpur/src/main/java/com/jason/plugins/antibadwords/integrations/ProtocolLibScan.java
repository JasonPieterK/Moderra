package com.jason.plugins.antibadwords.integrations;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.jason.plugins.antibadwords.BukkitPlatform;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Packet-level scan of sign updates, book edits and anvil renames through ProtocolLib. Only loaded when ProtocolLib is installed and
 * {@link com.jason.plugins.antibadwords.engine.integrations.PacketScanPlan} chose it (the Bukkit events are missing). Compiled against
 * a name-only shim ({@code src/protocollib-shim}, not in the jar). The packet fields are read by TYPE (String[] for the sign, List for
 * the book pages, String for the anvil name), so no field index of a particular Minecraft version is assumed; a packet without such a
 * field is ignored.
 */
final class ProtocolLibScan extends PacketAdapter {
    private final Engine engine;

    private ProtocolLibScan(Plugin plugin, Engine engine) {
        super(plugin, PacketType.Play.Client.UPDATE_SIGN, PacketType.Play.Client.B_EDIT, PacketType.Play.Client.ITEM_NAME);
        this.engine = engine;
    }

    static void start(Plugin plugin, Engine engine) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new ProtocolLibScan(plugin, engine));
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null || event.isCancelled()) {
                return;
            }
            PacketType type = event.getPacketType();
            StructureModifier<Object> fields = event.getPacket().getModifier();
            if (type == PacketType.Play.Client.UPDATE_SIGN) {
                scan(event, player, Message.SIGN, strings(fields.<Object>withType(String[].class)), false);
            } else if (type == PacketType.Play.Client.B_EDIT) {
                scan(event, player, Message.BOOK, strings(fields.<Object>withType(List.class)), false);
            } else if (type == PacketType.Play.Client.ITEM_NAME) {
                scan(event, player, Message.ANVIL, strings(fields.<Object>withType(String.class)), true);
            }
        } catch (RuntimeException ex) {
            // a packet we cannot read is left alone; never break the connection
        }
    }

    private void scan(PacketEvent event, Player player, String source, List<String> texts, boolean anvil) {
        if (texts.isEmpty()) {
            return;
        }
        Message template = Message.of(player.getUniqueId(), player.getName(), player.getWorld().getName(), source, "", player::hasPermission)
            .withDimension(BukkitPlatform.dimensionName(player.getWorld()));
        Verdict verdict = engine.inspectFirst(template, texts);
        if (!verdict.blocked() && !verdict.censored()) {
            return;
        }
        event.setCancelled(true);
        if (!anvil || engine.firstAnvilViolation(player.getUniqueId())) {
            engine.onViolation(template, verdict);
        }
    }

    private static List<String> strings(StructureModifier<Object> modifier) {
        List<String> out = new ArrayList<>();
        if (modifier == null) {
            return out;
        }
        for (int i = 0; i < modifier.size(); i++) {
            add(out, modifier.read(i));
        }
        return out;
    }

    private static void add(List<String> out, Object value) {
        if (value instanceof String s) {
            if (!s.isBlank()) {
                out.add(s);
            }
        } else if (value instanceof String[] arr) {
            for (String s : arr) {
                add(out, s);
            }
        } else if (value instanceof Iterable<?> it) {
            for (Object o : it) {
                add(out, o);
            }
        }
    }
}
