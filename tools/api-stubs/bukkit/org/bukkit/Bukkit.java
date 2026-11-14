package org.bukkit;
import java.util.*; import org.bukkit.entity.Player;
public final class Bukkit { public static Collection<? extends Player> getOnlinePlayers(){return null;} public static Player getPlayerExact(String n){return null;} public static Player getPlayer(UUID u){return null;}
 public static boolean dispatchCommand(org.bukkit.command.CommandSender s,String c){return true;} public static org.bukkit.command.ConsoleCommandSender getConsoleSender(){return null;} public static org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder h,int s,String t){return null;} public static boolean isPrimaryThread(){return true;} public static org.bukkit.plugin.PluginManager getPluginManager(){return null;}
 public static org.bukkit.scheduler.BukkitScheduler getScheduler(){return null;} }
