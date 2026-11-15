package com.jason.plugins.antibadwords.mod.mixin;

import com.jason.plugins.antibadwords.mod.ModRuntime;
import com.jason.plugins.antibadwords.mod.SignLines;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scans signs and written books (the plugin's SignChangeEvent / PlayerEditBookEvent handlers). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "updateSignText", at = @At("HEAD"), cancellable = true)
    private void antibadwords$sign(ServerboundSignUpdatePacket packet, List<FilteredText> filtered, CallbackInfo ci) {
        if (ModRuntime.engine != null && ModRuntime.blockSign(player, SignLines.of(packet), SignLines.isBack(packet))) {
            ci.cancel();
        }
    }

    /** A player sets the command of a command block (scanned like a command-block run). */
    @Inject(method = "handleSetCommandBlock", at = @At("HEAD"), cancellable = true)
    private void antibadwords$commandBlock(ServerboundSetCommandBlockPacket packet, CallbackInfo ci) {
        if (ModRuntime.engine == null || packet.getCommand() == null || packet.getCommand().isEmpty()) {
            return;
        }
        var pos = packet.getPos();
        String where = player.getName().getString() + " set " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        if (ModRuntime.blockCommandBlock(player.level().dimension().identifier().toString(), where, packet.getCommand())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleEditBook", at = @At("HEAD"), cancellable = true)
    private void antibadwords$book(ServerboundEditBookPacket packet, CallbackInfo ci) {
        if (ModRuntime.engine != null && ModRuntime.blockBook(player, packet.pages(), packet.title().orElse(null))) {
            ci.cancel();
        }
    }
}
