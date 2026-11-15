package com.jason.plugins.moderra.engine.gui;

import java.util.List;

/**
 * One item of the 9x6 chest grid, as plain data. {@code icon} is an abstract name from {@link Icons} that each platform
 * maps to a real item; nothing in here is a Minecraft class. Slots without an item are simply not in the rendered list.
 */
public record Slot(int index, String icon, String name, List<String> lore, boolean glowing) {
    public Slot {
        lore = List.copyOf(lore);
    }
}
