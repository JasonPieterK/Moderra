package com.jason.plugins.antibadwords.gui;

import com.jason.plugins.antibadwords.AntiBadWordsCommand;
import com.jason.plugins.antibadwords.AntiBadWordsPlugin;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
import com.jason.plugins.antibadwords.engine.gui.ClickType;
import com.jason.plugins.antibadwords.engine.gui.Icons;
import com.jason.plugins.antibadwords.engine.gui.Menu;
import com.jason.plugins.antibadwords.engine.gui.MenuAction;
import com.jason.plugins.antibadwords.engine.gui.MenuState;
import com.jason.plugins.antibadwords.engine.gui.Slot;
import com.jason.plugins.antibadwords.engine.gui.TextRequest;
import com.jason.plugins.antibadwords.engine.gui.prompt.TextPrompt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shows the engine's settings menu ({@link Menu}) as a 54-slot chest inventory. All menu logic (what is on a page,
 * what a click does, validation, saving) is in the engine; this class only draws {@link Slot}s as items and forwards
 * clicks. One {@link Session} per viewer. Everything runs on the main thread.
 */
public final class GuiManager {
    static final int SIZE = 54;
    private static final int MAX_TITLE = 32; // old Bukkit versions refuse longer inventory titles

    /** What one viewer has open. */
    static final class Session {
        final MenuState state;
        final UUID viewer;
        Inventory inventory;
        String title = "";
        /** True while this class itself opens or closes the inventory (the close event is not a "viewer closed it"). */
        boolean internal;
        /** True while the viewer types an answer in the chat; the inventory is closed then. */
        boolean prompting;

        Session(MenuState state, UUID viewer) {
            this.state = state;
            this.viewer = viewer;
        }
    }

    private final AntiBadWordsPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private GuiIcons icons;

    public GuiManager(AntiBadWordsPlugin plugin) {
        this.plugin = plugin;
        reloadIcons();
    }

    private Engine engine() {
        return plugin.getEngine();
    }

    /** Reads gui-icons.yml again (jar defaults plus the admin's file). */
    public void reloadIcons() {
        icons = GuiIcons.load(plugin.getDataFolder().toPath(), message -> plugin.getLogger().warning(message));
    }

    // ------------------------------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------------------------------

    /** Opens the main menu for the player; {@code page} (1-based) jumps ahead on the main menu. */
    public void open(Player player, int page) {
        if (!player.hasPermission(Menu.PERMISSION)) {
            player.sendMessage("§cNo permission.");
            return;
        }
        reloadIcons();
        if (sessions.remove(player.getUniqueId()) != null) {
            engine().prompts().cancel(player.getUniqueId());
        }
        Session session = new Session(Menu.open(engine()), player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        CommandSender viewer = viewer(player);
        // The model has no "go to page" call: click its own next-page button until the page is reached.
        for (int i = 1; i < page && i < 100; i++) {
            Integer next = slotWithIcon(session, Icons.NEXT_PAGE);
            if (next == null) {
                break;
            }
            Menu.click(session.state, next, ClickType.LEFT, viewer);
        }
        show(player, session, true);
    }

    private static Integer slotWithIcon(Session session, String icon) {
        for (Slot slot : Menu.render(session.state)) {
            if (icon.equals(slot.icon())) {
                return slot.index();
            }
        }
        return null;
    }

    /** Draws the current page. A new inventory is opened when there is none yet or the title changed. */
    private void show(Player player, Session session, boolean forceNew) {
        String title = clip(Menu.title(session.state));
        List<Slot> slots = Menu.render(session.state);
        if (forceNew || session.inventory == null || !title.equals(session.title)) {
            MenuHolder holder = new MenuHolder(session.viewer);
            Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
            holder.inventory(inventory);
            fill(inventory, slots);
            session.inventory = inventory;
            session.title = title;
            session.internal = true;
            try {
                player.openInventory(inventory);
            } finally {
                session.internal = false;
            }
        } else {
            session.inventory.clear();
            fill(session.inventory, slots);
        }
    }

    private static String clip(String title) {
        return title.length() <= MAX_TITLE ? title : title.substring(0, MAX_TITLE);
    }

    private void fill(Inventory inventory, List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot.index() >= 0 && slot.index() < SIZE) {
                inventory.setItem(slot.index(), item(slot));
            }
        }
    }

    private ItemStack item(Slot slot) {
        ItemStack stack = new ItemStack(icons.material(slot.icon()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(slot.name());
            meta.setLore(new ArrayList<>(slot.lore()));
            if (slot.glowing()) {
                meta.addEnchant(Enchantment.LURE, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------------------------------
    // Events (called by GuiListener)
    // ------------------------------------------------------------------------------------------

    /** True when the inventory is one of ours (by holder), whoever the viewer is. */
    static boolean isMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MenuHolder;
    }

    void onClick(Player player, Inventory clicked, int rawSlot, org.bukkit.event.inventory.ClickType bukkitClick) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory == null) {
            player.closeInventory(); // a menu without a session (stale after a reload): close it, nothing is possible
            return;
        }
        if (clicked != session.inventory || rawSlot < 0 || rawSlot >= SIZE) {
            return; // the player's own inventory below: cancelled by the listener, nothing to do
        }
        handle(player, session, Menu.click(session.state, rawSlot, translate(bukkitClick), viewer(player)));
    }

    void onClose(Player player, Inventory inventory) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != inventory || session.internal || session.prompting) {
            return;
        }
        MenuAction action = Menu.closeRequested(session.state, viewer(player));
        if (action.kind() == MenuAction.Kind.REFRESH) {
            // Unsaved changes: the confirm page replaces the closed inventory. Opening one inside the close event
            // is not allowed, so it happens on the next tick.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.get(player.getUniqueId()) == session) {
                    show(player, session, true);
                }
            });
            return;
        }
        sessions.remove(player.getUniqueId());
        if (action.message() != null) {
            player.sendMessage(action.message());
        }
    }

    /** The player left: forget the menu and any pending prompt. */
    public void onQuit(UUID player) {
        Session session = sessions.remove(player);
        if (session != null) {
            Menu.closed(session.state);
        }
        engine().prompts().onQuit(player);
    }

    /** Plugin disable: close every open menu (staged changes are dropped). */
    public void closeAll() {
        for (Session session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.viewer);
            sessions.remove(session.viewer);
            Menu.closed(session.state);
            if (player != null && session.inventory != null && !session.prompting) {
                session.internal = true;
                player.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------------------------

    private void handle(Player player, Session session, MenuAction action) {
        switch (action.kind()) {
            case NONE -> { }
            case REFRESH -> show(player, session, false);
            case ERROR -> {
                if (action.message() != null) {
                    player.sendMessage("§c" + action.message());
                }
                show(player, session, false);
            }
            case TEXT_INPUT -> startPrompt(player, session, action.request());
            case CLOSE, SAVED, DENIED -> {
                sessions.remove(session.viewer);
                session.internal = true;
                player.closeInventory();
                if (action.message() != null) {
                    player.sendMessage((action.kind() == MenuAction.Kind.SAVED ? "§a" : "§e") + action.message());
                }
            }
        }
    }

    /**
     * Text input: the chest is closed and the engine's prompt service takes the next chat line (the sign editor is not
     * used on Bukkit, see {@code BukkitPlatform#supportsSignPrompt}). The answer is validated by the prompt, handed to
     * the menu model, and the chest opens again.
     */
    private void startPrompt(Player player, Session session, TextRequest request) {
        Setting<?> setting = engine().registry().get(request.key());
        boolean secret = request.masked() || (setting != null && setting.type() == SettingType.SECRET);
        TextPrompt prompt = TextPrompt.of(request.prompt(), request.hint(), secret, text -> {
            if (request.purpose() == TextRequest.Purpose.ADD_ITEM) {
                return text.isBlank() ? "Nothing typed." : null;
            }
            if (setting == null) {
                return "That setting no longer exists.";
            }
            try {
                setting.require(secret ? text.trim() : text);
                return null;
            } catch (IllegalArgumentException ex) {
                return secret ? "That value is not valid." : ex.getMessage();
            }
        }, text -> resume(session, menu -> Menu.submitText(session.state, text, viewer(menu))),
            () -> resume(session, menu -> Menu.cancelText(session.state, viewer(menu))));
        session.prompting = true;
        session.internal = true;
        try {
            player.closeInventory();
        } finally {
            session.internal = false;
        }
        session.inventory = null;
        engine().prompts().start(player.getUniqueId(), prompt);
    }

    /** The prompt ended (answer, cancel, timeout): apply it to the model and show the menu again. */
    private void resume(Session session, Function<Player, MenuAction> apply) {
        session.prompting = false;
        Player player = Bukkit.getPlayer(session.viewer);
        if (player == null || sessions.get(session.viewer) != session) {
            if (sessions.get(session.viewer) == session) {
                sessions.remove(session.viewer);
            }
            Menu.closed(session.state);
            return;
        }
        MenuAction action = apply.apply(player);
        if (action.closesMenu()) {
            sessions.remove(session.viewer);
            if (action.message() != null) {
                player.sendMessage(action.message());
            }
            return;
        }
        if (action.kind() == MenuAction.Kind.ERROR && action.message() != null) {
            player.sendMessage("§c" + action.message());
        }
        show(player, session, true);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    static ClickType translate(org.bukkit.event.inventory.ClickType click) {
        if (click == null) {
            return ClickType.OTHER;
        }
        return switch (click) {
            case LEFT -> ClickType.LEFT;
            case RIGHT -> ClickType.RIGHT;
            case SHIFT_LEFT -> ClickType.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickType.SHIFT_RIGHT;
            case MIDDLE -> ClickType.MIDDLE;
            case DROP, CONTROL_DROP -> ClickType.DROP;
            default -> ClickType.OTHER; // number keys, double click, window border ...
        };
    }

    private static CommandSender viewer(Player player) {
        return AntiBadWordsCommand.wrap(player);
    }
}
