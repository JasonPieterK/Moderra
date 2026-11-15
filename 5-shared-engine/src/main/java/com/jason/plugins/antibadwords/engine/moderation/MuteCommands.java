package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.engine.command.Chat;
import static com.jason.plugins.antibadwords.engine.command.Chat.GRAY;
import static com.jason.plugins.antibadwords.engine.command.Chat.WHITE;
import static com.jason.plugins.antibadwords.engine.command.Chat.GREEN;
import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.engine.PlayerRef;
import com.jason.plugins.antibadwords.engine.command.CommandContext;
import com.jason.plugins.antibadwords.engine.command.CommandRegistry;
import com.jason.plugins.antibadwords.engine.command.SubCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** {@code /abw mute <player> [duration] [reason...]}, {@code /abw unmute <player>}, {@code /abw mutes [page]}. */
public final class MuteCommands {
    static final int PAGE_SIZE = 8;
    private static final int MAX_OFFLINE_SUGGESTIONS = 50;
    private static final List<String> DURATION_HINTS = List.of("10m", "1h", "1d", "7d", "perm");

    private MuteCommands() {
    }

    public static void register(CommandRegistry registry) {
        registry.register(new Mute());
        registry.register(new Unmute());
        registry.register(new Mutes());
    }

    private abstract static class Base implements SubCommand {
        @Override
        public String permission() {
            return MuteService.PERMISSION;
        }
    }

    private static final class Mute extends Base {
        @Override
        public String name() {
            return "mute";
        }

        @Override
        public void execute(CommandContext c) {
            if (c.args().length < 1) {
                c.usage("<player> [duration: 30s 10m 1h30m 2d perm] [reason...]");
                return;
            }
            MuteService service = c.engine().mutes();
            PlayerRef target = c.engine().resolvePlayer(c.args()[0])
                .or(() -> service.findByName(c.args()[0]).map(m -> new PlayerRef(m.id(), m.name()))).orElse(null);
            if (target == null) {
                c.send(Chat.error("Player not found (not online and never seen on this server)."));
                return;
            }
            OptionalLong parsed = c.args().length >= 2 ? DurationParser.tryParse(c.args()[1]) : OptionalLong.empty();
            long millis = parsed.isPresent() ? parsed.getAsLong() : c.engine().value(MuteSettings.DEFAULT_DURATION).toMillis();
            String reason = c.joined(parsed.isPresent() ? 2 : 1);
            if (!c.engine().value(MuteSettings.ENABLED)) {
                c.send(Chat.error("The built-in mute is switched off (mute.enabled in moderation/mute.yml)."));
                return;
            }
            if (millis == 0 || !service.mute(target.id(), target.name(), millis, reason, c.sender().name())) {
                c.send(Chat.error("The plugin cannot mute " + Chat.val(target.name()) + "."));
                return;
            }
            c.send(Chat.ok(Chat.val(target.name()) + " is muted for " + Chat.val(DurationParser.format(millis))
                + (reason.isEmpty() ? "" : " (" + reason + ")") + "." + (c.engine().isOnline(target.id()) ? "" : GRAY + " (offline)")));
            // online: answered at once; offline: by the permission plugin (LuckPerms) in the background, the answer is sent on the main thread
            c.engine().hasPermission(target.id(), MuteService.BYPASS_PERMISSION).thenAccept(has -> {
                if (has) {
                    c.engine().platform().runSync(() -> c.send(Chat.warn(Chat.val(target.name()) + " has the permission " + Chat.val(MuteService.BYPASS_PERMISSION)
                        + ". The mute does not stop this player.")));
                }
            });
            c.audit();
        }

        @Override
        public List<String> complete(CommandContext c) {
            if (c.args().length == 1) {
                return playerNames(c);
            }
            return c.args().length == 2 ? CommandRegistry.prefix(DURATION_HINTS, c.args()[1]) : List.of();
        }
    }

    private static final class Unmute extends Base {
        @Override
        public String name() {
            return "unmute";
        }

        @Override
        public void execute(CommandContext c) {
            if (c.args().length < 1) {
                c.usage("<player>");
                return;
            }
            MuteService service = c.engine().mutes();
            PlayerRef target = service.findByName(c.args()[0]).map(m -> new PlayerRef(m.id(), m.name()))
                .or(() -> c.engine().resolvePlayer(c.args()[0])).orElse(null);
            if (target == null) {
                c.send(Chat.error("Player not found (not online and never seen on this server)."));
            } else if (service.unmute(target.id(), c.sender().name())) {
                c.send(Chat.ok(Chat.val(target.name()) + " is unmuted." + (c.engine().isOnline(target.id()) ? "" : GRAY + " (offline)")));
                c.audit();
            } else {
                c.send(Chat.error(Chat.val(target.name()) + " is not muted."));
            }
        }

        @Override
        public List<String> complete(CommandContext c) {
            return c.args().length == 1
                ? CommandRegistry.prefix(c.engine().mutes().list().stream().map(MuteService.Mute::name).toList(), c.args()[0]) : List.of();
        }
    }

    private static final class Mutes extends Base {
        @Override
        public String name() {
            return "mutes";
        }

        @Override
        public void execute(CommandContext c) {
            List<MuteService.Mute> all = c.engine().mutes().list();
            if (all.isEmpty()) {
                c.send(Chat.ok("Nobody is muted."));
                return;
            }
            int pages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            int page = 1;
            if (c.args().length >= 1) {
                try {
                    page = Integer.parseInt(c.args()[0]);
                } catch (NumberFormatException ex) {
                    c.usage("[page]");
                    return;
                }
            }
            if (page < 1 || page > pages) {
                c.send(Chat.error("The list has " + (pages == 1 ? "only 1 page." : "only " + pages + " pages.")));
                return;
            }
            long now = c.engine().now();
            c.send(Chat.heading("Muted players") + GRAY + " (" + WHITE + all.size() + GRAY + "), page " + WHITE + page + GRAY + " of " + WHITE + pages + GRAY + ":");
            for (MuteService.Mute m : all.subList((page - 1) * PAGE_SIZE, Math.min(all.size(), page * PAGE_SIZE))) {
                c.send(GRAY + "- " + RED + m.name() + GRAY + " " + DurationParser.format(m.remaining(now)) + " remaining, by " + m.by()
                    + (m.reason().isEmpty() ? "" : ", " + m.reason()));
            }
        }

        @Override
        public List<String> complete(CommandContext c) {
            return List.of();
        }
    }

    /** Online players first, then known offline players. */
    private static List<String> playerNames(CommandContext c) {
        List<String> names = new ArrayList<>(
            CommandRegistry.prefix(c.engine().platform().onlinePlayers().stream().map(PlayerRef::name).toList(), c.args()[0]));
        for (String known : c.engine().players().names(c.args()[0], MAX_OFFLINE_SUGGESTIONS)) {
            if (names.stream().noneMatch(known::equalsIgnoreCase)) {
                names.add(known);
            }
        }
        return names;
    }
}
