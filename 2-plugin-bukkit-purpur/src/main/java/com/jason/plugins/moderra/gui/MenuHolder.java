package com.jason.plugins.moderra.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as an Moderra menu, so the listener can tell it from every other chest. */
final class MenuHolder implements InventoryHolder {
    private final UUID viewer;
    private Inventory inventory;

    MenuHolder(UUID viewer) {
        this.viewer = viewer;
    }

    UUID viewer() {
        return viewer;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
