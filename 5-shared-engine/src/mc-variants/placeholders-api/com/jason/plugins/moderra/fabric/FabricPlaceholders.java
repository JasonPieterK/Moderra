package com.jason.plugins.moderra.fabric;

import com.jason.plugins.moderra.engine.integrations.PlaceholderValues;
import com.jason.plugins.moderra.mod.ModRuntime;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.resources.Identifier;

/**
 * Variant "api": registers {@code %moderra:<name>%} with the Fabric "Text Placeholder API" (eu.pb4:placeholder-api).
 * The values come from {@code engine.placeholders()} (the same ones as the Bukkit PlaceholderAPI expansion). This class is only
 * loaded when the mod {@code placeholder-api} is installed (see ModerraFabric), so the mod runs without it.
 */
final class FabricPlaceholders {
    private FabricPlaceholders() {
    }

    static void register() {
        for (String name : PlaceholderValues.NAMES) {
            Placeholders.registerCommon(Identifier.fromNamespaceAndPath("moderra", name), (context, argument) -> {
                if (ModRuntime.engine == null) {
                    return PlaceholderResult.invalid("not ready");
                }
                String value = ModRuntime.engine.placeholders().resolve(context.hasPlayer() ? context.player().getUUID() : null, name);
                return value == null ? PlaceholderResult.invalid("unknown placeholder") : PlaceholderResult.value(value);
            });
        }
    }
}
