package com.jason.plugins.antibadwords;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.integrations.ChatChannels;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;

/**
 * Chat-channel plugins (ChatControl, VentureChat, HuskChat, CarbonChat): detection by plugin name only, no API of theirs is
 * called. Their channel names are NOT read (their event and API names were not verified); channels come from the engine's
 * {@code channel-commands} map instead. What this class does: find them, log them, and pick the early chat priority.
 */
final class ChatChannelPlugins {
    private ChatChannelPlugins() {
    }

    /** The enabled-and-installed plugins from {@link ChatChannels#PLUGIN_NAMES}. */
    static List<String> detect(Engine engine) {
        List<String> found = new ArrayList<>();
        if (!engine.value(ChatChannels.ENABLED)) {
            return found;
        }
        for (String name : ChatChannels.PLUGIN_NAMES) {
            if (engine.value(ChatChannels.switchOf(name)) && Bukkit.getPluginManager().getPlugin(name) != null) {
                found.add(name);
            }
        }
        return found;
    }

    /** {@code null} = no opinion (use the normal priority). */
    static EventPriority priority(Engine engine, List<String> found) {
        if (!engine.value(ChatChannels.ENABLED)) {
            return null;
        }
        String setting = engine.value(ChatChannels.PRIORITY);
        if (ChatChannels.PRIORITY_LOWEST.equals(setting)) {
            return EventPriority.LOWEST;
        }
        if (ChatChannels.PRIORITY_HIGHEST.equals(setting)) {
            return EventPriority.HIGHEST;
        }
        return found.isEmpty() ? null : EventPriority.LOWEST;
    }
}
