package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.Verdict;
import com.jason.plugins.antibadwords.engine.integrations.ChatChannels;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Real chat events of channel plugins, found by reflection and registered only when the plugin is present.
 * Verified in the plugin-api-dump: TownyChat {@code AsyncChatHookEvent} (getPlayer, getMessage, setMessage, setCancelled,
 * getChannel().getName()). Not usable: VentureChatEvent (not cancellable, no setter), HuskChat events (proxy side),
 * mcMMO party chat (author class not in the dump), ChatControl / CarbonChat / FactionsUUID (jars missing).
 */
final class ChannelChatEvents {
    private ChannelChatEvents() {
    }

    static void register(AntiBadWordsPlugin plugin, Engine engine) {
        if (!engine.value(ChatChannels.ENABLED) || !engine.value(ChatChannels.CHANNEL_EVENTS)) {
            return;
        }
        Plugin towny = Bukkit.getPluginManager().getPlugin("TownyChat");
        if (towny == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.palmergames.bukkit.TownyChat.events.AsyncChatHookEvent", true, towny.getClass().getClassLoader());
            Method getPlayer = type.getMethod("getPlayer");
            Method getMessage = type.getMethod("getMessage");
            Method setMessage = type.getMethod("setMessage", String.class);
            Method setCancelled = type.getMethod("setCancelled", boolean.class);
            Method isCancelled = type.getMethod("isCancelled");
            Method getChannel = type.getMethod("getChannel");
            Method channelName = getChannel.getReturnType().getMethod("getName");
            Listener listener = new Listener() { };
            plugin.getServer().getPluginManager().registerEvent(type.asSubclass(Event.class), listener, EventPriority.HIGHEST, (l, event) -> {
                if (!type.isInstance(event)) {
                    return;
                }
                try {
                    if ((Boolean) isCancelled.invoke(event)) {
                        return;
                    }
                    Player player = (Player) getPlayer.invoke(event);
                    String text = (String) getMessage.invoke(event);
                    Object channel = getChannel.invoke(event);
                    String name = channel == null ? "" : String.valueOf(channelName.invoke(channel));
                    if (player == null || text == null) {
                        return;
                    }
                    Message message = Message.of(player.getUniqueId(), player.getName(), player.getWorld().getName(), Message.CHAT, text,
                        player::hasPermission).withDimension(BukkitPlatform.dimensionName(player.getWorld())).withChannel(name);
                    Verdict verdict = engine.inspect(message);
                    if (verdict.captured() || verdict.blocked()) {
                        setCancelled.invoke(event, true);
                    } else if (verdict.censored()) {
                        setMessage.invoke(event, verdict.text());
                    }
                    engine.onViolation(message, verdict);
                } catch (ReflectiveOperationException | ClassCastException ex) {
                    plugin.getLogger().warning("TownyChat channel event could not be read (" + ex.getClass().getSimpleName() + ").");
                }
            }, plugin, false);
            plugin.getLogger().info("TownyChat found: scanning its channel chat events (AsyncChatHookEvent).");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().info("TownyChat found but its AsyncChatHookEvent is not available (" + ex.getClass().getSimpleName() + "); chat covered by the normal chat check.");
        }
    }
}
