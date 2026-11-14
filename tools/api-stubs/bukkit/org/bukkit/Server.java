package org.bukkit; import java.util.*; import org.bukkit.entity.Player;
public interface Server { Collection<? extends Player> getOnlinePlayers(); org.bukkit.plugin.PluginManager getPluginManager(); String getBukkitVersion(); org.bukkit.command.ConsoleCommandSender getConsoleSender(); }
