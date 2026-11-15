package com.jason.plugins.antibadwords.engine.network;

import static com.jason.plugins.antibadwords.engine.command.Chat.GRAY;
import static com.jason.plugins.antibadwords.engine.command.Chat.GREEN;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.engine.command.CommandContext;
import com.jason.plugins.antibadwords.engine.command.SubCommand;
import java.time.Instant;
import java.util.Map;

/** {@code /abw network}: mode, server id, peers seen, last sync time and queued records (permission: admin). */
public final class NetworkCommand implements SubCommand {
    @Override
    public String name() {
        return "network";
    }

    @Override
    public void execute(CommandContext c) {
        NetworkSync n = c.engine().network();
        String mode = n.mode();
        c.send(GREEN + "Network sync: " + YELLOW + mode + GRAY + " (configured: " + c.engine().value(NetworkSettings.MODE) + ")");
        if (NetworkSettings.OFF.equals(mode)) {
            return;
        }
        c.send(GRAY + "Server id: " + YELLOW + n.serverId());
        Map<String, Long> peers = n.peers();
        c.send(GRAY + "Peers seen: " + YELLOW + (peers.isEmpty() ? "none yet" : peers.size() + " (" + String.join(", ", peers.keySet()) + ")"));
        c.send(GRAY + "Last sync: " + YELLOW + (n.lastSync() == 0 ? "never" : Instant.ofEpochMilli(n.lastSync()).toString()));
        c.send(GRAY + "Queued records: " + YELLOW + n.queued());
    }
}
