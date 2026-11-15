package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import com.jason.plugins.moderra.engine.network.NetworkSettings;

/**
 * The one entry point of all settings in {@code integrations.yml}: one block per plugin or feature, in a fixed order.
 * The constants live in the per-feature holders (EssentialsSettings, BanBedrockSettings, PapiLuckPermsSettings,
 * ExtraIntegrationSettings, PluginCommandSettings, NameplateSettings, ChatChannels, NetworkSettings).
 */
public final class IntegrationSettings {
    public static final String FILE = "integrations.yml";

    private IntegrationSettings() {
    }

    public static void register(SettingsRegistry registry) {
        registry.fileHeader(FILE, "Moderra - integrations with other plugins\n"
            + "Everything here is optional: it only does something when the other plugin is installed. A plugin that is not\n"
            + "installed is simply skipped. Reload with /moderra reload (switches that say \"restart\" need a server restart).\n"
            + "Bukkit family only (Spigot, Paper, Purpur) unless a section says otherwise; Fabric and Forge ignore those parts.");
        EssentialsSettings.register(registry);
        BanBedrockSettings.register(registry);
        PapiLuckPermsSettings.register(registry);
        ExtraIntegrationSettings.register(registry);
        PluginCommandSettings.register(registry);
        NameplateSettings.register(registry);
        ChatChannels.register(registry);
        NetworkSettings.register(registry);
    }
}
