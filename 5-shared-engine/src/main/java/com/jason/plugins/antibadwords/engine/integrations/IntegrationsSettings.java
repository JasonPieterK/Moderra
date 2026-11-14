package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;

/**
 * Settings of the ban-plugin and Bedrock integrations ({@code integrations.yml}, keys under {@code integrations.bans.}
 * and {@code integrations.bedrock.}).
 */
public final class IntegrationsSettings {
    public static final String FILE = "integrations.yml";
    public static final String CATEGORY = "Integrations";

    public static final String AUTO = "auto";
    public static final String VANILLA = "vanilla";
    public static final String ESSENTIALS = "essentials";
    public static final String LITEBANS = "litebans";
    public static final String ADVANCEDBAN = "advancedban";
    public static final String LIBERTYBANS = "libertybans";
    public static final String CUSTOM = "custom";

    public static final String FALLBACK_KICK = "kick";
    public static final String FALLBACK_MUTE = "mute";
    public static final String FALLBACK_NONE = "none";

    public static final String MODE_ON = "on";
    public static final String MODE_OFF = "off";

    private static final String B = "integrations.bans.";
    private static final String T = "integrations.bedrock.";
    private static final String PLACEHOLDERS = "Placeholders: {player} {uuid} {reason} {duration} (written the way the provider reads it), "
        + "{seconds}, {millis}. Empty = this provider cannot do it (the fallback action runs).";

    public static final Setting<String> PROVIDER = s(Setting.choice(B + "provider", AUTO, AUTO, VANILLA, ESSENTIALS, LITEBANS, ADVANCEDBAN,
        LIBERTYBANS, CUSTOM).title("Ban plugin")
        .description("Which ban plugin runs the ladder actions \"tempban <time> [reason]\" and \"ipban [reason]\" (console commands, templates below).\n"
            + "auto = the first of LiteBans, AdvancedBan, LibertyBans, EssentialsX that is installed (Bukkit family only; on Fabric and Forge\n"
            + "auto means vanilla), otherwise vanilla. Vanilla Minecraft has no timed ban: the fallback below runs instead.\n"
            + "custom = the templates under integrations.bans.custom."));
    public static final Setting<String> FALLBACK = s(Setting.choice(B + "fallback", FALLBACK_KICK, FALLBACK_KICK, FALLBACK_MUTE, FALLBACK_NONE)
        .title("When a ban is not possible")
        .description("What \"tempban\" does when the chosen provider has no tempban template (vanilla): kick = kick the player, mute = the built-in\n"
            + "mute for the same time, none = nothing. A warning is written to the console every time."));
    public static final Setting<String> DURATION_FORMAT = s(Setting.choice(B + "duration-format", AUTO, AUTO, "compound", "single")
        .title("How the ban time is written")
        .description("compound = 1d2h30m, single = one unit only (90m). auto = what the provider is known to accept (AdvancedBan: single)."));

    public static final Setting<String> ESSENTIALS_TEMPBAN = s(Setting.string(B + "essentials.tempban", "tempban {player} {duration} {reason}")
        .title("EssentialsX: tempban command").description(PLACEHOLDERS));
    public static final Setting<String> ESSENTIALS_IPBAN = s(Setting.string(B + "essentials.ipban", "banip {player} {reason}")
        .title("EssentialsX: ipban command").description(PLACEHOLDERS));
    public static final Setting<String> LITEBANS_TEMPBAN = s(Setting.string(B + "litebans.tempban", "tempban {player} {duration} {reason} -s")
        .title("LiteBans: tempban command").description(PLACEHOLDERS + " The -s keeps the ban silent."));
    public static final Setting<String> LITEBANS_IPBAN = s(Setting.string(B + "litebans.ipban", "ipban {player} {reason} -s")
        .title("LiteBans: ipban command").description(PLACEHOLDERS));
    public static final Setting<String> ADVANCEDBAN_TEMPBAN = s(Setting.string(B + "advancedban.tempban", "tempban {player} {duration} {reason}")
        .title("AdvancedBan: tempban command").description(PLACEHOLDERS));
    public static final Setting<String> ADVANCEDBAN_IPBAN = s(Setting.string(B + "advancedban.ipban", "ipban {player} {reason}")
        .title("AdvancedBan: ipban command").description(PLACEHOLDERS));
    public static final Setting<String> LIBERTYBANS_TEMPBAN = s(Setting.string(B + "libertybans.tempban", "tempban {player} {duration} {reason}")
        .title("LibertyBans: tempban command").description(PLACEHOLDERS));
    public static final Setting<String> LIBERTYBANS_IPBAN = s(Setting.string(B + "libertybans.ipban", "ipban {player} {reason}")
        .title("LibertyBans: ipban command").description(PLACEHOLDERS));
    public static final Setting<String> VANILLA_TEMPBAN = s(Setting.string(B + "vanilla.tempban", "")
        .title("Vanilla: tempban command").description("Vanilla Minecraft cannot do this; leave empty. " + PLACEHOLDERS));
    public static final Setting<String> VANILLA_IPBAN = s(Setting.string(B + "vanilla.ipban", "ban-ip {player} {reason}")
        .title("Vanilla: ipban command").description("ban-ip accepts the name of a player who is online. " + PLACEHOLDERS));
    public static final Setting<String> CUSTOM_TEMPBAN = s(Setting.string(B + "custom.tempban", "")
        .title("Custom: tempban command").description("Used when provider is custom. " + PLACEHOLDERS));
    public static final Setting<String> CUSTOM_IPBAN = s(Setting.string(B + "custom.ipban", "")
        .title("Custom: ipban command").description("Used when provider is custom. " + PLACEHOLDERS));

    public static final Setting<String> BEDROCK_MODE = s(Setting.choice(T + "mode", AUTO, AUTO, MODE_ON, MODE_OFF)
        .title("Bedrock players (Geyser / Floodgate)")
        .description("Bedrock players join through Geyser; Floodgate gives them a name with a prefix (and may keep spaces).\n"
            + "auto = on when the plugin floodgate or Geyser-Spigot is installed (Bukkit family only), on = always, off = never."));
    public static final Setting<String> BEDROCK_PREFIX = s(Setting.string(T + "prefix", ".")
        .title("Bedrock name prefix")
        .description("The prefix in front of Bedrock names; must be the same as username-prefix in Floodgate's config.yml."));
    public static final Setting<Boolean> BEDROCK_STRIP = s(Setting.bool(T + "strip-prefix-before-filter", true)
        .title("Strip the prefix before the name check")
        .description("Check the name of a Bedrock player when player names are scanned on join without the prefix, so \".Steve\" is judged as \"Steve\"."));
    public static final Setting<String> BEDROCK_SPACE = s(Setting.string(T + "space-replacement", "_")
        .title("Spaces in names inside commands")
        .description("A Bedrock name can contain spaces, which would split a console command. In {player} every space is replaced by this\n"
            + "text (Floodgate itself uses _). Empty = keep spaces. Looking up a player by name also accepts _ for a space and a missing prefix."));

    private IntegrationsSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static void register(SettingsRegistry registry) {
        registry.fileHeader(FILE, "AntiBadWords - integrations with other plugins\n"
            + "Ban plugins for the ladder actions tempban and ipban, and Bedrock players (Geyser / Floodgate). Reload with /abw reload.");
        for (Setting<?> setting : new Setting<?>[] {PROVIDER, FALLBACK, DURATION_FORMAT, ESSENTIALS_TEMPBAN, ESSENTIALS_IPBAN, LITEBANS_TEMPBAN,
            LITEBANS_IPBAN, ADVANCEDBAN_TEMPBAN, ADVANCEDBAN_IPBAN, LIBERTYBANS_TEMPBAN, LIBERTYBANS_IPBAN, VANILLA_TEMPBAN, VANILLA_IPBAN,
            CUSTOM_TEMPBAN, CUSTOM_IPBAN, BEDROCK_MODE, BEDROCK_PREFIX, BEDROCK_STRIP, BEDROCK_SPACE}) {
            registry.register(setting);
        }
    }
}
