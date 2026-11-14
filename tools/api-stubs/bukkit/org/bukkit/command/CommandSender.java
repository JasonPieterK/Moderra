package org.bukkit.command; public interface CommandSender extends org.bukkit.permissions.Permissible { void sendMessage(String m); String getName(); }
