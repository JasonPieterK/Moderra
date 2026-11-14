package com.jason.plugins.antibadwords.engine.gui;

import java.util.Map;

/**
 * One screen of the menu: a title and the entries by slot number (0-44 content, 45-53 the navigation row).
 *
 * @param pageIndex zero-based page shown (already clamped)
 * @param pageCount number of pages of this view (at least 1)
 */
public record Page(String title, Map<Integer, Entry> slots, int pageIndex, int pageCount) {
    public static final int CONTENT_SLOTS = 45;
    public static final int SIZE = 54;

    public static final int BACK = 45;
    public static final int PREV = 46;
    public static final int ADD = 47;
    public static final int CANCEL = 48;
    public static final int INFO = 49;
    public static final int DONE = 50;
    public static final int RESET = 51;
    public static final int NEXT = 52;
    public static final int HOME = 53;
}
