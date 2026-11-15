package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Platform;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import com.jason.plugins.antibadwords.engine.moderation.MuteHandler;
import com.jason.plugins.antibadwords.mod.Names;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the ladder actions {@code tempban} and {@code ipban} into the console command of the installed ban plugin
 * (settings {@code integrations.bans.*}). Nothing here talks to a ban plugin's API.
 */
public final class BanService {
    /** What to do for one action: the text for the punishment list and a task that runs last (command, kick), both may be null. */
    public record Result(String applied, Runnable late) {
    }

    private final Engine engine;

    public BanService(Engine engine) {
        this.engine = engine;
    }

    /** The provider in use: the setting, or for {@code auto} the first installed ban plugin, otherwise vanilla. */
    public String provider() {
        String chosen = engine.value(IntegrationsSettings.PROVIDER);
        if (!IntegrationsSettings.AUTO.equals(chosen)) {
            return chosen;
        }
        Platform platform = engine.platform();
        if (platform.hasPlugin("LiteBans")) {
            return IntegrationsSettings.LITEBANS;
        }
        if (platform.hasPlugin("AdvancedBan")) {
            return IntegrationsSettings.ADVANCEDBAN;
        }
        if (platform.hasPlugin("LibertyBans")) {
            return IntegrationsSettings.LIBERTYBANS;
        }
        if (platform.hasPlugin("Essentials")) {
            return IntegrationsSettings.ESSENTIALS;
        }
        return IntegrationsSettings.VANILLA;
    }

    /** Writes a time the way a ban plugin reads it: compound {@code 1d2h30m} or one unit {@code 90m}. At least one second. */
    public static String formatDuration(long millis, boolean singleUnit) {
        long seconds = Math.max(1, (millis + 999) / 1000);
        long[] size = {86_400, 3_600, 60, 1};
        String[] unit = {"d", "h", "m", "s"};
        if (singleUnit) {
            for (int i = 0; i < size.length; i++) {
                if (seconds % size[i] == 0) {
                    return seconds / size[i] + unit[i];
                }
            }
        }
        StringBuilder out = new StringBuilder();
        long left = seconds;
        for (int i = 0; i < size.length; i++) {
            if (left >= size[i]) {
                out.append(left / size[i]).append(unit[i]);
                left %= size[i];
            }
        }
        return out.toString();
    }

    private boolean singleUnit(String provider) {
        String format = engine.value(IntegrationsSettings.DURATION_FORMAT);
        return "single".equals(format) || (IntegrationsSettings.AUTO.equals(format) && IntegrationsSettings.ADVANCEDBAN.equals(provider));
    }

    private String template(String provider, boolean temp) {
        return switch (provider) {
            case IntegrationsSettings.ESSENTIALS -> engine.value(temp ? IntegrationsSettings.ESSENTIALS_TEMPBAN : IntegrationsSettings.ESSENTIALS_IPBAN);
            case IntegrationsSettings.LITEBANS -> engine.value(temp ? IntegrationsSettings.LITEBANS_TEMPBAN : IntegrationsSettings.LITEBANS_IPBAN);
            case IntegrationsSettings.ADVANCEDBAN -> engine.value(temp ? IntegrationsSettings.ADVANCEDBAN_TEMPBAN : IntegrationsSettings.ADVANCEDBAN_IPBAN);
            case IntegrationsSettings.LIBERTYBANS -> engine.value(temp ? IntegrationsSettings.LIBERTYBANS_TEMPBAN : IntegrationsSettings.LIBERTYBANS_IPBAN);
            case IntegrationsSettings.CUSTOM -> engine.value(temp ? IntegrationsSettings.CUSTOM_TEMPBAN : IntegrationsSettings.CUSTOM_IPBAN);
            default -> engine.value(temp ? IntegrationsSettings.VANILLA_TEMPBAN : IntegrationsSettings.VANILLA_IPBAN);
        };
    }

    private static String clean(String reason) {
        return reason == null ? "" : reason.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    /** The address for {@code {ip}}: online IP, else the last one remembered in players.json, else null. */
    private String ipOf(UUID id) {
        String ip = engine.platform().playerIp(id);
        if (ip == null || ip.isBlank()) {
            ip = engine.players().ipOf(id).orElse(null);
        }
        return ip == null || ip.isBlank() ? null : ip;
    }

    private String command(String provider, boolean temp, UUID id, String name, long millis, String reason) {
        String template = template(provider, temp).trim();
        if (template.isEmpty()) {
            return null;
        }
        Map<String, String> values = new HashMap<>();
        values.put("player", engine.bedrock().commandName(name));
        values.put("uuid", id.toString());
        values.put("reason", clean(reason));
        if (!temp && template.toLowerCase(Locale.ROOT).contains("{ip}")) {
            String ip = ipOf(id);
            if (ip == null) {
                engine.platform().logger().warn("Ladder action ipban for " + name + ": no IP address known (player offline and none stored, or "
                    + "known-players.store-ip is off); {ip} was replaced by the player name.");
                ip = engine.bedrock().commandName(name);
            }
            values.put("ip", ip);
        }
        if (temp) {
            values.put("duration", formatDuration(millis, singleUnit(provider)));
            values.put("seconds", Long.toString(Math.max(1, (millis + 999) / 1000)));
            values.put("millis", Long.toString(millis));
        }
        String line = Placeholders.apply(template, values).trim();
        return line.startsWith("/") ? line.substring(1) : line;
    }

    /** A timed ban. When the provider has no tempban command the fallback (kick, mute or nothing) runs and a warning is logged. */
    public Result tempban(UUID id, String name, long millis, String reason) {
        String provider = provider();
        String line = command(provider, true, id, name, millis, reason);
        String shown = DurationParser.format(millis);
        if (line != null) {
            return new Result("a tempban (" + shown + ")", () -> engine.platform().runConsoleCommand(line));
        }
        String fallback = engine.value(IntegrationsSettings.FALLBACK);
        engine.platform().logger().warn("Ladder action tempban for " + name + ": provider '" + provider + "' has no tempban command"
            + " (integrations.bans." + provider + ".tempban is empty); fallback: " + fallback + ".");
        switch (fallback) {
            case IntegrationsSettings.FALLBACK_KICK:
                return new Result("a kick", () -> engine.platform().kick(id, Names.colour(clean(reason))));
            case IntegrationsSettings.FALLBACK_MUTE:
                if (engine.muteHandler().mute(id, name, millis, clean(reason), MuteHandler.SYSTEM)) {
                    return new Result("a mute (" + shown + ")", null);
                }
                return new Result(null, null);
            default:
                return new Result(null, null);
        }
    }

    /** An IP ban (the provider's command normally takes the player's name). Without a template nothing happens and a warning is logged. */
    public Result ipban(UUID id, String name, String reason) {
        String provider = provider();
        String line = command(provider, false, id, name, 0, reason);
        if (line == null) {
            engine.platform().logger().warn("Ladder action ipban for " + name + ": provider '" + provider.toLowerCase(Locale.ROOT)
                + "' has no ipban command (integrations.bans." + provider + ".ipban is empty); nothing was done.");
            return new Result(null, null);
        }
        return new Result("an IP ban", () -> engine.platform().runConsoleCommand(line));
    }
}
