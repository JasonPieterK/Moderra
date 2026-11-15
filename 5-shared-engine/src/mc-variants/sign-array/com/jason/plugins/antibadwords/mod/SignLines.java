package com.jason.plugins.antibadwords.mod;

import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

/** Minecraft 26.2 and older (also 1.21.x): the sign packet has {@code getLines()} returning an array. */
public final class SignLines {
    private SignLines() {
    }

    public static String[] of(ServerboundSignUpdatePacket packet) {
        return packet.getLines();
    }

    /** True when the packet edits the back side of the sign. */
    public static boolean isBack(ServerboundSignUpdatePacket packet) {
        return !packet.isFrontText();
    }
}
