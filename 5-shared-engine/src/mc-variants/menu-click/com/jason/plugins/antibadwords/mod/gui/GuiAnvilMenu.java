package com.jason.plugins.antibadwords.mod.gui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** The anvil of {@link AnvilPrompt}. Variant "click": the third parameter of {@code clicked} is {@code ClickType}. */
public final class GuiAnvilMenu extends AnvilMenu {
    /** True once the answer was sent or the cancel was reported. */
    public boolean done;
    /** Set while another menu replaces this one on purpose. */
    public boolean quiet;

    public GuiAnvilMenu(int id, Inventory inventory, String hint) {
        super(id, inventory);
        slots.get(INPUT_SLOT).set(AnvilPrompt.paper(hint));
    }

    /** Empties the input, extra and result slots, so closing gives nothing back. */
    void empty() {
        slots.get(INPUT_SLOT).set(ItemStack.EMPTY);
        slots.get(ADDITIONAL_SLOT).set(ItemStack.EMPTY);
        slots.get(RESULT_SLOT).set(ItemStack.EMPTY);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (AnvilPrompt.ours(slot)) {
            AnvilPrompt.clicked(this, slot, player);
        } else {
            super.clicked(slot, button, type, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        empty();
        super.removed(player);
        AnvilPrompt.removed(this, player);
    }
}
