package com.jason.plugins.moderra.engine.network;

import com.jason.plugins.moderra.engine.command.Chat;
import com.jason.plugins.moderra.engine.command.CommandContext;
import com.jason.plugins.moderra.engine.command.SubCommand;
import java.time.Instant;
import java.util.Map;

/** {@code /moderra network}: mode, server id, peers seen, last sync time and queued records (permission: admin). */
public final class NetworkCommand implements SubCommand {
    @Override
    public String name() {
        return "network";
    }

    @Override
    public void execute(CommandContext c) {
        NetworkSync n = c.engine().network();
        String mode = n.mode();
        c.send(Chat.heading("Network sync."));
        c.send(Chat.info("Mode: " + Chat.val(mode) + ". Configured mode: " + Chat.val(c.engine().value(NetworkSettings.MODE)) + "."));
        if (NetworkSettings.OFF.equals(mode)) {
            return;
        }
        c.send(Chat.info("Server id: " + Chat.val(n.serverId()) + "."));
        Map<String, Long> peers = n.peers();
        c.send(Chat.info("Peers seen: " + Chat.val(peers.isEmpty() ? "none yet" : peers.size() + " (" + String.join(", ", peers.keySet()) + ")") + "."));
        c.send(Chat.info("Last sync: " + Chat.val(n.lastSync() == 0 ? "never" : Instant.ofEpochMilli(n.lastSync()).toString()) + "."));
        c.send(Chat.info("Queued records: " + Chat.val(n.queued()) + "."));
    }
}
