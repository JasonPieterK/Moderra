package com.jason.plugins.moderra.engine.gui;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.gui.prompt.PromptKind;
import com.jason.plugins.moderra.engine.gui.prompt.TextPrompt;
import java.util.function.Consumer;

/**
 * Connects a {@code TEXT_INPUT} action of the menu to the engine's text prompts ({@code engine.prompts()}). A platform
 * calls {@link #start} when {@link Menu#click} returns {@code TEXT_INPUT}; the answer (chat line or sign text) is
 * validated, staged through {@link Menu#submitText} and the resulting {@link MenuAction} goes to {@code after} so the
 * platform can re-open or refresh the menu. Cancel, timeout and an empty sign go through {@link Menu#cancelText}.
 */
public final class MenuPrompts {
    private MenuPrompts() {
    }

    /**
     * @param after gets the result of the answer (REFRESH on success, DENIED when the permission went away, ...)
     * @return how the player is asked, or {@code null} when the viewer has no player id (console): nothing is asked and
     *         the request is cancelled
     */
    public static PromptKind start(Engine engine, MenuState state, CommandSender viewer, TextRequest request, Consumer<MenuAction> after) {
        if (viewer == null || viewer.id() == null) {
            after.accept(Menu.cancelText(state, viewer));
            return null;
        }
        TextPrompt prompt = TextPrompt.of(request.prompt(), request.hint(), request.masked(),
            text -> Menu.validateText(state, text),
            text -> after.accept(Menu.submitText(state, text, viewer)),
            () -> after.accept(Menu.cancelText(state, viewer)));
        return engine.prompts().start(viewer.id(), prompt);
    }
}
