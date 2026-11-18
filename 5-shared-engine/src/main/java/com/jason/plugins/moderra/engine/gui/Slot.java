package com.jason.plugins.moderra.engine.gui;

import java.util.List;

/**
 * One item of the 9x6 chest grid, as plain data. {@code icon} is an abstract name from {@link Icons} that each platform
 * maps to a real item; nothing in here is a Minecraft class. Slots without an item are simply not in the rendered list.
 * {@code hint} is an optional, more specific icon name (for example {@code category.punishment} or
 * {@code setting.mute.enabled}); a platform tries it first and falls back to {@code icon} when it has no mapping for it.
 */
public record Slot(int index, String icon, String name, List<String> lore, boolean glowing, String hint) {
    public Slot {
        lore = List.copyOf(lore);
    }

    public Slot(int index, String icon, String name, List<String> lore, boolean glowing) {
        this(index, icon, name, lore, glowing, null);
    }

    /** The icon names to try, most specific first. */
    public List<String> iconCandidates() {
        return hint == null || hint.isBlank() ? List.of(icon) : List.of(hint, icon);
    }
}
