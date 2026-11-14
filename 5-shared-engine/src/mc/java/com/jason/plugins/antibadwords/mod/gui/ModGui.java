package com.jason.plugins.antibadwords.mod.gui;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.gui.ClickType;
import com.jason.plugins.antibadwords.engine.gui.Menu;
import com.jason.plugins.antibadwords.engine.gui.MenuAction;
import com.jason.plugins.antibadwords.engine.gui.MenuState;
import com.jason.plugins.antibadwords.engine.gui.TextRequest;
import com.jason.plugins.antibadwords.engine.gui.prompt.TextPrompt;
import com.jason.plugins.antibadwords.mod.ModRuntime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Glue between the engine's GUI model ({@code engine.gui.Menu}) and a {@link GuiBackend} that shows a server-side chest
 * menu. Contains no vanilla container or item code, only {@link ServerPlayer} (already used elsewhere in this module),
 * so it is independent of the Minecraft version. Server thread only.
 *
 * <p>NOT compiled by the Maven test module (stub-compiled only, never run).
 */
public final class ModGui {
    private static GuiBackend backend;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ModGui() {
    }

    /** Per viewer: the engine's menu state plus two flags that tell our own closes from the player's. */
    private static final class Session {
        final MenuState state = Menu.open(ModRuntime.engine);
        /** true while we close the menu ourselves (text prompt, finished): the "closed" notification is ignored. */
        boolean closingByUs;
        /** true while a text prompt is out; the menu is closed and comes back after the answer. */
        boolean awaitingText;
    }

    /** Called once by a loader that has a working backend. Without one the menu stays unavailable. */
    public static void install(GuiBackend implementation) {
        backend = implementation;
    }

    public static boolean available() {
        return backend != null;
    }

    /** Opens the main menu. False when there is no backend, no engine or the player lacks the permission (fail closed). */
    public static boolean open(ServerPlayer player) {
        if (backend == null || ModRuntime.engine == null || !ModRuntime.has(player, Menu.PERMISSION)) {
            return false;
        }
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null) {
            if (old.awaitingText) {
                ModRuntime.engine.prompts().cancel(player.getUUID()); // its callbacks see the session is gone and do nothing
            }
            Menu.closed(old.state);
        }
        Session session = new Session();
        SESSIONS.put(player.getUUID(), session);
        backend.open(player, Menu.title(session.state), Menu.render(session.state));
        return true;
    }

    /**
     * A click inside the chest part of the menu.
     *
     * @param slot        0..53
     * @param vanillaType the name of the vanilla click type (PICKUP, QUICK_MOVE, THROW, CLONE, SWAP, ...)
     * @param button      the vanilla button (0 left, 1 right for PICKUP/QUICK_MOVE)
     */
    public static void onClick(ServerPlayer player, int slot, String vanillaType, int button) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.awaitingText) {
            return;
        }
        handle(player, session, Menu.click(session.state, slot, mapClick(vanillaType, button), sender(player)));
    }

    /** The menu was closed on the client side (Esc) or by anything but us. */
    public static void onClosed(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.closingByUs) {
            session.closingByUs = false;
            return;
        }
        if (session.awaitingText) {
            return;
        }
        MenuAction action = Menu.closeRequested(session.state, sender(player));
        if (action.kind() == MenuAction.Kind.REFRESH) {
            // unsaved changes: the confirm page replaces the closed menu, so open it again
            backend.open(player, Menu.title(session.state), Menu.render(session.state));
            return;
        }
        SESSIONS.remove(player.getUUID());
        say(player, action.message());
    }

    /** Disconnect or server stop: forget the viewer's staged changes (the engine ends the text prompt itself). */
    public static void onQuit(UUID id) {
        Session session = SESSIONS.remove(id);
        if (session != null) {
            Menu.closed(session.state);
        }
    }

    // ------------------------------------------------------------------------------------------

    private static void handle(ServerPlayer player, Session session, MenuAction action) {
        switch (action.kind()) {
            case REFRESH -> backend.update(player, Menu.title(session.state), Menu.render(session.state));
            case ERROR -> say(player, action.message());
            case CLOSE, SAVED, DENIED -> {
                SESSIONS.remove(player.getUUID());
                session.closingByUs = true;
                backend.close(player);
                say(player, action.message());
            }
            case TEXT_INPUT -> askText(player, session, action.request());
            default -> {
            }
        }
    }

    private static void askText(ServerPlayer player, Session session, TextRequest request) {
        UUID id = player.getUUID();
        session.awaitingText = true;
        session.closingByUs = true;
        backend.close(player);
        TextPrompt prompt = TextPrompt.of(request.prompt(), request.hint(), request.masked(),
            text -> validate(request, text),
            text -> answered(id, session, text),
            () -> cancelled(id, session));
        ModRuntime.engine.prompts().start(id, prompt);
    }

    /** {@code null} = fine. Never repeats the typed text. */
    private static String validate(TextRequest request, String text) {
        Setting<?> setting = ModRuntime.engine.registry().get(request.key());
        if (request.purpose() == TextRequest.Purpose.ADD_ITEM || setting == null) {
            return text == null || text.isBlank() ? "Nothing typed." : null;
        }
        try {
            setting.require(text == null ? "" : text.trim());
            return null;
        } catch (IllegalArgumentException ex) {
            return request.masked() ? "That value is not valid." : ex.getMessage();
        }
    }

    private static void answered(UUID id, Session session, String text) {
        ServerPlayer player = ModRuntime.onlinePlayer(id);
        if (player == null || SESSIONS.get(id) != session) {
            return;
        }
        session.awaitingText = false;
        MenuAction action = Menu.submitText(session.state, text, sender(player));
        if (action.kind() == MenuAction.Kind.ERROR) {
            say(player, action.message());
            action = Menu.cancelText(session.state, sender(player));
        }
        if (action.closesMenu()) { // permission lost meanwhile
            SESSIONS.remove(id);
            say(player, action.message());
            return;
        }
        backend.open(player, Menu.title(session.state), Menu.render(session.state));
    }

    private static void cancelled(UUID id, Session session) {
        ServerPlayer player = ModRuntime.onlinePlayer(id);
        if (player == null || SESSIONS.get(id) != session) {
            return;
        }
        session.awaitingText = false;
        MenuAction action = Menu.cancelText(session.state, sender(player));
        if (action.closesMenu()) {
            SESSIONS.remove(id);
            say(player, action.message());
            return;
        }
        backend.open(player, Menu.title(session.state), Menu.render(session.state));
    }

    private static void say(ServerPlayer player, String message) {
        if (message != null && !message.isEmpty()) {
            ModRuntime.engine.platform().sendMessage(player.getUUID(), message);
        }
    }

    /** Vanilla click types as names, so this class needs no vanilla enum. Anything unknown is OTHER (ignored by the model). */
    public static ClickType mapClick(String vanillaType, int button) {
        if (vanillaType == null) {
            return ClickType.OTHER;
        }
        return switch (vanillaType.toUpperCase(Locale.ROOT)) {
            case "PICKUP" -> button == 0 ? ClickType.LEFT : button == 1 ? ClickType.RIGHT : ClickType.OTHER;
            case "QUICK_MOVE" -> button == 0 ? ClickType.SHIFT_LEFT : button == 1 ? ClickType.SHIFT_RIGHT : ClickType.OTHER;
            case "CLONE" -> ClickType.MIDDLE;
            case "THROW" -> ClickType.DROP;
            default -> ClickType.OTHER;
        };
    }

    private static CommandSender sender(ServerPlayer player) {
        return new CommandSender() {
            @Override
            public String name() {
                return player.getName().getString();
            }

            @Override
            public UUID id() {
                return player.getUUID();
            }

            @Override
            public boolean has(String permission) {
                return ModRuntime.has(player, permission);
            }

            @Override
            public void send(String message) {
                say(player, message);
            }
        };
    }
}
