package com.jason.plugins.antibadwords.fabric;

import com.jason.plugins.antibadwords.engine.integrations.PlaceholderValues;
import com.jason.plugins.antibadwords.mod.ModRuntime;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.resources.Identifier;

/**
 * Variant "api": registers {@code %antibadwords:<name>%} with the Fabric "Text Placeholder API" (eu.pb4:placeholder-api).
 * The values come from {@code engine.placeholders()} (the same ones as the Bukkit PlaceholderAPI expansion). This class is only
 * loaded when the mod {@code placeholder-api} is installed (see AntiBadWordsFabric), so the mod runs without it.
 */
final class FabricPlaceholders {
    private FabricPlaceholders() {
    }

    static void register() {
        for (String name : PlaceholderValues.NAMES) {
            Placeholders.registerCommon(Identifier.fromNamespaceAndPath("antibadwords", name), (context, argument) -> {
                if (ModRuntime.engine == null) {
                    return PlaceholderResult.invalid("not ready");
                }
                String value = ModRuntime.engine.placeholders().resolve(context.hasPlayer() ? context.player().getUUID() : null, name);
                return value == null ? PlaceholderResult.invalid("unknown placeholder") : PlaceholderResult.value(value);
            });
        }
    }
}
