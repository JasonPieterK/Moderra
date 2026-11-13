package com.jason.plugins.antibadwords.engine.filters;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.util.List;

/** Settings of feature "link, IP and invite filter" ({@code filters/links.yml}). */
public final class LinkSettings {
    public static final String FILE = "filters/links.yml";
    public static final String PERMISSION_BYPASS = "antibadwords.bypass.links";
    public static final String MODE_OFF = "off";
    public static final String MODE_BLOCK_ALL = "block-all";
    public static final String MODE_ALLOW_LIST = "allow-list";
    public static final String MODE_BLOCK_LIST = "block-list";
    private static final String CATEGORY = "Links";
    private static final String P = "filters.links.";

    public static final Setting<String> MODE = s(Setting.choice(P + "mode", MODE_OFF, MODE_OFF, MODE_BLOCK_ALL, MODE_ALLOW_LIST, MODE_BLOCK_LIST)
        .title("Link filter mode")
        .description("off        = links are not checked (default)\n"
            + "block-all  = every link is caught\n"
            + "allow-list = every link is caught, except the domains in allowed-domains\n"
            + "block-list = only the domains in blocked-domains are caught"));
    public static final Setting<List<String>> ALLOWED = s(Setting.list(P + "allowed-domains", List.of()).title("Allowed domains")
        .description("Used by mode allow-list (and for IP addresses you want to allow). One entry per domain:\n"
            + "  example.com     = that domain and everything under it (www.example.com, shop.example.com)\n"
            + "  *.example.com   = only the names under it\n"
            + "  .org            = every domain that ends like this\n"
            + "  203.0.113.5     = that address"));
    public static final Setting<List<String>> BLOCKED = s(Setting.list(P + "blocked-domains", List.of()).title("Blocked domains")
        .description("Used by mode block-list. Same forms as allowed-domains."));
    public static final Setting<Boolean> BLOCK_IPS = s(Setting.bool(P + "block-ip-addresses", true).title("Catch IP addresses")
        .description("While the filter is on (any mode): catch IPv4 and IPv6 addresses such as 203.0.113.5 (server advertising),\n"
            + "unless the address is listed in allowed-domains. false = addresses are ignored."));
    public static final Setting<Boolean> BLOCK_INVITES = s(Setting.bool(P + "block-invites", true).title("Catch invite links")
        .description("While the filter is on (any mode): catch invite links (see invite-links below), even when their domain is\n"
            + "allowed, unless the invite is listed in allowed-invites. false = invites are treated like any other link."));
    public static final Setting<List<String>> INVITE_LINKS = s(Setting.list(P + "invite-links",
            List.of("discord.gg", "discord.com/invite", "discordapp.com/invite", "discord.me", "discord.io", "dsc.gg", "invite.gg",
                "t.me", "telegram.me", "chat.whatsapp.com", "guilded.gg"))
        .title("What counts as an invite link")
        .description("A domain, or a domain with the start of the path. Everything that begins like one of these is an invite."));
    public static final Setting<List<String>> ALLOWED_INVITES = s(Setting.list(P + "allowed-invites", List.of()).title("Allowed invites")
        .description("Invites that are fine, for example your own server: discord.gg/yourcode"));
    public static final Setting<Boolean> DETECT_OBFUSCATED = s(Setting.bool(P + "detect-hidden-dots", true).title("Find hidden dots")
        .description("Also find links whose dot is hidden: \"example dot com\", \"example(dot)com\", \"example[.]com\",\n"
            + "\"example .com\", \"example . com\". (Unicode dots and invisible characters are always handled.)"));
    public static final Setting<List<String>> DOT_WORDS = s(Setting.list(P + "dot-words", List.of("dot", "d0t", "punkt", "punto", "ponto", "точка"))
        .title("Words that mean \"dot\"").description("Used by detect-hidden-dots. Lower case."));
    public static final Setting<List<String>> ENDINGS = s(Setting.list(P + "domain-endings",
            List.of("com", "net", "org", "gg", "io", "co", "xyz", "info", "biz", "dev", "app", "tv", "cc", "ly", "gl", "link", "online", "site",
                "store", "shop", "club", "fun", "pro", "live", "top", "vip", "games", "host", "cloud", "tech", "space", "website", "world",
                "network", "edu", "gov", "tk", "ml", "ga", "cf", "gq", "uk", "ru", "fr", "nl", "pl", "br", "cz", "dk", "fi", "jp", "cn",
                "kr", "ua", "hu", "gr", "mx", "tr", "ch", "ro", "pt", "su"))
        .title("Domain endings")
        .description("A word.ending without http:// or www. only counts as a link when the ending is in this list. Endings that\n"
            + "are also everyday words in some language (de, es, it, me, in, at, to, is, us, no, se, eu, ca, au, id ...) are left\n"
            + "out on purpose, because \"casa.de\" or \"trust.me\" would be caught; add them if your players abuse them.\n"
            + "Links with http://, https:// or www. are always found."));
    public static final Setting<String> ACTION = s(Setting.choice(P + "action", FilterAction.BLOCK, FilterAction.BLOCK, FilterAction.CENSOR,
            FilterAction.LOG_ONLY)
        .title("Action")
        .description("block = the message is not delivered; censor = the link is replaced by censor-replacement (chat on\n"
            + "Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked); log-only = it goes through and is only noted."));
    public static final Setting<String> REPLACEMENT = s(Setting.string(P + "censor-replacement", "[link]").title("Censor: replacement")
        .description("What a caught link is replaced with when the action is censor."));
    public static final Setting<String> MESSAGE = s(Setting.string(P + "message", "&cLinks are not allowed here.").title("Message when blocked")
        .description("Sent to the player when the message was blocked for a link. Empty = no message. Colours: & codes."));
    public static final Setting<Integer> WEIGHT = s(Setting.integer(P + "strike-weight", 0).range(0, 100).title("Strikes for a link")
        .description("How many strikes a caught link adds. 0 = none."));
    public static final Setting<List<String>> SOURCES = s(Setting.list(P + "sources", List.of("chat", "command", "sign", "book", "anvil"))
        .title("Where links are checked")
        .description("Text sources: chat, command, sign, book, anvil, join, command-block (or \"*\" for all)."));

    private LinkSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).overridable().build();
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "AntiBadWords - links, IP addresses and invites\n"
            + "OFF until you choose a mode. Players with the permission antibadwords.bypass.links are not checked.\n"
            + "Reload with /abw reload.");
        for (Setting<?> setting : List.of(MODE, ALLOWED, BLOCKED, BLOCK_IPS, BLOCK_INVITES, INVITE_LINKS, ALLOWED_INVITES, DETECT_OBFUSCATED,
            DOT_WORDS, ENDINGS, ACTION, REPLACEMENT, MESSAGE, WEIGHT, SOURCES)) {
            r.register(setting);
        }
    }
}
