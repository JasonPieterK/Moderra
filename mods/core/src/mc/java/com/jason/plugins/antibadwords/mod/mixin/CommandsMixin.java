package com.jason.plugins.antibadwords.mod.mixin;

import com.jason.plugins.antibadwords.mod.ModRuntime;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scans every command a player runs (the plugin's PlayerCommandPreprocessEvent handler). */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void antibadwords$scan(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (ModRuntime.core != null && ModRuntime.blockCommand(parse.getContext().getSource(), command)) {
            ci.cancel();
        }
    }
}
