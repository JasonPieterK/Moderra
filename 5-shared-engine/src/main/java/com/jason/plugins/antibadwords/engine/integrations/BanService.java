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
        String chosen = engine.value(BanBedrockSettings.PROVIDER);
        if (!BanBedrockSettings.AUTO.equals(chosen)) {
            return chosen;
        }
        Platform platform = engine.platform();
        if (platform.hasPlugin("LiteBans")) {
            return BanBedrockSettings.LITEBANS;
        }
        if (platform.hasPlugin("AdvancedBan")) {
            return BanBedrockSettings.ADVANCEDBAN;
        }
        if (platform.hasPlugin("LibertyBans")) {
            return BanBedrockSettings.LIBERTYBANS;
        }
        if (platform.hasPlugin("Essentials")) {
            return BanBedrockSettings.ESSENTIALS;
        }
        return BanBedrockSettings.VANILLA;
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
        String format = engine.value(BanBedrockSettings.DURATION_FORMAT);
        return "single".equals(format) || (BanBedrockSettings.AUTO.equals(format) && BanBedrockSettings.ADVANCEDBAN.equals(provider));
    }

    private String template(String provider, boolean temp) {
        return switch (provider) {
            case BanBedrockSettings.ESSENTIALS -> engine.value(temp ? BanBedrockSettings.ESSENTIALS_TEMPBAN : BanBedrockSettings.ESSENTIALS_IPBAN);
            case BanBedrockSettings.LITEBANS -> engine.value(temp ? BanBedrockSettings.LITEBANS_TEMPBAN : BanBedrockSettings.LITEBANS_IPBAN);
            case BanBedrockSettings.ADVANCEDBAN -> engine.value(temp ? BanBedrockSettings.ADVANCEDBAN_TEMPBAN : BanBedrockSettings.ADVANCEDBAN_IPBAN);
            case BanBedrockSettings.LIBERTYBANS -> engine.value(temp ? BanBedrockSettings.LIBERTYBANS_TEMPBAN : BanBedrockSettings.LIBERTYBANS_IPBAN);
            case BanBedrockSettings.CUSTOM -> engine.value(temp ? BanBedrockSettings.CUSTOM_TEMPBAN : BanBedrockSettings.CUSTOM_IPBAN);
            default -> engine.value(temp ? BanBedrockSettings.VANILLA_TEMPBAN : BanBedrockSettings.VANILLA_IPBAN);
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
                engine.platform().logger().warn("Ladder action ipban for " + name + ": notice. No IP address is known. The player is offline and no IP is stored, "
                    + "or known-players.store-ip is off. The plugin uses the player name instead of {ip}.");
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
        String fallback = engine.value(BanBedrockSettings.FALLBACK);
        engine.platform().logger().warn("Ladder action tempban for " + name + ": notice. The provider '" + provider + "' has no tempban command"
            + " (integrations.bans." + provider + ".tempban is empty). The fallback is: " + fallback + ".");
        switch (fallback) {
            case BanBedrockSettings.FALLBACK_KICK:
                return new Result("a kick", () -> engine.platform().kick(id, Names.colour(clean(reason))));
            case BanBedrockSettings.FALLBACK_MUTE:
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
            engine.platform().logger().warn("Ladder action ipban for " + name + ": notice. The provider '" + provider.toLowerCase(Locale.ROOT)
                + "' has no ipban command (integrations.bans." + provider + ".ipban is empty). The plugin does nothing.");
            return new Result(null, null);
        }
        return new Result("an IP ban", () -> engine.platform().runConsoleCommand(line));
    }
}
