package com.jason.plugins.moderra.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bukkit events for the menu. Rule: inside one of our inventories NOTHING may move. Every click, shift-click, number
 * key, double click, drop and drag is cancelled first; only then is the click mapped to a menu action. Clicks in the
 * player's own inventory are cancelled too while a menu is open (a shift-click would move items into the menu).
 */
public final class GuiListener implements Listener {
    private final GuiManager gui;

    public GuiListener(GuiManager gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!GuiManager.isMenu(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            gui.onClick(player, event.getClickedInventory(), event.getRawSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (GuiManager.isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (GuiManager.isMenu(event.getInventory()) && event.getPlayer() instanceof Player player) {
            gui.onClose(player, event.getInventory());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gui.onQuit(event.getPlayer().getUniqueId());
    }
}
