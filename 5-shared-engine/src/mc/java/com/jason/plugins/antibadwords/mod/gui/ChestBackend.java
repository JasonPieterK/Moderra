package com.jason.plugins.antibadwords.mod.gui;

import com.jason.plugins.antibadwords.engine.gui.Icons;
import com.jason.plugins.antibadwords.engine.gui.Slot;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The vanilla 9x6 chest menu behind {@link ModGui}: a server-side {@link GuiChestMenu} (a {@code ChestMenu} whose clicks
 * never reach the normal item logic) over a {@link SimpleContainer}. No client mod is needed.
 *
 * <p>Only item names are shown: the lore of a slot is not set yet because the {@code ItemLore} class is not in the API
 * dumps (it is on the list in tools/dump-mc-api.py). Icons use only items present in the 1.21.11 and 26.3 dumps.
 */
public final class ChestBackend implements GuiBackend {
    private static final int SIZE = 54;

    @Override
    public void open(ServerPlayer player, String title, List<Slot> slots) {
        if (player.containerMenu instanceof GuiChestMenu old) {
            old.quiet = true; // opening the new menu closes this one
        }
        SimpleContainer container = new SimpleContainer(SIZE);
        fill(container, slots);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GuiChestMenu(id, inventory, container),
            Component.literal(title)));
    }

    @Override
    public void update(ServerPlayer player, String title, List<Slot> slots) {
        if (!(player.containerMenu instanceof GuiChestMenu menu)) {
            open(player, title, slots);
            return;
        }
        fill((SimpleContainer) menu.getContainer(), slots);
        menu.broadcastChanges();
    }

    @Override
    public void close(ServerPlayer player) {
        if (player.containerMenu instanceof GuiChestMenu) {
            player.closeContainer();
        }
    }

    private static void fill(SimpleContainer container, List<Slot> slots) {
        for (int i = 0; i < SIZE; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        for (Slot slot : slots) {
            if (slot.index() >= 0 && slot.index() < SIZE) {
                container.setItem(slot.index(), stack(slot));
            }
        }
    }

    private static ItemStack stack(Slot slot) {
        ItemStack stack = new ItemStack(item(slot.icon()), 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(slot.name()));
        if (slot.glowing()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }
        return stack;
    }

    private static Item item(String icon) {
        return switch (icon) {
            case Icons.CATEGORY, Icons.HOME, Icons.LIST, Icons.LIST_ITEM, Icons.LANGUAGE, Icons.LADDER, Icons.RULE -> Items.BOOK;
            case Icons.BACK, Icons.PREV_PAGE, Icons.NEXT_PAGE -> Items.ARROW;
            case Icons.TOGGLE_OFF, Icons.CANCEL, Icons.WARNING, Icons.LANGUAGE_OFF, Icons.MUTE -> Items.BARRIER;
            case Icons.TEXT, Icons.WORD, Icons.SECRET, Icons.SEARCH, Icons.ADD, Icons.PROFILE -> Items.NAME_TAG;
            case Icons.NUMBER, Icons.DURATION, Icons.CYCLE, Icons.RESET -> Items.WRITABLE_BOOK;
            default -> Items.PAPER; // toggle_on, info, done, status, anything new
        };
    }

    // ---- called by GuiChestMenu (its clicked/removed overrides differ per Minecraft version) ----

    static void clicked(GuiChestMenu menu, int slot, int button, String clickType, Player player) {
        if (player instanceof ServerPlayer sp && slot >= 0 && slot < SIZE) {
            ModGui.onClick(sp, slot, clickType, button);
        }
        // Never call super.clicked: nothing may move. Put the client back in sync (cursor stack, slots).
        if (player.containerMenu == menu) {
            menu.setCarried(ItemStack.EMPTY);
            menu.sendAllDataToRemote();
        }
    }

    static void removed(GuiChestMenu menu, Player player) {
        if (menu.quiet) {
            menu.quiet = false;
            return;
        }
        if (player instanceof ServerPlayer sp) {
            ModGui.onClosed(sp);
        }
    }
}
