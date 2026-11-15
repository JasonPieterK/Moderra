package com.jason.plugins.moderra.mod.gui;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.21.11 and (assumed) 26.2 and older: the click type is net.minecraft.world.inventory.ClickType. The only difference between the two variants is the type of the third {@code clicked} parameter. */
public final class GuiChestMenu extends ChestMenu {
    /** Set while ours replaces this menu, so the close is not reported as the player leaving. */
    public boolean quiet;

    public GuiChestMenu(int id, Inventory inventory, Container container) {
        super(MenuType.GENERIC_9x6, id, inventory, container, 6);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        ChestBackend.clicked(this, slot, button, String.valueOf(type), player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        ChestBackend.removed(this, player);
    }
}
