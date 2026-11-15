package com.jason.plugins.antibadwords.mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Text prompt with a fake sign: the player sees a sign block two blocks above the feet (block update + block entity data
 * packet, only for that player), the sign editor opens on it, and the answer is caught in the sign mixin. The real
 * block is sent again afterwards. Nothing changes in the world on the server.
 */
public final class FakeSign {
    private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();

    private FakeSign() {
    }

    public static void open(ServerPlayer player) {
        restore(player);
        Level level = player.level();
        BlockPos pos = player.blockPosition().above(2).immutable();
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        BlockEntity entity = new SignBlockEntity(pos, state);
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        player.connection.send(ClientboundBlockEntityDataPacket.create(entity, (be, registries) -> new CompoundTag()));
        player.connection.send(SignLines.openEditor(pos));
        OPEN.put(player.getUUID(), pos);
    }

    /** True when {@code pos} is the fake sign of this player; the real block is then sent again and the edit must be dropped. */
    public static boolean answered(ServerPlayer player, BlockPos pos) {
        BlockPos fake = OPEN.get(player.getUUID());
        if (fake == null || !fake.equals(pos)) {
            return false;
        }
        restore(player);
        return true;
    }

    /** Sends the real block (and its block entity data) at the fake position again. Safe to call when nothing is open. */
    public static void restore(ServerPlayer player) {
        BlockPos pos = OPEN.remove(player.getUUID());
        if (pos == null) {
            return;
        }
        Level level = player.level();
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity real = level.getBlockEntity(pos);
        if (real != null) {
            Packet<?> data = real.getUpdatePacket();
            if (data != null) {
                player.connection.send(data);
            }
        }
    }
}
