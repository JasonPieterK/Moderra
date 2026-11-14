package com.jason.plugins.antibadwords.engine.gui;

/**
 * The menu asks the platform to collect typed text (sign or chat prompt, built later). The answer goes to
 * {@link Menu#submitText}; a cancelled prompt goes to {@link Menu#cancelText}.
 *
 * @param key     the setting being edited
 * @param purpose whether the text becomes the new value or a new list item
 * @param prompt  what to tell the player
 * @param hint    the allowed values in words
 * @param masked  true for secrets: the prompt must never echo what is typed
 */
public record TextRequest(String key, Purpose purpose, String prompt, String hint, boolean masked) {
    public enum Purpose { SET_VALUE, ADD_ITEM, SEARCH, ADD_CUSTOM_WORD }
}
