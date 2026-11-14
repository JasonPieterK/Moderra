package com.jason.plugins.antibadwords.engine.gui;

/** The mouse/keyboard action a viewer made on a slot. Platforms translate their own click events into these. */
public enum ClickType {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    /** Middle mouse button (creative pick). With {@link #DROP} it resets a setting to its default. */
    MIDDLE,
    /** The drop key (Q). */
    DROP,
    /** Anything else (number keys, double click, drag ...). Never changes anything. */
    OTHER;

    public boolean isShift() {
        return this == SHIFT_LEFT || this == SHIFT_RIGHT;
    }

    public boolean isLeft() {
        return this == LEFT || this == SHIFT_LEFT;
    }

    public boolean isRight() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }

    public boolean isReset() {
        return this == MIDDLE || this == DROP;
    }
}
