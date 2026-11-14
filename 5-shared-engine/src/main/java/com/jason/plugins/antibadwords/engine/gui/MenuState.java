package com.jason.plugins.antibadwords.engine.gui;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.config.Setting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one open menu remembers: the navigation stack, the staged (unsaved) values and a pending text request.
 * One instance per viewer. Not thread safe: call it from one thread (the server thread) only.
 */
public final class MenuState {
    /** Kinds of screens. */
    public enum Kind {
        MAIN, CATEGORY, LIST, CONFIRM,
        /** Special pages (engine required): languages, word-list index, one word list, ladder, mutes, profiles, search results. */
        LANGUAGES, SOURCES, WORDS, LADDER, MUTES, PROFILES, SEARCH
    }

    /** One entry of the navigation stack. {@code target} is the category name or the list setting's key. */
    public record View(Kind kind, String target, int page) {
        View withPage(int p) {
            return new View(kind, target, p);
        }
    }

    private final ConfigAccess access;
    private final List<View> stack = new ArrayList<>();
    private final Map<Setting<?>, Object> staged = new LinkedHashMap<>();
    private TextRequest pending;
    private CommandSender viewer;

    MenuState(ConfigAccess access) {
        this.access = access;
        stack.add(new View(Kind.MAIN, "", 0));
    }

    /** The viewer of the call being handled (set by {@link Menu} after the permission check, for handlers that need it). */
    CommandSender viewer() {
        return viewer;
    }

    void setViewer(CommandSender viewer) {
        this.viewer = viewer;
    }

    ConfigAccess access() {
        return access;
    }

    public View current() {
        return stack.get(stack.size() - 1);
    }

    /** Depth of the navigation stack (1 = main menu). */
    public int depth() {
        return stack.size();
    }

    void push(View view) {
        stack.add(view);
    }

    boolean pop() {
        if (stack.size() <= 1) {
            return false;
        }
        stack.remove(stack.size() - 1);
        return true;
    }

    void home() {
        View main = stack.get(0);
        stack.clear();
        stack.add(main);
    }

    void setPage(int page) {
        stack.set(stack.size() - 1, current().withPage(page));
    }

    /** True when something was changed and not yet saved. */
    public boolean hasUnsavedChanges() {
        return !staged.isEmpty();
    }

    /** Number of settings with a staged value. */
    public int stagedCount() {
        return staged.size();
    }

    public boolean isStaged(Setting<?> setting) {
        return staged.containsKey(setting);
    }

    /** The staged value when there is one, else the loaded value. */
    public Object value(Setting<?> setting) {
        return staged.containsKey(setting) ? staged.get(setting) : access.current(setting);
    }

    /** Stages a value; staging the loaded value again removes the change. @return true when the staged map changed */
    boolean stage(Setting<?> setting, Object value) {
        if (value.equals(access.current(setting))) {
            return staged.remove(setting) != null;
        }
        Object old = staged.put(setting, value);
        return !value.equals(old);
    }

    Map<Setting<?>, Object> staged() {
        return staged;
    }

    void discard() {
        staged.clear();
        pending = null;
    }

    /** The text request waiting for an answer, or {@code null}. */
    public TextRequest pendingText() {
        return pending;
    }

    void setPending(TextRequest request) {
        pending = request;
    }
}
