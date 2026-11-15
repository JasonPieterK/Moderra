package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;

/**
 * Settings of the PlaceholderAPI and LuckPerms hooks ({@code integrations.yml}, category "Integrations"). The hooks exist on the
 * Bukkit family only; the keys are read everywhere but do nothing on Fabric and Forge (there LuckPerms is reached through the
 * permission nodes the loaders already use).
 */
public final class PapiLuckPermsSettings {
    public static final String FILE = "integrations.yml";
    private static final String CATEGORY = "Integrations";
    private static final String P = "integrations.placeholderapi.";
    private static final String L = "integrations.luckperms.";

    public static final Setting<Boolean> PAPI_ENABLED = s(Setting.bool(P + "enabled", true).title("PlaceholderAPI expansion")
        .description("BUKKIT FAMILY ONLY, needs PlaceholderAPI. Register the expansion \"antibadwords\" with the placeholders\n"
            + "%antibadwords_strikes%, _strikes_next%, _next_step%, _muted%, _mute_remaining%, _mute_reason%, _profile%,\n"
            + "_violations_total% and _protection_level%. They never contain chat text, word lists or secrets.\n"
            + "Without PlaceholderAPI this does nothing. Takes effect after a server restart."));
    public static final Setting<Boolean> PAPI_PARSE = s(Setting.bool(P + "parse-in-messages", false).title("Use PlaceholderAPI in our messages")
        .description("BUKKIT FAMILY ONLY, needs PlaceholderAPI. Replace %placeholders% of OTHER plugins in the messages this plugin\n"
            + "sends to one player. Off by default: a text a player typed (a mute reason, a blocked word) could contain a\n"
            + "%placeholder% and would be replaced too. Turn on only if you accept that. Broadcasts are never parsed."));

    public static final Setting<Boolean> LP_ENABLED = s(Setting.bool(L + "enabled", true).title("LuckPerms hook")
        .description("BUKKIT FAMILY ONLY, needs LuckPerms. Use LuckPerms groups and meta for the rule profile and check permissions\n"
            + "of offline players. Without LuckPerms this does nothing. Takes effect after a server restart."));
    public static final Setting<Boolean> LP_USE_META = s(Setting.bool(L + "use-meta", true).title("Profile from LuckPerms meta")
        .description("When a player (or one of their groups) has the LuckPerms meta value named below, it is the rule profile.\n"
            + "Set it with: /lp group vip meta set antibadwords-profile relaxed. Beats the group map below, not\n"
            + "the permission antibadwords.profile.<name>. Context aware (per server / world contexts of LuckPerms)."));
    public static final Setting<String> LP_META_KEY = s(Setting.string(L + "meta-key", "antibadwords-profile").title("LuckPerms meta key")
        .description("The meta key that holds a profile name from worlds.yml."));
    public static final Setting<Boolean> LP_PRIMARY_ONLY = s(Setting.bool(L + "primary-group-only", false).title("Only the primary group counts")
        .description("false: a player counts as being in a group when they inherit it. true: only the primary group is compared."));
    public static final Setting<Boolean> LP_OFFLINE = s(Setting.bool(L + "offline-permission-checks", true).title("Check offline players")
        .description("Use LuckPerms to see whether an OFFLINE player has a permission such as antibadwords.bypass.mute (for example\n"
            + "to warn when you mute an exempt player). The data is loaded in the background; the server is not stopped."));

    public static final Section GROUP_PROFILES = new Section(L + "group-profiles", FILE, CATEGORY, "Profile by LuckPerms group",
        "Which rule profile (see worlds.yml) a LuckPerms group gets: group name on the left, profile name on the right.\n"
            + "The first line that matches a player wins; the order of the file counts. Order of choosing a profile:\n"
            + "permission antibadwords.profile.<name>, then LuckPerms meta, then this map, then the world rules, then the default.\n"
            + "Example:\n"
            + "  group-profiles:\n"
            + "    vip: relaxed\n"
            + "    newbie: strict\n"
            + "A profile name that does not exist in worlds.yml is reported at start and ignored.",
        "{}\n", false);

    private PapiLuckPermsSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry r) {
        List.of(PAPI_ENABLED, PAPI_PARSE, LP_ENABLED, LP_USE_META, LP_META_KEY, LP_PRIMARY_ONLY, LP_OFFLINE).forEach(r::register);
        r.register(GROUP_PROFILES);
    }
}
