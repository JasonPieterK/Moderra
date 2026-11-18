package com.jason.plugins.moderra.engine.gui;

import java.util.List;
import java.util.Locale;

/** One clickable item of a {@link Page}: how it looks and what a click does. {@code hint}: see {@link Slot#hint()}. */
public record Entry(String icon, String name, List<String> lore, boolean glowing, Handler handler, String hint) {
    /** What happens when the viewer clicks the entry. Permission was already checked by {@link Menu#click}. */
    @FunctionalInterface
    public interface Handler {
        MenuAction click(MenuState state, ClickType type);
    }

    public Entry {
        lore = List.copyOf(lore);
    }

    public Entry(String icon, String name, List<String> lore, boolean glowing, Handler handler) {
        this(icon, name, lore, glowing, handler, null);
    }

    /** The same entry with a more specific icon name. */
    public Entry hinted(String hint) {
        return new Entry(icon, name, lore, glowing, handler, hint);
    }

    /** {@code "Look-alike tables"} becomes {@code look_alike_tables}: the form used in icon hints. */
    public static String slug(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", "_").replaceAll("^_+|_+$", "");
    }

    public Slot slot(int index) {
        return new Slot(index, icon, name, lore, glowing, hint);
    }
}
