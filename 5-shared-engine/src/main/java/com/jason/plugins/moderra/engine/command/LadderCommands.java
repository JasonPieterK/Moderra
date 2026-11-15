package com.jason.plugins.moderra.engine.command;

import static com.jason.plugins.moderra.engine.command.Chat.GOLD;
import static com.jason.plugins.moderra.engine.command.Chat.BOLD;
import static com.jason.plugins.moderra.engine.command.Chat.GRAY;
import static com.jason.plugins.moderra.engine.command.Chat.RED;
import static com.jason.plugins.moderra.engine.command.Chat.WHITE;
import static com.jason.plugins.moderra.engine.command.Chat.YELLOW;

import com.jason.plugins.moderra.engine.EffectiveSettings;
import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.PlayerRef;
import com.jason.plugins.moderra.engine.moderation.Ladder;
import com.jason.plugins.moderra.engine.moderation.LadderSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code /moderra ladder [profile]} and {@code /moderra strikes <player>}. Both need {@code moderra.admin}. */
public final class LadderCommands {
    private static final int MAX_OFFLINE_SUGGESTIONS = 50;

    private LadderCommands() {
    }

    public static void register(CommandRegistry r) {
        r.register(new Ladders());
        r.register(new Strikes());
    }

    /** The profile that applies to a player (their world and permissions), or the default profile. */
    private static EffectiveSettings settingsOf(Engine engine, UUID player) {
        if (player == null || !engine.isOnline(player)) {
            return engine.settings();
        }
        return engine.settings(player, engine.platform().worldName(player), engine.platform().dimensionName(player),
            node -> engine.platform().has(player, node));
    }

    private static String actions(Ladder.Step step) {
        List<String> parts = new ArrayList<>(step.actions().stream().map(Ladder.Action::text).toList());
        if (!step.finalCommand().isBlank()) {
            parts.add("command " + step.finalCommand());
        }
        return parts.isEmpty() ? "(messages only)" : String.join("; ", parts);
    }

    private static String when(Ladder.Step step) {
        return step.threshold() + (step.repeats() ? (step.repeatEvery() == 1 ? " and every strike after" : ", then every " + step.repeatEvery()) : "") + " strikes";
    }

    private static final class Ladders implements SubCommand {
        @Override
        public String name() {
            return "ladder";
        }

        @Override
        public void execute(CommandContext c) {
            Engine engine = c.engine();
            EffectiveSettings settings;
            if (c.args().length >= 1) {
                settings = engine.profile(c.args()[0]);
                if (settings == null) {
                    c.send(Chat.error("This profile does not exist. Known profiles: " + Chat.val(String.join(", ", engine.profileNames())) + ".")); 
                    return;
                }
            } else {
                settings = settingsOf(engine, c.sender().id());
            }
            boolean explicit = !settings.ladder().isEmpty();
            Ladder ladder = settings.activeLadder();
            c.send(Chat.heading("Punishment ladder") + GRAY + " (profile " + WHITE + settings.profile() + GRAY + ", "
                + (explicit ? ladder.steps().size() + " step" + (ladder.steps().size() == 1 ? "" : "s") : "classic setup, no steps in ladder.yml") + ")");
            if (!settings.legacy().punishmentEnabled()) {
                c.send(Chat.error("Punishment is switched off (punishment.enabled): bad words are blocked, nobody is punished."));
            }
            for (Ladder.Step step : ladder.steps()) {
                c.send(YELLOW + BOLD + "At " + when(step) + GRAY + ": " + WHITE + step.name());
                c.send(GRAY + "   " + (explicit || step.actions().isEmpty() ? actions(step) : classicActions(settings, step)));
            }
            if (explicit && ladder.steps().size() > 1) {
                c.send(GRAY + "One violation can reach several steps. Applied steps: "
                    + (LadderSettings.HIGHEST.equals(settings.get(LadderSettings.SEVERAL_STEPS)) ? "only the highest" : "all, lowest first") + ".");
            }
            if (!explicit) {
                c.send(Chat.hint("After a penalty, the strikes return to 0. To change this, write steps under ladder.steps in moderation/ladder.yml."));
            }
        }

        private static String classicActions(EffectiveSettings settings, Ladder.Step step) {
            List<String> enabled = settings.legacy().punishmentActions().stream().filter(a -> a.enabled()).map(a -> a.id()).toList();
            String list = enabled.isEmpty() ? "no punishment action is enabled" : "enabled actions: " + String.join(", ", enabled);
            return list + (step.finalCommand().isBlank() ? "" : "; command " + step.finalCommand()) + "; strikes reset";
        }

        @Override
        public List<String> complete(CommandContext c) {
            return c.args().length == 1 ? CommandRegistry.prefix(new ArrayList<>(c.engine().profileNames()), c.args()[0]) : List.of();
        }
    }

    private static final class Strikes implements SubCommand {
        @Override
        public String name() {
            return "strikes";
        }

        @Override
        public void execute(CommandContext c) {
            if (c.args().length < 1) {
                c.usage("<player>");
                return;
            }
            Engine engine = c.engine();
            PlayerRef target = engine.resolvePlayer(c.args()[0]).orElse(null);
            if (target == null) {
                c.send(Chat.error("Player not found (not online and never seen on this server)."));
                return;
            }
            boolean online = engine.isOnline(target.id());
            int points = engine.strikes().get(target.id());
            EffectiveSettings settings = settingsOf(engine, target.id());
            Ladder ladder = settings.activeLadder();
            c.send(YELLOW + BOLD + target.name() + GRAY + ": " + WHITE + points + GRAY + " strike" + (points == 1 ? "" : "s")
                + GRAY + " (profile " + settings.profile() + ")" + (online ? "" : GRAY + " (offline)"));
            if (!settings.legacy().punishmentEnabled()) {
                c.send(GRAY + "Punishment is off in this profile. No step is applied.");
                return;
            }
            Optional<Ladder.Next> next = ladder.next(points);
            if (next.isEmpty()) {
                c.send(GRAY + "No more steps. The ladder has no step above " + points + " strikes.");
                return;
            }
            int at = next.get().at();
            c.send(GRAY + "Next step: " + WHITE + next.get().step().name() + GRAY + " at " + WHITE + at + GRAY + " strikes ("
                + (at - points) + " more). Actions: " + actions(next.get().step()));
        }

        @Override
        public List<String> complete(CommandContext c) {
            if (c.args().length != 1) {
                return List.of();
            }
            List<String> names = new ArrayList<>(CommandRegistry.prefix(
                c.engine().platform().onlinePlayers().stream().map(PlayerRef::name).toList(), c.args()[0]));
            for (String known : c.engine().players().names(c.args()[0], MAX_OFFLINE_SUGGESTIONS)) {
                if (names.stream().noneMatch(known::equalsIgnoreCase)) {
                    names.add(known);
                }
            }
            return names;
        }
    }
}
