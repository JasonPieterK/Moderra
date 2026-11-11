package com.jason.plugins.antibadwords.mod.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads two protected fields of the anvil's parent class (a Mixin cannot shadow fields it does not declare). */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
    @Accessor("player")
    Player antibadwords$player();

    @Accessor("resultSlots")
    ResultContainer antibadwords$resultSlots();
}
