package org.bukkit.inventory; public interface Inventory { InventoryHolder getHolder(); void clear(); void setItem(int i, ItemStack s); int getSize(); }
