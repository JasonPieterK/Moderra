package com.jason.plugins.antibadwords.forge;

import net.minecraftforge.fml.ModList;

/** Minecraft 26.1 and newer: {@code ModList.isLoaded(id)} is a static method (the dumps show no {@code get()} there). */
final class ForgeMods {
    private ForgeMods() {
    }

    static boolean isLoaded(String id) {
        return ModList.isLoaded(id);
    }
}
