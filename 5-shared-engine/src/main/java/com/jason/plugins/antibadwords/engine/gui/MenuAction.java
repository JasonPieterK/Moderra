package com.jason.plugins.antibadwords.engine.gui;

/**
 * What the platform must do after a click. {@code CLOSE}, {@code SAVED} and {@code DENIED} mean: close the inventory
 * (show {@code message} if present). {@code REFRESH}: call {@link Menu#render} again. {@code ERROR}: show the message,
 * keep the menu open. {@code TEXT_INPUT}: start a text prompt for {@code request}.
 */
public record MenuAction(Kind kind, String message, TextRequest request) {
    public enum Kind { NONE, REFRESH, CLOSE, SAVED, DENIED, ERROR, TEXT_INPUT }

    public static MenuAction none() {
        return new MenuAction(Kind.NONE, null, null);
    }

    public static MenuAction refresh() {
        return new MenuAction(Kind.REFRESH, null, null);
    }

    public static MenuAction close(String message) {
        return new MenuAction(Kind.CLOSE, message, null);
    }

    public static MenuAction saved(String message) {
        return new MenuAction(Kind.SAVED, message, null);
    }

    public static MenuAction denied() {
        return new MenuAction(Kind.DENIED, "You do not have permission to use the AntiBadWords menu.", null);
    }

    public static MenuAction error(String message) {
        return new MenuAction(Kind.ERROR, message, null);
    }

    public static MenuAction text(TextRequest request) {
        return new MenuAction(Kind.TEXT_INPUT, null, request);
    }

    public boolean closesMenu() {
        return kind == Kind.CLOSE || kind == Kind.SAVED || kind == Kind.DENIED;
    }
}
