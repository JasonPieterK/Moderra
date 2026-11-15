package com.jason.plugins.antibadwords.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignTextSlot;

/** Minecraft 26.3 and newer: the sign packet is a record with {@code lines()} returning a list. */
public final class SignLines {
    private SignLines() {
    }

    public static String[] of(ServerboundSignUpdatePacket packet) {
        return packet.lines().toArray(new String[0]);
    }

    /** True when the packet edits the back side of the sign. */
    public static boolean isBack(ServerboundSignUpdatePacket packet) {
        return packet.slot().toString().equalsIgnoreCase("back");
    }

    public static BlockPos pos(ServerboundSignUpdatePacket packet) {
        return packet.pos();
    }

    /** The packet that opens the editor of the front side of the sign at {@code pos}. */
    public static Packet<?> openEditor(BlockPos pos) {
        return new ClientboundOpenSignEditorPacket(pos, SignTextSlot.FRONT);
    }
}
