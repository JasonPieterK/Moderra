package com.jason.plugins.antibadwords.engine.network;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;

/** Settings of the network sync ({@code integrations.yml}, keys under {@code integrations.network.}). */
public final class NetworkSettings {
    public static final String FILE = "integrations.yml";
    public static final String CATEGORY = "Integrations";
    public static final String OFF = "off";
    public static final String SHARED_FOLDER = "shared-folder";
    public static final String PLUGIN_MESSAGING = "plugin-messaging";
    /** The plugin messaging channel of the proxy relay. */
    public static final String CHANNEL = "antibadwords:sync";

    private static final String N = "integrations.network.";

    public static final Setting<String> MODE = s(Setting.choice(N + "mode", OFF, OFF, SHARED_FOLDER, PLUGIN_MESSAGING)
        .title("Network sync mode")
        .description("One shared mute list and strike count for several servers of a network.\n"
            + "off = every server keeps its own (default).\n"
            + "shared-folder = every server writes its changes to its own journal file in the folder below and reads the files of the others;\n"
            + "  all servers need to see the same folder (same machine or a network share). No proxy plugin needed.\n"
            + "plugin-messaging = changes travel over the plugin messaging channel " + CHANNEL + " through a proxy relay.\n"
            + "  This needs a loader that provides the transport; when it does not, a warning is logged and nothing is synced."));
    public static final Setting<String> FOLDER = s(Setting.string(N + "folder", "../abw-network")
        .title("Shared folder")
        .description("Mode shared-folder: the folder all servers share. A relative path is relative to this plugin's config folder. It is created when missing."));
    public static final Setting<String> SERVER_ID = s(Setting.string(N + "server-id", "")
        .title("Server id")
        .description("A name for this server, unique in the network (letters, digits, - and _). Empty = made from the config folder path.\n"
            + "Do not change it later: the other servers remember how far they have read this server's journal."));
    public static final Setting<Duration> POLL = s(Setting.duration(N + "poll-interval", Duration.ofSeconds(2))
        .title("How often to look for changes")
        .description("Mode shared-folder: how often the journals of the other servers are read."));
    public static final Setting<Boolean> SYNC_MUTES = s(Setting.bool(N + "sync-mutes", true)
        .title("Share mutes")
        .description("A mute or unmute on one server applies on all. Conflict rule: the newest change of a player wins (clock time, the larger server id wins a tie)."));
    public static final Setting<Boolean> SYNC_STRIKES = s(Setting.bool(N + "sync-strikes", true)
        .title("Share strikes")
        .description("Strike points add up over the whole network, and a reset (for example after a punishment) resets everywhere.\n"
            + "Conflict rule: points are added, never overwritten; points older than the newest reset of that player are dropped."));

    private NetworkSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry registry) {
        for (Setting<?> setting : new Setting<?>[] {MODE, FOLDER, SERVER_ID, POLL, SYNC_MUTES, SYNC_STRIKES}) {
            registry.register(setting);
        }
    }
}
