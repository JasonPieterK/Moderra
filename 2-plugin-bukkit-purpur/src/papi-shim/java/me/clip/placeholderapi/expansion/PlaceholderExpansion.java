package me.clip.placeholderapi.expansion;

import org.bukkit.OfflinePlayer;

/**
 * COMPILE-TIME SHIM, not part of the plugin jar (the maven-jar-plugin excludes {@code me/**}). It only has the names of the
 * PlaceholderAPI methods used by {@code ModerraPlaceholderExpansion}, so that class compiles without PlaceholderAPI's repository.
 * At runtime the real PlaceholderExpansion of the installed PlaceholderAPI is used; it is only loaded when PlaceholderAPI is present.
 */
public abstract class PlaceholderExpansion {
    public abstract String getIdentifier();

    public abstract String getAuthor();

    public abstract String getVersion();

    public boolean persist() {
        return false;
    }

    public boolean canRegister() {
        return true;
    }

    public boolean register() {
        return true;
    }

    public boolean unregister() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        return null;
    }
}
