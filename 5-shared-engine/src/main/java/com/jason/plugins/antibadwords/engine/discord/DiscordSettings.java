package com.jason.plugins.antibadwords.engine.discord;

import com.jason.plugins.antibadwords.engine.ModerationEvent;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings of feature "Discord webhook alerts" ({@code discord.yml}). */
public final class DiscordSettings {
    public static final String FILE = "discord.yml";
    public static final String PERMISSION = "antibadwords.discord";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_WEBHOOK = "webhook";
    public static final String MODE_DISCORDSRV = "discordsrv";
    public static final String MODE_OFF = "off";
    public static final String STYLE_EMBED = "embed";
    public static final String STYLE_PLAIN = "plain";
    private static final String CATEGORY = "Discord";
    private static final String P = "discord.";

    /** Event kinds that can be routed, in the order they appear in the file. */
    public static final List<String> KINDS = List.of(ModerationEvent.VIOLATION, ModerationEvent.PUNISHMENT, "mute", "unmute",
        ModerationEvent.SPAM, ModerationEvent.LINK, ModerationEvent.EVASION, ModerationEvent.ADMIN_COMMAND);

    private static final Map<String, String> DEFAULT_COLORS = Map.of(
        ModerationEvent.VIOLATION, "#F1C40F", ModerationEvent.PUNISHMENT, "#E74C3C", "mute", "#E67E22", "unmute", "#2ECC71",
        ModerationEvent.SPAM, "#3498DB", ModerationEvent.LINK, "#9B59B6", ModerationEvent.EVASION, "#1ABC9C",
        ModerationEvent.ADMIN_COMMAND, "#95A5A6");

    private static final Map<String, String> DEFAULT_TEMPLATES = Map.of(
        ModerationEvent.VIOLATION, "{player} used a blocked word in {source}: {word} ({count}/{limit}, {action})",
        ModerationEvent.PUNISHMENT, "{player} was punished ({action}) at {count} strikes, step {step}",
        "mute", "{player} was muted for {duration}: {reason}",
        "unmute", "{player} was unmuted",
        ModerationEvent.SPAM, "{player} triggered the spam filter ({check}: {detail}) in {source}, action {action}",
        ModerationEvent.LINK, "{player} posted a link in {source}: {detail} (action {action})",
        ModerationEvent.EVASION, "{player} tried to dodge the filter in {source}: {detail} (action {action})",
        ModerationEvent.ADMIN_COMMAND, "{player} ran /{command}");

    public static final Setting<Boolean> ENABLED = s(Setting.bool(P + "enabled", false).title("Discord alerts on")
        .description("Master switch. false = nothing is ever sent to Discord (default)."));
    public static final Setting<String> MODE = s(Setting.choice(P + "mode", MODE_AUTO, MODE_AUTO, MODE_WEBHOOK, MODE_DISCORDSRV, MODE_OFF)
        .title("Mode")
        .description("How alerts reach Discord:\n"
            + "  auto       = DiscordSRV when it is installed (Bukkit family only), otherwise the webhooks below\n"
            + "  webhook    = always the webhooks below (works on every server type)\n"
            + "  discordsrv = only through DiscordSRV (Bukkit family only; ignored by Fabric and Forge)\n"
            + "  off        = nothing is sent"));
    public static final Setting<String> ALERTS_URL = s(Setting.secret(P + "webhook.alerts-url").title("Alerts webhook")
        .description("SECRET. The webhook URL of your alerts channel (Discord: channel settings > Integrations > Webhooks).\n"
            + "Anyone with this URL can post in your channel, so keep this file private. Better: write ${ENV:ABW_ALERTS_URL}\n"
            + "and set that environment variable on the server. Empty = no alerts channel."));
    public static final Setting<String> LOG_URL = s(Setting.secret(P + "webhook.log-url").title("Log webhook")
        .description("SECRET. Optional second webhook for a quiet log channel (see routing.log). Same rules as the alerts\n"
            + "webhook, ${ENV:NAME} works too. Empty = no log channel."));
    public static final Setting<String> USERNAME = s(Setting.string(P + "webhook.username", "AntiBadWords").title("Sender name")
        .description("The name shown for the messages in Discord. Empty = the name set in the webhook."));
    public static final Setting<List<String>> ROUTE_ALERTS = s(Setting.list(P + "routing.alerts",
            List.of(ModerationEvent.PUNISHMENT, "mute", "unmute", ModerationEvent.EVASION, ModerationEvent.ADMIN_COMMAND))
        .title("Kinds sent to the alerts channel")
        .description("Event kinds: " + String.join(", ", KINDS) + ". \"*\" = every kind."));
    public static final Setting<List<String>> ROUTE_LOG = s(Setting.list(P + "routing.log", List.of("*"))
        .title("Kinds sent to the log channel")
        .description("Same kinds as above; the default \"*\" logs everything. Only used when log-url is set. A kind can go to both."));
    public static final Setting<String> STYLE = s(Setting.choice(P + "style", STYLE_EMBED, STYLE_EMBED, STYLE_PLAIN).title("Message style")
        .description("embed = a coloured card per event, plain = one line of text per event."));
    public static final Setting<String> MENTION = s(Setting.string(P + "mention", "").title("Mention")
        .description("Written exactly as Discord wants it, for example <@&123456789> (a role) or <@123456789> (a person); empty = nobody.\n"
            + "Only @everyone, @here, roles and users that appear here can ever be pinged; names typed by players never can."));
    public static final Setting<List<String>> MENTION_KINDS = s(Setting.list(P + "mention-kinds", List.of(ModerationEvent.PUNISHMENT))
        .title("Kinds that mention").description("Which kinds add the mention (the severe ones). Same kinds as above, \"*\" = all."));
    public static final Setting<Integer> QUEUE_SIZE = s(Setting.integer(P + "queue.size", 500).range(10, 10000).title("Queue size")
        .description("How many messages may wait per webhook. When it is full the OLDEST waiting message is dropped (with a warning).\n"
            + "The server never waits for Discord."));
    public static final Setting<Duration> MIN_INTERVAL = s(Setting.duration(P + "rate.min-interval", Duration.ofSeconds(1))
        .range(0, 3600).title("Time between requests")
        .description("Shortest time between two requests to the same webhook (Discord allows about 5 per 2 seconds; 429 answers are\n"
            + "also honoured)."));
    public static final Setting<Integer> BATCH_SIZE = s(Setting.integer(P + "rate.batch-size", 5).range(1, 10).title("Batch size")
        .description("Up to this many events are sent in one request (embed style: up to 10 cards; plain style: lines)."));
    public static final Setting<Duration> BATCH_WAIT = s(Setting.duration(P + "rate.batch-wait", Duration.ofSeconds(2)).range(0, 600)
        .title("Batch wait")
        .description("After the first waiting event the sender waits this long for more before sending. 0s = send at once."));
    public static final Setting<Duration> TIMEOUT = s(Setting.duration(P + "http.timeout", Duration.ofSeconds(10)).range(1, 120)
        .title("Request timeout").description("How long one request may take."));
    public static final Setting<Integer> RETRIES = s(Setting.integer(P + "http.retries", 3).range(0, 10).title("Retries")
        .description("How often a failed request (no connection, 5xx, 429) is repeated before the batch is given up."));
    public static final Setting<Duration> BACKOFF = s(Setting.duration(P + "http.backoff", Duration.ofSeconds(2)).range(0, 600)
        .title("Retry delay")
        .description("Wait before the first retry; it doubles every time (2s, 4s, 8s ...)."));
    public static final Setting<Duration> SHUTDOWN_WAIT = s(Setting.duration(P + "http.shutdown-wait", Duration.ofSeconds(5)).range(0, 60)
        .title("Wait on shutdown")
        .description("When the server stops, waiting messages are sent right away; this is the longest the shutdown waits for that."));

    private static final Map<String, Setting<String>> COLORS = new LinkedHashMap<>();
    private static final Map<String, Setting<String>> TEMPLATES = new LinkedHashMap<>();

    static {
        for (String kind : KINDS) {
            COLORS.put(kind, s(Setting.string(P + "colors." + kind, DEFAULT_COLORS.get(kind)).title("Colour: " + kind)
                .description("Colour of the " + kind + " card (embed style) as #RRGGBB.")));
            TEMPLATES.put(kind, s(Setting.string(P + "templates." + kind, DEFAULT_TEMPLATES.get(kind)).title("Text: " + kind)
                .description("Text for " + kind + " events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}\n"
                    + "and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply).")));
        }
    }

    private DiscordSettings() {
    }

    private static <T> Setting<T> s(Setting.Builder<T> builder) {
        return builder.file(FILE).category(CATEGORY).build();
    }

    public static Setting<String> color(String kind) {
        return COLORS.get(kind);
    }

    public static Setting<String> template(String kind) {
        return TEMPLATES.get(kind);
    }

    public static String defaultTemplate(String kind) {
        return DEFAULT_TEMPLATES.getOrDefault(kind, "{kind}: {player} {detail}");
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "AntiBadWords - Discord alerts\n"
            + "OFF until you set enabled: true and paste a webhook URL. The URLs are SECRETS: keep this file private\n"
            + "(chmod 600) or use ${ENV:NAME}. Test with /abw discord test. Reload with /abw reload.");
        List<Setting<?>> all = new ArrayList<>(List.of(ENABLED, MODE, ALERTS_URL, LOG_URL, USERNAME, ROUTE_ALERTS, ROUTE_LOG, STYLE, MENTION,
            MENTION_KINDS, QUEUE_SIZE, MIN_INTERVAL, BATCH_SIZE, BATCH_WAIT, TIMEOUT, RETRIES, BACKOFF, SHUTDOWN_WAIT));
        all.addAll(COLORS.values());
        all.addAll(TEMPLATES.values());
        all.forEach(r::register);
    }
}
