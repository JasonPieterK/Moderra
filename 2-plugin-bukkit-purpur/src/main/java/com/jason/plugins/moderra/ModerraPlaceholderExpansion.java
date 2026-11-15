package com.jason.plugins.moderra;

import com.jason.plugins.moderra.engine.Engine;
import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * The PlaceholderAPI expansion {@code moderra}. It is compiled against a name-only shim of PlaceholderExpansion
 * ({@code src/papi-shim}, not in the jar) and is only ever loaded by {@link PlaceholderApiHook} after PlaceholderAPI was found.
 * All values come from the engine ({@code PlaceholderValues}); nothing here reads chat text or secrets.
 */
final class ModerraPlaceholderExpansion extends PlaceholderExpansion {
    private final Engine engine;
    private final String version;
    private final String author;

    ModerraPlaceholderExpansion(Engine engine, String version, List<String> authors) {
        this.engine = engine;
        this.version = version == null ? "1" : version;
        this.author = authors.isEmpty() ? "Moderra" : String.join(", ", authors);
    }

    @Override
    public String getIdentifier() {
        return "moderra";
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getVersion() {
        return version;
    }

    /** Stays registered when PlaceholderAPI is reloaded ({@code /papi reload}). */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            return engine.placeholders().resolve(player == null ? null : player.getUniqueId(), params);
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
