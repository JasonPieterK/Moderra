package com.jason.plugins.moderra.mod.mixin;

import com.jason.plugins.moderra.mod.ModRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Command blocks and command-block minecarts about to run their command (filters.scan.command-blocks). */
@Mixin(BaseCommandBlock.class)
public abstract class BaseCommandBlockMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void moderra$run(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (ModRuntime.engine == null) {
            return;
        }
        String command = ((BaseCommandBlock) (Object) this).getCommand();
        String dimension = level.dimension().identifier().toString();
        if (command != null && !command.isEmpty() && ModRuntime.blockCommandBlock(dimension, "running in " + dimension, command)) {
            cir.setReturnValue(false);
        }
    }
}
