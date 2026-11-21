package com.jason.plugins.moderra.banner;

import java.util.List;

/**
 * Everything the banner shows, gathered once on the main thread so rendering needs no Bukkit access.
 *
 * @param version         plugin version
 * @param platform        server software label (Paper, Purpur, Spigot...)
 * @param minecraft       Minecraft version
 * @param java            Java runtime version
 * @param languagesOn     word-list languages that are switched on ("custom" not counted)
 * @param languagesTotal  word-list languages that have a file ("custom" not counted)
 * @param words           blocked words in the active lists at the current protection level
 * @param profiles        world profiles (worlds.yml)
 * @param startupMs       time Moderra needed to enable
 * @param integrations    soft-dependency hooks, {@code ok} = hook is live (or the plugin is present, when there is no hook object)
 * @param configProblems  problems found while loading the config folder
 * @param discord         short Discord alert status text, e.g. "webhooks on"
 * @param discordOn       true when Discord alerts are delivered
 */
public record BannerData(String version, String platform, String minecraft, String java,
                         int languagesOn, int languagesTotal, int words, int profiles, long startupMs,
                         List<Hook> integrations, int configProblems, String discord, boolean discordOn) {

    /** One integration hook and whether it is live. */
    public record Hook(String name, boolean ok) {}
}
