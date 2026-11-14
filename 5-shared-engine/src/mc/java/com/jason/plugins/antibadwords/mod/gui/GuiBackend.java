package com.jason.plugins.antibadwords.mod.gui;

import com.jason.plugins.antibadwords.engine.gui.Slot;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only part of the GUI that needs real container and item classes: a server-side 9x6 chest menu without a client
 * mod. It is NOT implemented yet because those vanilla APIs could not be checked (see handoffs/BUILD-VERIFICATION-TODO.md,
 * section "Mod GUI backend"); until a loader installs one with {@link ModGui#install}, {@code /abw} behaves as before.
 *
 * <p>Contract for an implementation:
 * <ul>
 *   <li>{@link #open} shows a 54-slot chest menu titled {@code title} with the given items (other slots empty);
 *       an already open menu of ours is replaced.</li>
 *   <li>{@link #update} changes the items (and title) of the menu that is open.</li>
 *   <li>{@link #close} closes our menu; the resulting "menu closed" notification of the game must be reported with
 *       {@link ModGui#onClosed}, which ignores it when the close was ours.</li>
 *   <li>Every click in the menu (also in the player's own inventory part, shift-click, number keys, drag, double click,
 *       drop) must be cancelled so no item can move; clicks inside the 54 slots are reported with
 *       {@link ModGui#onClick}. Nothing a player does may create an item, and {@code quickMoveStack} must return empty.</li>
 *   <li>All calls happen on the server thread.</li>
 * </ul>
 */
public interface GuiBackend {
    void open(ServerPlayer player, String title, List<Slot> slots);

    void update(ServerPlayer player, String title, List<Slot> slots);

    void close(ServerPlayer player);
}
