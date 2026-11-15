package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;

/** Settings of the packet-library, CoreProtect and Plan integrations ({@code integrations.yml}). Bukkit family only; all optional. */
public final class ExtraIntegrationSettings {
    public static final String FILE = "integrations.yml";
    private static final String CATEGORY = "Integrations";
    private static final String BUKKIT = "BUKKIT FAMILY ONLY. ";

    public static final String LOG_AS_CHAT = "chat";
    public static final String LOG_AS_COMMAND = "command";

    public static final Setting<Boolean> PROTOCOLLIB = s(Setting.bool("integrations.protocollib.enabled", true).title("ProtocolLib packet scan allowed")
        .description(BUKKIT + "Allows the packet-level scan of anvil renames, sign edits and book edits through ProtocolLib. It is only used when\n"
            + "the Bukkit events for these are missing (see integrations.packet-scan.only-when-events-missing). Needs a restart."));
    public static final Setting<Boolean> PACKETEVENTS = s(Setting.bool("integrations.packetevents.enabled", true).title("PacketEvents packet scan allowed")
        .description(BUKKIT + "Same as the ProtocolLib switch, for PacketEvents. ProtocolLib is preferred when both are installed. Needs a restart."));
    public static final Setting<Boolean> PACKET_ONLY_WHEN_MISSING = s(Setting.bool("integrations.packet-scan.only-when-events-missing", true)
        .title("Packet scan only when events are missing")
        .description(BUKKIT + "true = the packet scan starts only on a server where the Bukkit events (PrepareAnvilEvent, SignChangeEvent,\n"
            + "PlayerEditBookEvent) do not exist. false = also run it next to the events (every text is then checked twice; not recommended)."));
    public static final Setting<Boolean> COREPROTECT = s(Setting.bool("integrations.coreprotect.enabled", true).title("CoreProtect log on")
        .description(BUKKIT + "Blocked or censored sign and book edits are also written to the CoreProtect log (\"/co lookup\"), when CoreProtect\n"
            + "is installed and its API has a chat or command log method. Needs a restart."));
    public static final Setting<String> COREPROTECT_LOG_AS = s(Setting.choice("integrations.coreprotect.log-as", LOG_AS_CHAT, LOG_AS_CHAT,
        LOG_AS_COMMAND).title("CoreProtect record type")
        .description("Which CoreProtect log the record goes to: chat (/co lookup action:chat) or command. Falls back to the other when missing."));
    public static final Setting<Boolean> COREPROTECT_TEXT = s(Setting.bool("integrations.coreprotect.include-text", true).title("Text in the CoreProtect record")
        .description("true = the blocked text is part of the record (shortened to 200 characters). false = only the kind and the player."));
    public static final Setting<List<String>> COREPROTECT_SOURCES = s(Setting.list("integrations.coreprotect.sources", List.of("sign", "sign-back", "book"))
        .title("Sources logged to CoreProtect")
        .description("Text sources whose blocked edits are logged: sign, sign-back, book, anvil."));
    public static final Setting<Boolean> PLAN = s(Setting.bool("integrations.plan.enabled", true).title("Plan hook on")
        .description(BUKKIT + "Reserved. Plan (Player Analytics) data extensions need the Plan API, which is not available to this build, so nothing is\n"
            + "exposed yet. The violation counts are available through PlaceholderAPI (%antibadwords_violations_total%)."));

    private ExtraIntegrationSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry r) {
        List.of(PROTOCOLLIB, PACKETEVENTS, PACKET_ONLY_WHEN_MISSING, COREPROTECT, COREPROTECT_LOG_AS, COREPROTECT_TEXT, COREPROTECT_SOURCES, PLAN)
            .forEach(r::register);
        OutgoingScanSettings.register(r);
    }
}
