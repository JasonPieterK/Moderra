package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.PunishmentAction;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import com.jason.plugins.antibadwords.engine.moderation.Ladder;
import com.jason.plugins.antibadwords.engine.moderation.LadderSettings;
import com.jason.plugins.antibadwords.engine.moderation.MuteHandler;
import com.jason.plugins.antibadwords.mod.Names;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happens after a message was blocked: count the strike, tell staff and the console, warn or punish the player and
 * publish events. This is the old plugin's {@code handleViolation / notifyStaff / punish}, now talking to the
 * {@link Platform} instead of Bukkit. Runs on the server's main thread.
 */
final class ViolationHandler {
    /** The {@code step} of a punishment event when the classic flat setup (no ladder steps) punished. */
    static final String LEGACY_STEP = "legacy";
    private final Engine engine;

    ViolationHandler(Engine engine) {
        this.engine = engine;
    }

    void handle(Message message, Verdict verdict) {
        Hit primary = verdict.primary();
        if (primary == null) {
            return;
        }
        Platform platform = engine.platform();
        EffectiveSettings effective = verdict.settings();
        AntiBadWordsSettings s = effective.legacy();
        UUID playerId = message.playerId();
        String playerName = message.playerName();
        int weight = verdict.hits().stream().mapToInt(Hit::strikeWeight).sum();
        // No ladder steps (the default): the classic flat setup, exactly as before. With steps the ladder decides.
        Ladder ladder = effective.ladder();
        boolean useLadder = !ladder.isEmpty();
        int limit = useLadder
            ? ladder.limit((playerId == null ? 0 : engine.strikes().get(playerId)) + Math.max(0, weight))
            : s.violationsBeforePunish();

        Map<String, String> values = new HashMap<>();
        values.put("player", playerName);
        values.put("word", primary.detail());
        values.put("source", message.source());
        values.put("limit", Integer.toString(limit));

        boolean badWord = BadWordCheck.ID.equals(primary.check());
        if (verdict.blocked() && !badWord && playerId != null) {
            // "your message was not sent because ...": the line the check's own settings give
            TextCheck check = engine.check(primary.check());
            String feedback = check == null ? null : check.feedback(primary, effective);
            if (feedback != null && !feedback.isBlank()) {
                platform.sendMessage(playerId, Placeholders.apply(feedback, values));
            }
        }
        publishFilterEvents(message, verdict);

        if (weight <= 0 || playerId == null) {
            // Nothing to count (for example a spam hit with strike weight 0): only the console and the subscribers.
            int count = playerId == null ? 0 : engine.strikes().get(playerId);
            values.put("count", Integer.toString(count));
            values.put("remaining", Integer.toString(Math.max(0, limit - count)));
            if (s.logToConsole()) {
                platform.logger().info(playerName + " triggered " + primary.check() + " (" + primary.detail() + ") in " + message.source()
                    + ": " + outcome(verdict));
            }
            publishViolation(message, verdict, primary, weight, values);
            return;
        }

        engine.players().learn(playerId, playerName);
        int count = engine.strikes().add(playerId, weight);
        List<Ladder.Step> reached = List.of();
        if (useLadder) {
            limit = ladder.limit(count);
            values.put("limit", Integer.toString(limit));
            if (s.punishmentEnabled()) {
                // "count - weight" and not the old total: a player at strikes.max-points still reaches the top steps again
                reached = ladder.reached(Math.max(0, count - weight), count);
                if (reached.size() > 1 && LadderSettings.HIGHEST.equals(effective.get(LadderSettings.SEVERAL_STEPS))) {
                    reached = List.of(reached.get(reached.size() - 1));
                }
            }
        }
        int remaining = Math.max(0, limit - count);
        boolean punish = useLadder ? !reached.isEmpty() : count >= limit && s.punishmentEnabled();
        values.put("count", Integer.toString(count));
        values.put("remaining", Integer.toString(remaining));

        // ---- staff and console ----
        if (s.notifyStaff()) {
            String staff = Placeholders.apply(s.staffNotifyFormat(), values);
            for (PlayerRef other : platform.onlinePlayers()) {
                if (!other.name().equals(playerName) && platform.has(other.id(), Engine.PERMISSION_NOTIFY)) {
                    platform.sendMessage(other.id(), staff);
                }
            }
        }
        if (s.logToConsole()) {
            platform.logger().info(badWord
                ? playerName + " said blocked word '" + primary.detail() + "' in " + message.source() + " (violation " + count + "/" + limit + ")"
                : playerName + " triggered " + primary.check() + " (" + primary.detail() + ") in " + message.source() + ": "
                    + outcome(verdict) + " (violation " + count + "/" + limit + ")");
        }
        publishViolation(message, verdict, primary, weight, values);

        if (!punish) {
            // Plain warning (or "blocked, but punishment is disabled").
            if (s.broadcastWarnings()) {
                platform.broadcast(Placeholders.apply(s.warningBroadcastMessage(), values));
            }
            boolean disabled = useLadder ? !s.punishmentEnabled() && count >= ladder.firstThreshold() : count >= limit;
            platform.sendMessage(playerId, Placeholders.apply(disabled ? s.punishmentDisabledMessage() : s.warningPlayerMessage(), values));
            return;
        }
        if (useLadder) {
            for (Ladder.Step step : reached) {
                runStep(message, effective, step, values);
            }
            return;
        }

        // ---- punishment ----
        List<String> applied = new ArrayList<>();
        for (PunishmentAction action : s.punishmentActions()) {
            if (!action.enabled()) {
                continue;
            }
            String description = execute(platform, playerId, playerName, action);
            if (description != null && !description.isBlank()) {
                applied.add(description);
            }
        }
        values.put("remaining", "0");
        values.put("punishments", Names.formatPunishmentList(applied));
        values.put("punishment", values.get("punishments"));
        platform.broadcast(Placeholders.apply(s.punishmentBroadcastMessage(), values));
        platform.sendMessage(playerId, Placeholders.apply(s.punishmentPlayerMessage(), values));
        if (s.finalCommandEnabled() && !s.finalCommand().isBlank()) {
            platform.runConsoleCommand(stripSlash(Placeholders.apply(s.finalCommand(), values)));
        }
        engine.strikes().reset(playerId);

        Map<String, String> data = new HashMap<>(values);
        data.put("profile", effective.profile());
        data.put("step", LEGACY_STEP);
        data.put("threshold", Integer.toString(limit));
        engine.events().publish(new ModerationEvent(ModerationEvent.PUNISHMENT, engine.now(), playerId, playerName, message.world(),
            message.source(), data));
    }

    /**
     * Carries out one ladder step: the actions from top to bottom, then the messages, and last the actions that remove
     * the player from the server (kick, ban), so the player still sees the message. Publishes one {@code punishment} event.
     */
    private void runStep(Message message, EffectiveSettings effective, Ladder.Step step, Map<String, String> base) {
        Platform platform = engine.platform();
        UUID playerId = message.playerId();
        String playerName = message.playerName();
        String defaultReason = effective.get(LadderSettings.DEFAULT_REASON);
        Map<String, String> values = new HashMap<>(base);
        values.put("uuid", playerId.toString());
        values.put("step", step.name());
        values.put("threshold", Integer.toString(step.threshold()));
        values.put("reason", defaultReason);
        values.put("duration", "");

        List<String> applied = new ArrayList<>();
        List<Runnable> last = new ArrayList<>();
        boolean reset = false;
        for (Ladder.Action action : step.actions()) {
            try {
                switch (action.type()) {
                    case Ladder.WARN -> {
                        if (!action.args().get(0).isEmpty()) {
                            platform.sendMessage(playerId, Names.colour(Placeholders.apply(action.args().get(0), values)));
                        }
                        applied.add("a warning");
                    }
                    case Ladder.MUTE -> {
                        long millis = Long.parseLong(action.args().get(0));
                        String reason = reason(action.args().get(1), defaultReason, values);
                        values.put("reason", reason);
                        values.put("duration", DurationParser.format(millis));
                        if (engine.muteHandler().mute(playerId, playerName, millis, reason, MuteHandler.SYSTEM)) {
                            applied.add(DurationParser.isPermanent(millis) ? "a permanent mute" : "a mute (" + DurationParser.format(millis) + ")");
                        }
                    }
                    case Ladder.KICK -> {
                        String reason = reason(action.args().get(0), defaultReason, values);
                        values.put("reason", reason);
                        last.add(() -> platform.kick(playerId, Names.colour(reason)));
                        applied.add("a kick");
                    }
                    case Ladder.BAN -> {
                        String reason = reason(action.args().get(0), defaultReason, values);
                        values.put("reason", reason);
                        String command = stripSlash(Placeholders.apply(effective.get(LadderSettings.BAN_COMMAND), values));
                        if (!command.isBlank()) {
                            last.add(() -> platform.runConsoleCommand(command));
                            applied.add("a ban");
                        }
                    }
                    case Ladder.TEMPBAN -> {
                        long millis = Long.parseLong(action.args().get(0));
                        String reason = reason(action.args().get(1), defaultReason, values);
                        values.put("reason", reason);
                        values.put("duration", DurationParser.format(millis));
                        queue(engine.bans().tempban(playerId, playerName, millis, reason), applied, last);
                    }
                    case Ladder.IPBAN -> {
                        String reason = reason(action.args().get(0), defaultReason, values);
                        values.put("reason", reason);
                        queue(engine.bans().ipban(playerId, playerName, reason), applied, last);
                    }
                    case Ladder.COMMAND -> {
                        platform.runConsoleCommand(stripSlash(Placeholders.apply(action.args().get(0), values)));
                        applied.add("server command");
                    }
                    case Ladder.PUNISHMENTS -> {
                        for (PunishmentAction classic : effective.legacy().punishmentActions()) {
                            if (classic.enabled()) {
                                add(applied, execute(platform, playerId, playerName, classic));
                            }
                        }
                    }
                    case Ladder.RESET -> reset = true;
                    default -> add(applied, execute(platform, playerId, playerName,
                        new PunishmentAction(true, action.type(), step.name(), action.args())));
                }
            } catch (RuntimeException ex) {
                platform.logger().warn("Could not carry out '" + action.text() + "' of ladder step '" + step.name() + "': " + ex);
            }
        }
        values.put("punishments", Names.formatPunishmentList(applied));
        values.put("punishment", values.get("punishments"));
        if (!step.broadcast().isBlank()) {
            platform.broadcast(Names.colour(Placeholders.apply(step.broadcast(), values)));
        }
        if (!step.message().isBlank()) {
            platform.sendMessage(playerId, Names.colour(Placeholders.apply(step.message(), values)));
        }
        if (!step.finalCommand().isBlank()) {
            platform.runConsoleCommand(stripSlash(Placeholders.apply(step.finalCommand(), values)));
        }
        for (Runnable task : last) {
            try {
                task.run();
            } catch (RuntimeException ex) {
                platform.logger().warn("Could not finish ladder step '" + step.name() + "': " + ex);
            }
        }
        if (reset) {
            engine.strikes().reset(playerId);
        }

        Map<String, String> data = new HashMap<>(values);
        data.put("profile", effective.profile());
        data.put("actions", step.actions().stream().map(Ladder.Action::text).collect(java.util.stream.Collectors.joining("; ")));
        engine.events().publish(new ModerationEvent(ModerationEvent.PUNISHMENT, engine.now(), playerId, playerName, message.world(),
            message.source(), data));
    }

    private static void queue(com.jason.plugins.antibadwords.engine.integrations.BanService.Result result, List<String> applied,
        List<Runnable> last) {
        if (result.applied() != null) {
            applied.add(result.applied());
        }
        if (result.late() != null) {
            last.add(result.late());
        }
    }

    private static String reason(String own, String fallback, Map<String, String> values) {
        return Placeholders.apply(own.isEmpty() ? fallback : own, values);
    }

    private static void add(List<String> applied, String description) {
        if (description != null && !description.isBlank()) {
            applied.add(description);
        }
    }

    private static String outcome(Verdict verdict) {
        return verdict.blocked() ? "blocked" : verdict.censored() ? "cleaned" : "noted only";
    }

    /** One {@code spam} / {@code link} / {@code evasion} event per hit of such a check (the kind is the id up to the first "-"). */
    private void publishFilterEvents(Message message, Verdict verdict) {
        for (Hit hit : verdict.hits()) {
            if (BadWordCheck.ID.equals(hit.check())) {
                continue;
            }
            int dash = hit.check().indexOf('-');
            Map<String, String> data = new HashMap<>();
            data.put("check", hit.check());
            data.put("detail", hit.detail() == null ? "" : hit.detail());
            data.put("action", hit.action().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
            data.put("verdict", verdict.type().name().toLowerCase(java.util.Locale.ROOT));
            data.put("weight", Integer.toString(hit.strikeWeight()));
            data.put("profile", verdict.settings().profile());
            data.put("text", message.rawText() == null ? "" : message.rawText());
            data.put("player", message.playerName());
            engine.events().publish(new ModerationEvent(dash < 0 ? hit.check() : hit.check().substring(0, dash), engine.now(),
                message.playerId(), message.playerName(), message.world(), message.source(), data));
        }
    }

    private void publishViolation(Message message, Verdict verdict, Hit primary, int weight, Map<String, String> values) {
        Map<String, String> data = new HashMap<>(values);
        data.put("check", primary.check());
        data.put("detail", primary.detail() == null ? "" : primary.detail());
        data.put("severity", Integer.toString(primary.severity()));
        data.put("action", verdict.type().name().toLowerCase(java.util.Locale.ROOT));
        data.put("weight", Integer.toString(weight));
        data.put("profile", verdict.settings().profile());
        data.put("text", message.rawText() == null ? "" : message.rawText());
        engine.events().publish(new ModerationEvent(ModerationEvent.VIOLATION, engine.now(), message.playerId(), message.playerName(),
            message.world(), message.source(), data));
    }

    /** Runs one action; {@code null} (and a log line) when it could not be applied. */
    private String execute(Platform platform, UUID playerId, String playerName, PunishmentAction action) {
        try {
            if (action.type().equals("command")) {
                platform.runConsoleCommand(stripSlash(Placeholders.apply(action.args().get(0), Map.of("player", playerName))));
                return "server command";
            }
            return platform.applyAction(playerId, action);
        } catch (RuntimeException ex) {
            platform.logger().warn("Could not apply " + action.type() + " punishment '" + action.id() + "' ("
                + String.join(", ", action.args()) + "): " + ex.getMessage());
            return null;
        }
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
