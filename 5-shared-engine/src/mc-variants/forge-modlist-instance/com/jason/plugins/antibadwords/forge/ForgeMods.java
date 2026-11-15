package com.jason.plugins.antibadwords.forge;

import net.minecraftforge.fml.ModList;

/** Minecraft 1.21.11: {@code ModList.get().isLoaded(id)} (instance method). */
final class ForgeMods {
    private ForgeMods() {
    }

    static boolean isLoaded(String id) {
        return ModList.get().isLoaded(id);
    }
}
