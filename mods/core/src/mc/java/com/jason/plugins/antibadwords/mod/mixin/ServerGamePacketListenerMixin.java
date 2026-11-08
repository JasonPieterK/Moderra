package com.jason.plugins.antibadwords.mod.mixin;

import com.jason.plugins.antibadwords.mod.ModRuntime;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
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
        if (ModRuntime.core != null && ModRuntime.blockSign(player, packet.getLines())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleEditBook", at = @At("HEAD"), cancellable = true)
    private void antibadwords$book(ServerboundEditBookPacket packet, CallbackInfo ci) {
        if (ModRuntime.core != null && ModRuntime.blockBook(player, packet.pages(), packet.title().orElse(null))) {
            ci.cancel();
        }
    }
}
