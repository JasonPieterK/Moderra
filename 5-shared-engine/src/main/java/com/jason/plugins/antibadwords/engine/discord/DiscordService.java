package com.jason.plugins.antibadwords.engine.discord;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.ModerationEvent;
import com.jason.plugins.antibadwords.engine.config.Setting;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns engine events into Discord webhook messages: routing by kind to the alerts and/or log webhook, templates, embed or
 * plain style, optional mention. Installed by the {@link Engine} (it subscribes to {@code engine.events()}); the events are
 * handled on the publishing thread only as far as formatting a short text, the network work happens in {@link WebhookSender}.
 */
public final class DiscordService {
    public static final String ALERTS = "alerts";
    public static final String LOG = "log";
    private static final Pattern ENV_REF = Pattern.compile("^\\$\\{ENV:([A-Za-z_][A-Za-z0-9_]*)}$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");
    private static final int VALUE_LIMIT = 300;
    private static final int DESCRIPTION_LIMIT = 2000;

    private final Engine engine;
    private final WebhookSender alerts;
    private final WebhookSender log;
    private volatile Function<String, String> environment = System::getenv;
    private volatile boolean suppressed;
    private volatile DiscordLink link;
    private volatile boolean linkSuppressed;
    private Runnable unsubscribe;

    public DiscordService(Engine engine) {
        this.engine = engine;
        this.alerts = new WebhookSender(ALERTS, () -> resolvedUrl(DiscordSettings.ALERTS_URL), this::options,
            engine.platform().logger(), this::secrets);
        this.log = new WebhookSender(LOG, () -> resolvedUrl(DiscordSettings.LOG_URL), this::options, engine.platform().logger(), this::secrets);
    }

    /** Subscribes to the events, registers {@code /abw discord} and checks the settings after every reload. */
    public void install() {
        unsubscribe = engine.events().subscribe(this::onEvent);
        engine.commands().register(new DiscordCommand(this));
        engine.addReloadListener(this::checkSettings);
        engine.addReloadListener(this::applyMode);
    }

    // ------------------------------------------------------------------------------------------
    // DiscordSRV (Bukkit family): the bridge is attached by the plugin, never by the engine itself
    // ------------------------------------------------------------------------------------------

    /**
     * Called by the Bukkit plugin when DiscordSRV is present. Returns the link the plugin feeds with Discord messages. While the link
     * exists and {@code discord.mode} is {@code auto} or {@code discordsrv}, alerts go through the bridge and the webhooks stay quiet.
     */
    public DiscordLink attachBridge(DiscordBridge bridge) {
        DiscordLink created = new DiscordLink(engine, this, bridge);
        link = created;
        applyMode();
        return created;
    }

    /** Called when DiscordSRV goes away: the webhooks work again. */
    public void detachBridge() {
        link = null;
        applyMode();
    }

    /** The current DiscordSRV link, or null on Fabric/Forge and when DiscordSRV is not installed. */
    public DiscordLink link() {
        return link;
    }

    /** Whether the DiscordSRV features (filter, commands) may work at all: the master switch is on and the mode is not {@code off}. */
    boolean hookEnabled() {
        return engine.value(DiscordSettings.ENABLED) && !DiscordSettings.MODE_OFF.equals(engine.value(DiscordSettings.MODE));
    }

    boolean bridgeCarriesAlerts() {
        String mode = engine.value(DiscordSettings.MODE);
        return link != null && engine.value(DiscordSettings.ENABLED)
            && (DiscordSettings.MODE_AUTO.equals(mode) || DiscordSettings.MODE_DISCORDSRV.equals(mode));
    }

    private void applyMode() {
        if (link != null || linkSuppressed) {
            suppressed = bridgeCarriesAlerts();
            linkSuppressed = suppressed;
        }
    }

    /** Stops listening and sends what is still waiting (once, at once, for at most {@code discord.http.shutdown-wait}). */
    public void shutdown() {
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
        long wait = engine.value(DiscordSettings.SHUTDOWN_WAIT).toMillis();
        alerts.shutdown(wait);
        log.shutdown(wait);
    }

    /** Tests replace how environment variables are read. */
    public void setEnvironment(Function<String, String> environment) {
        this.environment = environment;
    }

    /** The DiscordSRV hook (Bukkit family) sets this while it carries the alerts itself, so nothing is sent twice. */
    public void suppressWebhooks(boolean suppressed) {
        this.suppressed = suppressed;
    }

    /** Whether events are sent through the webhooks right now. */
    public boolean active() {
        String mode = engine.value(DiscordSettings.MODE);
        return !suppressed && engine.value(DiscordSettings.ENABLED)
            && (DiscordSettings.MODE_AUTO.equals(mode) || DiscordSettings.MODE_WEBHOOK.equals(mode));
    }

    // ------------------------------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------------------------------

    private void onEvent(ModerationEvent event) {
        DiscordLink current = link;
        if (current != null) {
            current.onEvent(event);
        }
        if (!active()) {
            return;
        }
        boolean toAlerts = !resolvedUrl(DiscordSettings.ALERTS_URL).isEmpty() && routed(DiscordSettings.ROUTE_ALERTS, event.kind());
        boolean toLog = !resolvedUrl(DiscordSettings.LOG_URL).isEmpty() && routed(DiscordSettings.ROUTE_LOG, event.kind());
        if (!toAlerts && !toLog) {
            return;
        }
        String message = render(event);
        boolean embed = DiscordSettings.STYLE_EMBED.equals(engine.value(DiscordSettings.STYLE));
        if (toAlerts) {
            String mention = routed(DiscordSettings.MENTION_KINDS, event.kind()) ? engine.value(DiscordSettings.MENTION).trim() : "";
            alerts.enqueue(item(event, message, embed, mention, null), false);
        }
        if (toLog) {
            log.enqueue(item(event, message, embed, "", null), false);
        }
    }

    private WebhookSender.Item item(ModerationEvent event, String message, boolean embed, String mention, CompletableFuture<String> result) {
        if (!embed) {
            return new WebhookSender.Item(event.kind(), null, "**[" + event.kind() + "]** " + message, mention, result);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", "AntiBadWords: " + event.kind());
        card.put("description", WebhookSender.truncate(message, DESCRIPTION_LIMIT));
        card.put("color", color(event.kind()));
        card.put("timestamp", Instant.ofEpochMilli(event.time()).toString());
        String footer = (event.world().isEmpty() ? "" : event.world()) + (event.world().isEmpty() || event.source().isEmpty() ? "" : " | ")
            + event.source();
        if (!footer.isEmpty()) {
            card.put("footer", Map.of("text", WebhookSender.truncate(clean(footer), 200)));
        }
        return new WebhookSender.Item(event.kind(), card, null, mention, result);
    }

    private int color(String kind) {
        Setting<String> setting = DiscordSettings.color(kind);
        String text = setting == null ? "#95A5A6" : engine.value(setting).trim();
        Matcher m = Pattern.compile("^#?([0-9a-fA-F]{6})$").matcher(text);
        return m.matches() ? Integer.parseInt(m.group(1), 16) : 0x95A5A6;
    }

    /** The text of one event from its template. */
    String render(ModerationEvent event) {
        Setting<String> setting = DiscordSettings.template(event.kind());
        String template = setting == null ? DiscordSettings.defaultTemplate(event.kind()) : engine.value(setting);
        Map<String, String> values = placeholders(event);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Map<String, String> placeholders(ModerationEvent e) {
        Map<String, String> d = e.data();
        Map<String, String> v = new LinkedHashMap<>();
        v.put("kind", e.kind());
        v.put("player", clean(e.playerName().isEmpty() ? "console" : e.playerName()));
        v.put("world", clean(e.world()));
        v.put("source", clean(e.source()));
        v.put("detail", clean(d.getOrDefault("detail", "")));
        v.put("word", clean(d.getOrDefault("word", d.getOrDefault("detail", ""))));
        v.put("count", clean(d.getOrDefault("count", "")));
        v.put("limit", clean(d.getOrDefault("limit", "")));
        v.put("action", clean(first(d, "action", "punishments", "actions", "command")));
        v.put("check", clean(d.getOrDefault("check", "")));
        v.put("step", clean(d.getOrDefault("step", "")));
        v.put("reason", clean(d.getOrDefault("reason", "")));
        v.put("duration", clean(d.getOrDefault("duration", "")));
        v.put("command", clean(d.getOrDefault("command", "")));
        v.put("text", clean(d.getOrDefault("text", "")));
        return v;
    }

    private static String first(Map<String, String> data, String... keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** Makes player-controlled text harmless in Discord: no pings, no formatting, no line breaks, not too long. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < VALUE_LIMIT; i++) {
            char c = text.charAt(i);
            if (c == '@') {
                out.append("@​");
            } else if (c == '\\' || c == '*' || c == '_' || c == '~' || c == '|' || c == '`' || c == '>' || c == '<' || c == '[') {
                out.append('\\').append(c);
            } else if (Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------------------------------
    // Test and status (used by /abw discord)
    // ------------------------------------------------------------------------------------------

    /** One test message per configured webhook, sent ahead of the queue. The futures finish with a masked result text. */
    Map<String, CompletableFuture<String>> sendTest(String senderName) {
        Map<String, CompletableFuture<String>> results = new LinkedHashMap<>();
        boolean embed = DiscordSettings.STYLE_EMBED.equals(engine.value(DiscordSettings.STYLE));
        for (String target : List.of(ALERTS, LOG)) {
            Setting<String> url = ALERTS.equals(target) ? DiscordSettings.ALERTS_URL : DiscordSettings.LOG_URL;
            if (resolvedUrl(url).isEmpty()) {
                continue;
            }
            CompletableFuture<String> result = new CompletableFuture<>();
            ModerationEvent event = new ModerationEvent("test", engine.now(), null, senderName, "", "command", Map.of());
            String message = "Test. " + clean(senderName) + " sent this message. The " + target + " webhook works.";
            WebhookSender.Item item;
            if (embed) {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("title", "AntiBadWords: test");
                card.put("description", message);
                card.put("color", 0x2ECC71);
                card.put("timestamp", Instant.ofEpochMilli(event.time()).toString());
                item = new WebhookSender.Item("test", card, null, "", result);
            } else {
                item = new WebhookSender.Item("test", null, "**[test]** " + message, "", result);
            }
            (ALERTS.equals(target) ? alerts : log).enqueue(item, true);
            results.put(target, result);
        }
        return results;
    }

    /** Lines for {@code /abw discord status}; never contains a URL. */
    List<String> status() {
        List<String> lines = new ArrayList<>();
        lines.add("enabled: " + engine.value(DiscordSettings.ENABLED) + ", mode: " + engine.value(DiscordSettings.MODE)
            + (suppressed ? " (webhooks currently handed over to DiscordSRV)" : "") + ", sending: " + (active() ? "yes" : "no")
            + ", DiscordSRV: " + (link == null ? "not connected (Bukkit family only)" : "connected (" + link.bridge().name() + ")"));

        for (String target : List.of(ALERTS, LOG)) {
            Setting<String> url = ALERTS.equals(target) ? DiscordSettings.ALERTS_URL : DiscordSettings.LOG_URL;
            WebhookSender s = ALERTS.equals(target) ? alerts : log;
            String raw = engine.value(url);
            String state = raw.isBlank() ? "not set" : resolvedUrl(url).isEmpty() ? "set but unusable (see the console)" : "set (" + url.display(raw) + ")";
            lines.add(target + " webhook: " + state + "; waiting " + s.queued() + ", sent " + s.sent() + ", failed " + s.failed()
                + ", dropped " + s.dropped() + (s.lastError().isEmpty() ? "" : "; last error: " + s.lastError()));
        }
        return lines;
    }

    // ------------------------------------------------------------------------------------------
    // Settings, secrets
    // ------------------------------------------------------------------------------------------

    private WebhookSender.Options options() {
        return new WebhookSender.Options(engine.value(DiscordSettings.USERNAME), engine.value(DiscordSettings.QUEUE_SIZE),
            engine.value(DiscordSettings.MIN_INTERVAL).toMillis(), engine.value(DiscordSettings.BATCH_SIZE),
            engine.value(DiscordSettings.BATCH_WAIT).toMillis(), engine.value(DiscordSettings.TIMEOUT).toMillis(),
            engine.value(DiscordSettings.RETRIES), engine.value(DiscordSettings.BACKOFF).toMillis());
    }

    /** The URL to use: {@code ${ENV:NAME}} resolved, checked; "" when not set or not usable. */
    private String resolvedUrl(Setting<String> setting) {
        String resolved = resolve(engine.value(setting));
        return validUrl(resolved) ? resolved : "";
    }

    private String resolve(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher m = ENV_REF.matcher(text);
        if (m.matches()) {
            String value = environment.apply(m.group(1));
            return value == null ? "" : value.trim();
        }
        return text;
    }

    /** https, or plain http only to this machine (tests, a local relay). */
    static boolean validUrl(String text) {
        if (text.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(text);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(uri.getScheme())
                && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Every secret value that must be hidden: as written in the file and as resolved. */
    private Set<String> secrets() {
        Set<String> set = new LinkedHashSet<>();
        for (Setting<String> setting : List.of(DiscordSettings.ALERTS_URL, DiscordSettings.LOG_URL)) {
            String raw = engine.value(setting);
            if (!raw.isBlank()) {
                set.add(raw.trim());
                String resolved = resolve(raw);
                if (!resolved.isEmpty()) {
                    set.add(resolved);
                }
            }
        }
        return set;
    }

    /** Masks secrets in any text before it is logged or shown. */
    public String mask(String text) {
        return Secrets.mask(text, secrets());
    }

    private boolean routed(Setting<List<String>> setting, String kind) {
        return routed(engine.value(setting), kind);
    }

    static boolean routed(List<String> kinds, String kind) {
        for (String entry : kinds) {
            String e = entry.trim().toLowerCase(Locale.ROOT);
            if (e.equals("*") || e.equals(kind)) {
                return true;
            }
        }
        return false;
    }

    /** After every (re)load: say what is wrong with the Discord settings, without ever printing a URL. */
    private void checkSettings() {
        var logger = engine.platform().logger();
        if (!engine.value(DiscordSettings.ENABLED)) {
            return;
        }
        String mode = engine.value(DiscordSettings.MODE);
        if (DiscordSettings.MODE_OFF.equals(mode)) {
            return;
        }
        boolean literal = false;
        boolean any = false;
        for (Setting<String> setting : List.of(DiscordSettings.ALERTS_URL, DiscordSettings.LOG_URL)) {
            String raw = engine.value(setting).trim();
            if (raw.isEmpty()) {
                continue;
            }
            any = true;
            Matcher env = ENV_REF.matcher(raw);
            if (env.matches()) {
                String value = environment.apply(env.group(1));
                if (value == null || value.isBlank()) {
                    logger.warn("discord.yml " + setting.key() + ": the environment variable " + env.group(1) + " is not set.");
                } else if (!validUrl(value.trim())) {
                    logger.warn("discord.yml " + setting.key() + ": the value of " + env.group(1) + " is not a usable webhook URL (it must start with https://).");
                }
            } else {
                literal = true;
                if (!validUrl(raw)) {
                    logger.warn("discord.yml " + setting.key() + " is not a usable webhook URL (it must start with https://); the value is not shown here.");
                }
            }
        }
        if (!any && !DiscordSettings.MODE_DISCORDSRV.equals(mode)) {
            logger.warn("discord.yml: discord.enabled is true but no webhook URL is set; nothing will be sent.");
        }
        if (literal && worldReadable(engine.config().folder().resolve(DiscordSettings.FILE))) {
            logger.warn("discord.yml can be read by every user of this machine and contains a webhook URL (a secret). "
                + "Restrict it (chmod 600 discord.yml) or use ${ENV:NAME} instead of the URL.");
        }
    }

    static boolean worldReadable(Path file) {
        try {
            return Files.getPosixFilePermissions(file).contains(PosixFilePermission.OTHERS_READ);
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            return false;
        }
    }
}
