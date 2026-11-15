package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.network.BungeeForward;
import com.jason.plugins.antibadwords.engine.network.NetworkSettings;
import com.jason.plugins.antibadwords.engine.network.PluginMessagingChannel;
import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Plugin messaging glue for {@code integrations.network.mode: plugin-messaging}, using only the long-stable Bukkit
 * {@code Messenger} API.
 *
 * <ul>
 *   <li>A plugin message needs an online player as carrier. While nobody is online the engine queues the records
 *       ({@link PluginMessagingChannel}) and sends them when a player is online (next poll or next change).</li>
 *   <li>A proxy does not relay a custom channel to other backend servers by itself, so outgoing records are wrapped
 *       in the {@code BungeeCord} channel {@code Forward ALL antibadwords:sync} message ({@link BungeeForward}); the
 *       proxy (BungeeCord, or Velocity with its BungeeCord bridge enabled) delivers it to every other server.</li>
 *   <li>Incoming: the forwarded message arrives on {@code BungeeCord}; a message directly on
 *       {@code antibadwords:sync} is accepted too.</li>
 * </ul>
 * Not verified on a real network (build host not reachable); see the BUILD-VERIFICATION-TODO list.
 */
final class NetworkMessaging implements PluginMessageListener {
    private final Plugin plugin;
    private final Engine engine;
    private boolean registered;

    NetworkMessaging(Plugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    void start() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, NetworkSettings.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, NetworkSettings.CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, BungeeForward.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, BungeeForward.CHANNEL, this);
        registered = true;
        engine.network().setTransport(new PluginMessagingChannel.Transport() {
            @Override
            public void send(String channel, byte[] payload) {
                byte[] message = BungeeForward.encodeForward(channel, payload);
                Player carrier = carrier();
                if (message == null || carrier == null) {
                    throw new IllegalStateException("no carrier"); // the channel keeps the record queued
                }
                carrier.sendPluginMessage(plugin, BungeeForward.CHANNEL, message);
            }

            @Override
            public boolean available() {
                return carrier() != null;
            }
        });
    }

    void stop() {
        if (!registered) {
            return;
        }
        registered = false;
        engine.network().setTransport(null);
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin);
        messenger.unregisterIncomingPluginChannel(plugin);
    }

    private Player carrier() {
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        return online.isEmpty() ? null : online.iterator().next();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        byte[] payload = NetworkSettings.CHANNEL.equals(channel) ? message
            : BungeeForward.CHANNEL.equals(channel) ? BungeeForward.decodeForwarded(NetworkSettings.CHANNEL, message) : null;
        if (payload != null) {
            engine.network().receive(payload);
        }
    }
}
