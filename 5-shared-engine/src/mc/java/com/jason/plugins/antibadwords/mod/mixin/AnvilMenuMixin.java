package com.jason.plugins.antibadwords.mod.mixin;

import com.jason.plugins.antibadwords.mod.ModRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks anvil renames that contain a blocked word (the plugin's PrepareAnvilEvent handler). */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow
    private String itemName;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void antibadwords$scan(CallbackInfo ci) {
        ItemCombinerMenuAccessor menu = (ItemCombinerMenuAccessor) (Object) this;
        ResultContainer result = menu.antibadwords$resultSlots();
        if (ModRuntime.engine != null && menu.antibadwords$player() instanceof ServerPlayer sp
            && ModRuntime.blockAnvil(sp, itemName, !result.getItem(0).isEmpty())) {
            result.setItem(0, ItemStack.EMPTY);
        }
    }
}
