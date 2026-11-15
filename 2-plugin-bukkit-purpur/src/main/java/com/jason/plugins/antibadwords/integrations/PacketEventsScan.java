package com.jason.plugins.antibadwords.integrations;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.jason.plugins.antibadwords.BukkitPlatform;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Packet-level scan of sign updates, book edits and anvil renames through PacketEvents: the same behaviour as
 * {@link ProtocolLibScan}. Only loaded when PacketEvents is installed and
 * {@link com.jason.plugins.antibadwords.engine.integrations.PacketScanPlan} chose it. Compiled against a name-only shim
 * ({@code src/protocollib-shim}, not in the jar). A packet that cannot be read is ignored.
 */
final class PacketEventsScan extends PacketListenerAbstract {
    private final Engine engine;

    private PacketEventsScan(Engine engine) {
        this.engine = engine;
    }

    static void start(Engine engine) {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null) {
            throw new IllegalStateException("PacketEvents API not ready");
        }
        api.getEventManager().registerListener(new PacketEventsScan(engine));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        try {
            PacketTypeCommon type = event.getPacketType();
            if (event.isCancelled() || type == null) {
                return;
            }
            boolean sign = type.equals(PacketType.Play.Client.UPDATE_SIGN);
            boolean book = type.equals(PacketType.Play.Client.EDIT_BOOK);
            boolean anvil = type.equals(PacketType.Play.Client.NAME_ITEM);
            if (!sign && !book && !anvil) {
                return;
            }
            Object who = event.getPlayer();
            if (!(who instanceof Player player)) {
                return;
            }
            List<String> texts = new ArrayList<>();
            String source;
            if (sign) {
                source = Message.SIGN;
                add(texts, new WrapperPlayClientUpdateSign(event).getTextLines());
            } else if (book) {
                source = Message.BOOK;
                WrapperPlayClientEditBook wrapper = new WrapperPlayClientEditBook(event);
                add(texts, wrapper.getPages());
                add(texts, wrapper.getTitle());
            } else {
                source = Message.ANVIL;
                add(texts, new WrapperPlayClientNameItem(event).getItemName());
            }
            scan(event, player, source, texts, anvil);
        } catch (RuntimeException ex) {
            // a packet we cannot read is left alone; never break the connection
        }
    }

    private void scan(PacketReceiveEvent event, Player player, String source, List<String> texts, boolean anvil) {
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

    private static void add(List<String> out, String[] lines) {
        if (lines != null) {
            for (String s : lines) {
                add(out, s);
            }
        }
    }

    private static void add(List<String> out, List<String> lines) {
        if (lines != null) {
            for (String s : lines) {
                add(out, s);
            }
        }
    }

    private static void add(List<String> out, String s) {
        if (s != null && !s.isBlank()) {
            out.add(s);
        }
    }
}
