package com.jason.plugins.moderra.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
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

    public static BlockPos pos(ServerboundSignUpdatePacket packet) {
        return packet.getPos();
    }

    /** The packet that opens the editor of the front side of the sign at {@code pos}. */
    public static Packet<?> openEditor(BlockPos pos) {
        return new ClientboundOpenSignEditorPacket(pos, true);
    }
}
