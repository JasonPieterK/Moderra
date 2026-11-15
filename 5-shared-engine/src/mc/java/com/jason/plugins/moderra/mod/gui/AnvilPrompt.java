package com.jason.plugins.moderra.mod.gui;

import com.jason.plugins.moderra.mod.AnvilNameAccess;
import com.jason.plugins.moderra.mod.ModRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Text input for the menu on the mods: a server-side anvil (a {@link GuiAnvilMenu}) with one named paper in the input slot.
 * The player types the value into the rename field and clicks the result item; the text goes to
 * {@code engine.prompts().onSignInput} (the same entry the Bukkit sign prompt uses). Nothing is given back, no XP is charged.
 *
 * <p>Used when {@code gui.prompt.mode} is {@code anvil}. The default is the fake sign ({@code FakeSign}). The instruction
 * text comes from the engine setting {@code gui.prompt.messages.sign}.
 */
public final class AnvilPrompt {
    private AnvilPrompt() {
    }

    public static void open(ServerPlayer player, String title, String hint) {
        if (player.containerMenu instanceof GuiChestMenu old) {
            old.quiet = true; // opening the anvil closes this menu; that is not the player leaving
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GuiAnvilMenu(id, inventory, hint), Component.literal(title)));
    }

    /** The one paper in the input slot; its name is the hint, so the text field starts with it. */
    static ItemStack paper(String hint) {
        ItemStack stack = new ItemStack(Items.PAPER, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(hint == null || hint.isEmpty() ? " " : hint));
        return stack;
    }

    /** True for the three anvil slots (the player's own inventory slots stay normal). */
    static boolean ours(int slot) {
        return slot == AnvilMenu.INPUT_SLOT || slot == AnvilMenu.ADDITIONAL_SLOT || slot == AnvilMenu.RESULT_SLOT;
    }

    /** Called by the menu for a click on one of the anvil slots. Only the result slot does anything: it sends the text. */
    static void clicked(GuiAnvilMenu menu, int slot, Player player) {
        if (slot == AnvilMenu.RESULT_SLOT && player instanceof ServerPlayer sp) {
            String text = ((AnvilNameAccess) (Object) menu).moderra$itemName();
            if (text != null && !text.isBlank()) {
                menu.done = true;
                menu.empty();
                sp.closeContainer();
                if (ModRuntime.engine != null) {
                    ModRuntime.engine.prompts().onSignInput(sp.getUUID(), new String[] {text});
                }
                return;
            }
        }
        if (player.containerMenu == menu) { // never move an item: put the client back in sync
            menu.setCarried(ItemStack.EMPTY);
            menu.sendAllDataToRemote();
        }
    }

    /** The anvil was closed (Escape, replaced, quit). Without an answer that is a cancel. */
    static void removed(GuiAnvilMenu menu, Player player) {
        if (!menu.done && !menu.quiet && ModRuntime.engine != null) {
            menu.done = true;
            ModRuntime.engine.prompts().onSignInput(player.getUUID(), new String[0]);
        }
    }
}
