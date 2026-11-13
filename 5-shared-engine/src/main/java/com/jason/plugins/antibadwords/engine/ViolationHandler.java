package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.PunishmentAction;
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
        int limit = s.violationsBeforePunish();

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
        int remaining = Math.max(0, limit - count);
        boolean punish = count >= limit && s.punishmentEnabled();
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
            platform.sendMessage(playerId, Placeholders.apply(count >= limit ? s.punishmentDisabledMessage() : s.warningPlayerMessage(), values));
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
        engine.events().publish(new ModerationEvent(ModerationEvent.PUNISHMENT, engine.now(), playerId, playerName, message.world(),
            message.source(), data));
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
