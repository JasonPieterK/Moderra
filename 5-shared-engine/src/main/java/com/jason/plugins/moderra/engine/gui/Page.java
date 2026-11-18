package com.jason.plugins.moderra.engine.gui;

import java.util.Map;

/**
 * One screen of the menu: a title and the entries by slot number (0-44 content, 45-53 the navigation row).
 *
 * @param pageIndex zero-based page shown (already clamped)
 * @param pageCount number of pages of this view (at least 1)
 */
public record Page(String title, Map<Integer, Entry> slots, int pageIndex, int pageCount) {
    /** Entries per page: 5 rows of 7 (the outer columns stay free so the grid looks centred). */
    public static final int CONTENT_SLOTS = 35;
    private static final int PER_ROW = 7;
    private static final int ROWS = 5;
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

    /**
     * Where the {@code i}-th of {@code count} entries of a page goes: rows of 7, every row centred horizontally and the
     * block of rows centred vertically in the 5 content rows (a short last row is centred too).
     */
    public static int contentSlot(int i, int count) {
        int rows = Math.max(1, (count + PER_ROW - 1) / PER_ROW);
        int top = (ROWS - Math.min(rows, ROWS)) / 2;
        int row = i / PER_ROW;
        int inRow = Math.min(PER_ROW, count - row * PER_ROW);
        int left = (9 - inRow) / 2;
        return (top + row) * 9 + left + i % PER_ROW;
    }
}
