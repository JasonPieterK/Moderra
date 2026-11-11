package com.jason.plugins.antibadwords.mod;

import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

/** Minecraft 26.3 and newer: the sign packet is a record with {@code lines()} returning a list. */
public final class SignLines {
    private SignLines() {
    }

    public static String[] of(ServerboundSignUpdatePacket packet) {
        return packet.lines().toArray(new String[0]);
    }
}
