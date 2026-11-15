package com.jason.plugins.moderra.engine.gui;

import java.util.List;

/** One clickable item of a {@link Page}: how it looks and what a click does. */
public record Entry(String icon, String name, List<String> lore, boolean glowing, Handler handler) {
    /** What happens when the viewer clicks the entry. Permission was already checked by {@link Menu#click}. */
    @FunctionalInterface
    public interface Handler {
        MenuAction click(MenuState state, ClickType type);
    }

    public Entry {
        lore = List.copyOf(lore);
    }

    public Slot slot(int index) {
        return new Slot(index, icon, name, lore, glowing);
    }
}
