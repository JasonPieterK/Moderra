package com.jason.plugins.moderra.mod.mixin;

import com.jason.plugins.moderra.mod.AnvilNameAccess;
import com.jason.plugins.moderra.mod.ModRuntime;
import com.jason.plugins.moderra.mod.gui.GuiAnvilMenu;
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
public abstract class AnvilMenuMixin implements AnvilNameAccess {
    @Shadow
    private String itemName;

    @Override
    public String moderra$itemName() {
        return itemName;
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void moderra$scan(CallbackInfo ci) {
        if ((Object) this instanceof GuiAnvilMenu) {
            return; // the text prompt of the menu: the prompt checks its text, it is not an anvil rename
        }
        ItemCombinerMenuAccessor menu = (ItemCombinerMenuAccessor) (Object) this;
        ResultContainer result = menu.moderra$resultSlots();
        if (ModRuntime.engine != null && menu.moderra$player() instanceof ServerPlayer sp
            && ModRuntime.blockAnvil(sp, itemName, !result.getItem(0).isEmpty())) {
            result.setItem(0, ItemStack.EMPTY);
        }
    }
}
