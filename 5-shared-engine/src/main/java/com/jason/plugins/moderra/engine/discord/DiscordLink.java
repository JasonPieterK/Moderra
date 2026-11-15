package com.jason.plugins.moderra.engine.discord;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.Hit;
import com.jason.plugins.moderra.engine.Message;
import com.jason.plugins.moderra.engine.ModerationEvent;
import com.jason.plugins.moderra.engine.PlayerRef;
import com.jason.plugins.moderra.engine.Verdict;
import com.jason.plugins.moderra.engine.command.CommandSender;
import com.jason.plugins.moderra.engine.command.SubCommand;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * All decisions of the DiscordSRV hook, with no Discord library in sight: the Bukkit plugin translates DiscordSRV's events into
 * {@link Incoming} and acts on the {@link Decision}; alerts go out through the {@link DiscordBridge}. Created by
 * {@link DiscordService#attachBridge(DiscordBridge)}.
 *
 * <p>Three jobs: (a) alerts to DiscordSRV channels, (b) checking messages that come from Discord (source {@code discord-in}),
 * (c) moderation commands typed in Discord. (c) is off by default and fails closed.
 */
public final class DiscordLink {
    /** One message from Discord. {@code roleIds} are the sender's role ids (empty for a webhook or a person without roles). */
    public record Incoming(String channelId, String channelName, String userId, String userName, Set<String> roleIds, String text) {
        public Incoming {
            channelId = channelId == null ? "" : channelId;
            channelName = channelName == null ? "" : channelName;
            userId = userId == null ? "" : userId;
            userName = userName == null ? "" : userName;
            roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
            text = text == null ? "" : text;
        }
    }

    /** What to do with the message: deliver it, drop it, or deliver {@code text} instead. */
    public record Decision(Type type, String text) {
        public enum Type { ALLOW, BLOCK, REPLACE }

        static final Decision ALLOW = new Decision(Type.ALLOW, null);
        static final Decision BLOCK = new Decision(Type.BLOCK, null);
    }

    /** Player names Minecraft can have (Java names; a leading dot is how Bedrock players appear). */
    private static final Pattern PLAYER_NAME = Pattern.compile("^\\.?[A-Za-z0-9_]{1,16}$");
    private static final Pattern COLOR_CODE = Pattern.compile("§.");
    private static final int REPLY_LIMIT = 1800;
    private static final int TEXT_LIMIT = 256;

    private final Engine engine;
    private final DiscordService service;
    private final DiscordBridge bridge;
    private final Map<String, ArrayDeque<Long>> usage = new HashMap<>();

    DiscordLink(Engine engine, DiscordService service, DiscordBridge bridge) {
        this.engine = engine;
        this.service = service;
        this.bridge = bridge;
    }

    public DiscordBridge bridge() {
        return bridge;
    }

    // ------------------------------------------------------------------------------------------
    // (a) alerts
    // ------------------------------------------------------------------------------------------

    /** Called by the service for every event while the link exists. */
    void onEvent(ModerationEvent event) {
        if (!service.bridgeCarriesAlerts()) {
            return;
        }
        String alerts = engine.value(DiscordSrvSettings.ALERT_CHANNEL).trim();
        String log = engine.value(DiscordSrvSettings.LOG_CHANNEL).trim();
        boolean toAlerts = !alerts.isEmpty() && DiscordService.routed(engine.value(DiscordSettings.ROUTE_ALERTS), event.kind());
        boolean toLog = !log.isEmpty() && !log.equalsIgnoreCase(alerts) && DiscordService.routed(engine.value(DiscordSettings.ROUTE_LOG), event.kind());
        if (!toAlerts && !toLog) {
            return;
        }
        String text = "**[" + event.kind() + "]** " + service.render(event);
        try {
            if (toAlerts && !bridge.sendToChannel(alerts, text)) {
                engine.platform().logger().warn("DiscordSRV: error. It has no usable channel named '" + alerts + "' (discord.discordsrv.alert-channel).");
            }
            if (toLog && !bridge.sendToChannel(log, text)) {
                engine.platform().logger().warn("DiscordSRV: error. It has no usable channel named '" + log + "' (discord.discordsrv.log-channel).");
            }
        } catch (RuntimeException ex) {
            engine.platform().logger().warn("DiscordSRV: error. The plugin cannot send the message: " + ex.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------------------------------
    // One entry point for every message that arrives from Discord
    // ------------------------------------------------------------------------------------------

    /**
     * Handles one message from Discord: first as a moderation command (when it starts with the prefix in a command channel), otherwise
     * as text to filter. May be called from any thread.
     *
     * @param reply where answers to commands go (the Discord channel the command came from); called on the server thread
     */
    public Decision onMessage(Incoming in, Consumer<String> reply) {
        if (!service.hookEnabled()) {
            return Decision.ALLOW; // discord.enabled: false or mode: off switches every DiscordSRV feature off
        }
        if (isCommand(in)) {
            handleCommand(in, reply);
            return Decision.BLOCK; // a command line is never shown in the game
        }
        return filter(in);
    }

    private static boolean matches(List<String> list, Incoming in) {
        for (String entry : list) {
            String e = entry.trim();
            if (e.equals("*") || (!e.isEmpty() && (e.equals(in.channelId()) || e.equalsIgnoreCase(in.channelName())))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // (b) filter Discord to Minecraft
    // ------------------------------------------------------------------------------------------

    /** Checks the text of one Discord message with the engine (source {@code discord-in}). */
    public Decision filter(Incoming in) {
        if (!engine.value(DiscordSrvSettings.INBOUND_ENABLED) || in.text().isBlank()
            || !matches(engine.value(DiscordSrvSettings.INBOUND_CHANNELS), in) || matches(engine.value(DiscordSrvSettings.INBOUND_EXCEPT), in)
            || isBypassed(in)) {
            return Decision.ALLOW;
        }
        // a stable fake id per Discord user so the spam counters work; it is never a player and never gets strikes
        UUID id = UUID.nameUUIDFromBytes(("discord:" + in.userId()).getBytes(StandardCharsets.UTF_8));
        Message message = Message.of(id, in.userName(), "", DiscordSrvSettings.SOURCE, in.text(), node -> false);
        Verdict verdict = engine.inspect(message);
        if (!verdict.hasHits()) {
            return Decision.ALLOW;
        }
        String action = engine.value(DiscordSrvSettings.INBOUND_ACTION);
        Decision decision = Decision.ALLOW;
        String done = "none";
        if (verdict.blocked() || verdict.censored()) {
            if (DiscordSrvSettings.ACTION_LOG_ONLY.equals(action)) {
                done = DiscordSrvSettings.ACTION_LOG_ONLY;
            } else if (DiscordSrvSettings.ACTION_CENSOR.equals(action) && verdict.censored() && bridge.canRewrite()) {
                decision = new Decision(Decision.Type.REPLACE, verdict.text());
                done = DiscordSrvSettings.ACTION_CENSOR;
            } else {
                decision = Decision.BLOCK;
                done = DiscordSrvSettings.ACTION_BLOCK;
            }
        }
        publishInbound(in, verdict, done);
        if (decision != Decision.ALLOW && engine.value(DiscordSrvSettings.NOTIFY_SENDER) && !in.userId().isEmpty()) {
            try {
                bridge.notifyUser(in.userId(), engine.value(DiscordSrvSettings.NOTIFY_TEXT));
            } catch (RuntimeException ex) {
                engine.platform().logger().warn("DiscordSRV: error. The plugin cannot tell the Discord user: " + ex.getClass().getSimpleName());
            }
        }
        return decision;
    }

    private boolean isBypassed(Incoming in) {
        if (!in.userId().isEmpty() && engine.value(DiscordSrvSettings.BYPASS_USERS).stream().anyMatch(u -> u.trim().equals(in.userId()))) {
            return true;
        }
        return engine.value(DiscordSrvSettings.BYPASS_ROLES).stream().anyMatch(r -> in.roleIds().contains(r.trim()));
    }

    private void publishInbound(Incoming in, Verdict verdict, String action) {
        Hit hit = verdict.primary();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("check", hit == null ? "" : hit.check());
        data.put("detail", hit == null || hit.detail() == null ? "" : hit.detail());
        data.put("word", hit == null || hit.detail() == null ? "" : hit.detail());
        data.put("action", action);
        data.put("channel", in.channelName().isEmpty() ? in.channelId() : in.channelName());
        data.put("discord-user", in.userId());
        data.put("text", in.text());
        ModerationEvent event = new ModerationEvent(ModerationEvent.VIOLATION, engine.now(), null, "Discord:" + in.userName(), "",
            DiscordSrvSettings.SOURCE, data);
        engine.platform().runSync(() -> engine.events().publish(event));
    }

    // ------------------------------------------------------------------------------------------
    // (c) moderation from Discord
    // ------------------------------------------------------------------------------------------

    private boolean isCommand(Incoming in) {
        String prefix = engine.value(DiscordSrvSettings.PREFIX).trim();
        if (DiscordSrvSettings.STYLE_SLASH.equals(engine.value(DiscordSrvSettings.STYLE))) {
            return false; // slash only: a typed prefix line is an ordinary message
        }
        if (prefix.isEmpty() || !matches(engine.value(DiscordSrvSettings.COMMAND_CHANNELS), in)) {
            return false;
        }
        String text = in.text().stripLeading();
        return text.regionMatches(true, 0, prefix, 0, prefix.length())
            && (text.length() == prefix.length() || Character.isWhitespace(text.charAt(prefix.length())));
    }

    private boolean isAllowed(Incoming in) {
        if (in.userId().isEmpty()) {
            return false;
        }
        return engine.value(DiscordSrvSettings.ALLOWED_USERS).stream().anyMatch(u -> u.trim().equals(in.userId()))
            || engine.value(DiscordSrvSettings.ALLOWED_ROLES).stream().anyMatch(r -> in.roleIds().contains(r.trim()));
    }

    private boolean rateLimited(String userId) {
        long now = engine.now();
        long window = engine.value(DiscordSrvSettings.RATE_WINDOW).toMillis();
        int max = engine.value(DiscordSrvSettings.RATE_MAX);
        synchronized (usage) {
            ArrayDeque<Long> times = usage.computeIfAbsent(userId, k -> new ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() >= window) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return true;
            }
            times.addLast(now);
            return false;
        }
    }

    /** Reply for a slash command that is refused for any reason before the allow-list passed. It says nothing about why. */
    static final String PREFIX = "[Moderra] ";
    static final String SLASH_REFUSED = PREFIX + "Error. You cannot use this command here.";

    /**
     * Handles one DiscordSRV slash command with the same rules as a prefix command (command channel, allow-list, switch, rate limit,
     * audit event). Unlike a typed message, a slash command must always be answered, so a refused call gets one neutral line that
     * does not tell which rule refused it. May be called from any thread.
     *
     * @param word the command word ({@code mute}, {@code unmute}, {@code history}, {@code strikes}, {@code check}, {@code reload})
     * @param arguments the option values in order, joined by spaces (may be empty)
     * @param reply where the answer goes; called at most once, on the server thread or at once
     */
    public void onSlashCommand(Incoming in, String word, String arguments, Consumer<String> reply) {
        String style = engine.value(DiscordSrvSettings.STYLE);
        if (!service.hookEnabled() || DiscordSrvSettings.STYLE_PREFIX.equals(style)
            || !matches(engine.value(DiscordSrvSettings.COMMAND_CHANNELS), in) || !isAllowed(in)) {
            reply.accept(SLASH_REFUSED);
            return;
        }
        String w = word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
        String a = arguments == null ? "" : arguments.trim();
        runCommand(in, a.isEmpty() ? w : w + " " + a, reply);
    }

    private void handleCommand(Incoming in, Consumer<String> reply) {
        // fail closed: somebody who is not listed gets no answer at all and nothing is run or counted
        if (!isAllowed(in)) {
            return;
        }
        String prefix = engine.value(DiscordSrvSettings.PREFIX).trim();
        runCommand(in, in.text().stripLeading().substring(prefix.length()).trim(), reply);
    }

    /** Shared by prefix and slash commands; the caller has already checked the channel and the allow-list. */
    private void runCommand(Incoming in, String rest, Consumer<String> reply) {
        if (rateLimited(in.userId())) {
            reply.accept(PREFIX + "Notice. You send too many commands. Try again in a moment.");
            return;
        }
        String[] words = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        String word = words.length == 0 ? "" : words[0].toLowerCase(Locale.ROOT);
        var enabled = DiscordSrvSettings.commandSwitch(word);
        if (enabled == null) {
            reply.accept(PREFIX + "Info. Commands you can use here: " + String.join(", ", enabledWords()) + ".");
            return;
        }
        if (!engine.value(enabled)) {
            reply.accept(PREFIX + "Error. The command '" + word + "' is switched off on this server.");
            return;
        }
        List<String> args = new ArrayList<>();
        String problem = buildArguments(word, words, rest, args);
        if (problem != null) {
            reply.accept(problem);
            return;
        }
        SubCommand command = engine.commands().find(word);
        if (command == null) {
            reply.accept(PREFIX + "Error. This command does not exist on this server.");
            return;
        }
        String who = "Discord:" + safeName(in.userName()) + "(" + safeId(in.userId()) + ")";
        String line = "moderra " + word + (args.isEmpty() ? "" : " " + String.join(" ", args));
        Map<String, String> data = new LinkedHashMap<>();
        data.put("command", line);
        data.put("sender", who);
        data.put("discord-user", safeId(in.userId()));
        data.put("channel", in.channelName().isEmpty() ? in.channelId() : in.channelName());
        ModerationEvent audit = new ModerationEvent(ModerationEvent.ADMIN_COMMAND, engine.now(), null, who, "", "discord", data);
        String[] argArray = args.toArray(new String[0]);
        engine.platform().runSync(() -> {
            engine.events().publish(audit);
            Capture sender = new Capture(who, command.permission());
            try {
                engine.commands().execute(sender, "moderra", prepend(word, argArray));
            } catch (RuntimeException ex) {
                sender.send(PREFIX + "Error. The command failed.");
            }
            reply.accept(sender.text());
        });
    }

    private List<String> enabledWords() {
        List<String> out = new ArrayList<>();
        for (String w : List.of("mute", "unmute", "history", "strikes", "check", "reload")) {
            if (engine.value(DiscordSrvSettings.commandSwitch(w))) {
                out.add(w);
            }
        }
        return out.isEmpty() ? List.of("none (all switched off)") : out;
    }

    /** Validates and cleans the arguments; returns an error text, or null when {@code out} is ready. */
    private String buildArguments(String word, String[] words, String rest, List<String> out) {
        switch (word) {
            case "reload":
                return words.length > 1 ? PREFIX + "Usage: reload" : null;
            case "check": {
                String text = clean(rest.substring(word.length()).trim());
                if (text.isEmpty()) {
                    return PREFIX + "Usage: check <text>";
                }
                out.add(text);
                return null;
            }
            default:
                break;
        }
        // the rest take a player first
        if (words.length < 2) {
            return PREFIX + "Usage: " + word + " <player>" + ("mute".equals(word) ? " [time] [reason]" : "history".equals(word) ? " [page]" : "");
        }
        String name = words[1];
        if (!PLAYER_NAME.matcher(name).matches() || ("history".equals(word) && (name.equalsIgnoreCase("recent") || name.equalsIgnoreCase("purge")))) {
            return PREFIX + "Error. The player name is not valid.";
        }
        Optional<PlayerRef> ref = engine.resolvePlayer(name);
        if (ref.isEmpty()) {
            return PREFIX + "Error. The plugin does not know this player. The player is not online and never joined this server.";
        }
        out.add(ref.get().name());
        switch (word) {
            case "mute" -> {
                String more = clean(String.join(" ", List.of(words).subList(2, words.length)));
                if (!more.isEmpty()) {
                    out.addAll(List.of(more.split(" ")));
                }
            }
            case "history" -> {
                if (words.length > 2) {
                    if (words.length > 3 || !words[2].matches("\\d{1,4}")) {
                        return PREFIX + "Usage: history <player> [page]";
                    }
                    out.add(words[2]);
                }
            }
            default -> {
                if (words.length > 2) {
                    return PREFIX + "Usage: " + word + " <player>";
                }
            }
        }
        return null;
    }

    private static String[] prepend(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    /** Free text from Discord: no colour codes, no control characters, one line, bounded. */
    private static String clean(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < TEXT_LIMIT; i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '​' || Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString().trim().replaceAll(" {2,}", " ");
    }

    private static String safeName(String name) {
        String s = name.replaceAll("[^A-Za-z0-9 _.-]", "").trim();
        return s.isEmpty() ? "user" : s.substring(0, Math.min(32, s.length()));
    }

    private static String safeId(String id) {
        return id.replaceAll("[^0-9]", "");
    }

    /** Makes an answer fit for Discord: no colour codes, nobody can be pinged, bounded length. */
    static String forDiscord(String text) {
        String s = COLOR_CODE.matcher(text).replaceAll("").replace("@", "@​");
        return s.length() <= REPLY_LIMIT ? s : s.substring(0, REPLY_LIMIT) + "...";
    }

    /** The "player" a Discord command runs as: collects the answer; holds only the one permission its command needs. */
    private static final class Capture implements CommandSender {
        private final String name;
        private final String permission;
        private final StringBuilder out = new StringBuilder();

        Capture(String name, String permission) {
            this.name = name;
            this.permission = permission;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UUID id() {
            return null;
        }

        @Override
        public boolean has(String node) {
            return permission != null && permission.equals(node);
        }

        @Override
        public boolean suppressAudit() {
            return true; // DiscordLink publishes the one admin-command event itself (with the Discord user and channel)
        }

        @Override
        public void send(String message) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(message);
        }

        String text() {
            return forDiscord(out.length() == 0 ? PREFIX + "Done. The command ran." : out.toString());
        }
    }
}
