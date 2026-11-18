package com.jason.plugins.moderra.mod.gui;

import com.jason.plugins.moderra.engine.gui.Icons;
import com.jason.plugins.moderra.engine.gui.Slot;
import java.util.ArrayList;
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
import net.minecraft.world.item.component.ItemLore;

/**
 * The vanilla 9x6 chest menu behind {@link ModGui}: a server-side {@link GuiChestMenu} (a {@code ChestMenu} whose clicks
 * never reach the normal item logic) over a {@link SimpleContainer}. No client mod is needed.
 *
 * <p>Name, lore (the help text: {@code DataComponents.LORE} with {@code new ItemLore(List)}, same signature on all six Minecraft
 * versions in the API dumps) and glow come from the engine's {@link Slot}. Icons use only items present in the dumps.
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
        for (int i = 0; i < SIZE; i++) {
            if (container.getItem(i).isEmpty()) {
                ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE, 1);
                pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
                container.setItem(i, pane);
            }
        }
    }

    private static ItemStack stack(Slot slot) {
        ItemStack stack = new ItemStack(item(slot), 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a76" + slot.name()));
        if (!slot.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : slot.lore()) {
                lines.add(Component.literal("\u00a77" + line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (slot.glowing()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }
        return stack;
    }

    private static Item item(Slot slot) {
        String hint = slot.hint() == null ? "" : slot.hint();
        if (hint.startsWith("category.")) {
            Item byCategory = switch (hint.substring(9)) {
                case "general" -> Items.COMMAND_BLOCK;
                case "scanning", "search" -> Items.SPYGLASS;
                case "matching" -> Items.TARGET;
                case "whitelist" -> Items.WHITE_BANNER;
                case "messages" -> Items.PAPER;
                case "punishment" -> Items.IRON_SWORD;
                case "ladder" -> Items.LADDER;
                case "strikes" -> Items.TNT;
                case "mute" -> Items.BELL;
                case "history" -> Items.KNOWLEDGE_BOOK;
                case "spam" -> Items.SLIME_BALL;
                case "links" -> Items.CHAIN;
                case "evasion" -> Items.ENDER_PEARL;
                case "player_names" -> Items.NAME_TAG;
                case "worlds" -> Items.GRASS_BLOCK;
                case "discord" -> Items.AMETHYST_SHARD;
                case "integrations" -> Items.PISTON;
                case "sounds" -> Items.NOTE_BLOCK;
                case "presets" -> Items.ENCHANTED_BOOK;
                default -> null;
            };
            if (byCategory != null) {
                return byCategory;
            }
        }
        return item(slot.icon());
    }

    private static Item item(String icon) {
        return switch (icon) {
            case Icons.TOGGLE_ON, Icons.DONE -> Items.LIME_CONCRETE;
            case Icons.TOGGLE_OFF, Icons.CANCEL -> Items.RED_CONCRETE;
            case Icons.WARNING -> Items.ORANGE_CONCRETE;
            case Icons.CATEGORY -> Items.BOOKSHELF;
            case Icons.LADDER -> Items.LADDER;
            case Icons.MUTE -> Items.BELL;
            case Icons.DURATION -> Items.CLOCK;
            case Icons.CYCLE -> Items.COMPARATOR;
            case Icons.RULE -> Items.COMPASS;
            case Icons.HOME, Icons.LIST, Icons.LIST_ITEM, Icons.LANGUAGE -> Items.BOOK;
            case Icons.BACK, Icons.PREV_PAGE, Icons.NEXT_PAGE -> Items.ARROW;
            case Icons.LANGUAGE_OFF -> Items.BARRIER;
            case Icons.TEXT, Icons.WORD, Icons.SECRET, Icons.SEARCH, Icons.ADD, Icons.PROFILE -> Items.NAME_TAG;
            case Icons.NUMBER, Icons.RESET -> Items.WRITABLE_BOOK;
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
