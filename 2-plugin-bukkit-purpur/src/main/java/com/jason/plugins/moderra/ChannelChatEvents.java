package com.jason.plugins.moderra;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.Message;
import com.jason.plugins.moderra.engine.Verdict;
import com.jason.plugins.moderra.engine.integrations.ChatChannels;
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
 * ChatControl / CarbonChat / FactionsUUID (jars missing).
 */
final class ChannelChatEvents {
    private ChannelChatEvents() {
    }

    static void register(ModerraPlugin plugin, Engine engine) {
        if (!engine.value(ChatChannels.ENABLED) || !engine.value(ChatChannels.CHANNEL_EVENTS)) {
            return;
        }
        registerMcmmoParty(plugin, engine);
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
                        node -> com.jason.plugins.moderra.BukkitPermissions.has(player, node)).withDimension(BukkitPlatform.dimensionName(player.getWorld())).withChannel(name);
                    Verdict verdict = engine.inspect(message);
                    if (verdict.captured() || verdict.blocked()) {
                        setCancelled.invoke(event, true);
                    } else if (verdict.censored()) {
                        setMessage.invoke(event, verdict.text());
                    }
                    engine.onViolation(message, verdict);
                } catch (ReflectiveOperationException | ClassCastException ex) {
                    plugin.getLogger().warning("TownyChat: error. The channel chat event cannot be read (" + ex.getClass().getSimpleName() + ").");
                }
            }, plugin, false);
            plugin.getLogger().info("TownyChat: found. The plugin scans its channel chat events (AsyncChatHookEvent).");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().info("TownyChat: found. AsyncChatHookEvent is not available (" + ex.getClass().getSimpleName() + "). The normal chat check covers the chat.");
        }
    }

    /**
     * mcMMO party chat. Verified in the plugin-api-dump: {@code McMMOPartyChatEvent extends McMMOChatEvent} (Cancellable) with
     * getMessage(), setMessage(String), getAuthor() (an Author), isCancelled(), setCancelled(boolean). The sender is found with
     * the Author's {@code uuid()} (abstract method {@code UUID uuid()} of the shaded adventure Identity, verified in the dump; called by reflection because the shaded type is not a dependency) or, if that
     * is missing, by the Author's display name. When the sender cannot be found the line is left alone and logged once.
     */
    private static void registerMcmmoParty(ModerraPlugin plugin, Engine engine) {
        Plugin mcmmo = Bukkit.getPluginManager().getPlugin("mcMMO");
        if (mcmmo == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.gmail.nossr50.events.chat.McMMOPartyChatEvent", true, mcmmo.getClass().getClassLoader());
            Class<?> base = Class.forName("com.gmail.nossr50.events.chat.McMMOChatEvent", true, mcmmo.getClass().getClassLoader());
            Method getMessage = base.getMethod("getMessage");
            Method setMessage = base.getMethod("setMessage", String.class);
            Method setCancelled = base.getMethod("setCancelled", boolean.class);
            Method isCancelled = base.getMethod("isCancelled");
            Method getAuthor = base.getMethod("getAuthor");
            boolean[] warned = {false};
            Listener listener = new Listener() { };
            plugin.getServer().getPluginManager().registerEvent(type.asSubclass(Event.class), listener, EventPriority.HIGHEST, (l, event) -> {
                if (!type.isInstance(event)) {
                    return;
                }
                try {
                    if ((Boolean) isCancelled.invoke(event)) {
                        return;
                    }
                    String text = (String) getMessage.invoke(event);
                    Player player = findAuthor(getAuthor.invoke(event));
                    if (player == null || text == null) {
                        if (!warned[0]) {
                            warned[0] = true;
                            plugin.getLogger().warning("mcMMO: notice. The plugin cannot find the sender of a party chat line. It does not check these lines here.");
                        }
                        return;
                    }
                    Message message = Message.of(player.getUniqueId(), player.getName(), player.getWorld().getName(), Message.CHAT, text,
                        node -> com.jason.plugins.moderra.BukkitPermissions.has(player, node)).withDimension(BukkitPlatform.dimensionName(player.getWorld())).withChannel("mcmmo-party");
                    Verdict verdict = engine.inspect(message);
                    if (verdict.captured() || verdict.blocked()) {
                        setCancelled.invoke(event, true);
                    } else if (verdict.censored()) {
                        setMessage.invoke(event, verdict.text());
                    }
                    engine.onViolation(message, verdict);
                } catch (ReflectiveOperationException | ClassCastException ex) {
                    plugin.getLogger().warning("mcMMO: error. The party chat event cannot be read (" + ex.getClass().getSimpleName() + ").");
                }
            }, plugin, false);
            plugin.getLogger().info("mcMMO: found. The plugin scans its party chat events (McMMOPartyChatEvent).");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().info("mcMMO: found. McMMOPartyChatEvent is not available (" + ex.getClass().getSimpleName() + "). The plugin does not scan party chat.");
        }
    }

    private static Player findAuthor(Object author) {
        if (author == null) {
            return null;
        }
        try {
            Object id = author.getClass().getMethod("uuid").invoke(author);
            if (id instanceof java.util.UUID uuid) {
                Player byId = Bukkit.getPlayer(uuid);
                if (byId != null) {
                    return byId;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // fall through to the name lookup
        }
        try {
            Object shown = author.getClass().getMethod("getAuthoredName", Class.forName("com.gmail.nossr50.datatypes.chat.ChatChannel")).invoke(author, new Object[] {null});
            if (shown != null) {
                String plain = String.valueOf(shown).replaceAll("\u00a7.", "");
                return Bukkit.getPlayerExact(plain.strip());
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // sender unknown
        }
        return null;
    }
}
