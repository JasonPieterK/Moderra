package com.jason.plugins.antibadwords.mod;

/**
 * Implemented by {@code AnvilMenuMixin} on the vanilla {@code AnvilMenu}: gives the text the player typed into the rename
 * field. It lives outside the mixin package because a mixin package class must never be loaded directly.
 */
public interface AnvilNameAccess {
    /** The current rename text; {@code null} before the player typed anything. */
    String antibadwords$itemName();
}
