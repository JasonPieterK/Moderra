package com.jason.plugins.antibadwords.mod.mixin;

import com.jason.plugins.antibadwords.mod.ModRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forgets the "already punished for this anvil text" flag when the anvil is closed (InventoryCloseEvent). */
@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void antibadwords$closed(Player player, CallbackInfo ci) {
        if ((Object) this instanceof AnvilMenu) {
            ModRuntime.anvilClosed(player.getUUID());
        }
    }
}
