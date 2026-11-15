package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The punishment ladder inside the violation flow. */
class LadderFlowTest {
    private static final String LADDER = "moderation/ladder.yml";
    private static final String STEPS = """
        steps:
            first-warning:
              at: 2
              repeat: once
              message: "&eLast warning, {player} ({count} strikes, step {step})."
              actions: [warn]
            short-mute:
              at: 3
              message: "&cMuted for {duration}: {reason}. You got {punishments}."
              broadcast: "&7{player} was muted."
              actions:
                - mute 10m No swearing
                - sound minecraft:entity.villager.no 1 1
                - command note {player} {uuid} {count} {threshold} {word}
            out:
              at: 4
              repeat: 2
              message: "&cBye."
              actions: [kick, "ban Repeated swearing"]
        """;

    @TempDir
    Path dir;
    Fixture f;
    UUID bob;
    final List<ModerationEvent> events = new ArrayList<>();
    final List<String> mutes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        TestFiles.edit(f.file(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
    }

    private void ladder(String steps) {
        TestFiles.edit(f.file(LADDER), "steps: {}", steps.stripTrailing());
        restart();
    }

    private void restart() {
        events.clear();
        Engine engine = f.start();
        assertEquals(List.of(), engine.problems());
        engine.events().subscribe(events::add);
        engine.setMuteHandler((player, name, millis, reason, actor) -> mutes.add(name + " " + DurationParser.format(millis) + " " + reason + " by " + actor));
        bob = f.platform.join("Bob");
    }

    private void swear() {
        f.platform.sentTo(bob).clear();
        f.platform.broadcasts.clear();
        assertTrue(f.say(bob, Message.CHAT, "fuck this").blocked());
    }

    private List<ModerationEvent> punishments() {
        return events.stream().filter(e -> e.kind().equals(ModerationEvent.PUNISHMENT)).toList();
    }

    @Test
    void stepsHappenInOrderAndStrikesKeepCounting() {
        ladder(STEPS);
        swear(); // 1
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §e§lWarning. §7Your message contains a word that is not permitted. After §f1§7 more violations, a penalty is applied."), f.platform.sentTo(bob));
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §c§lMessage blocked. §fBob§7 used a word that is not permitted: §c§ofuck§7."), f.platform.broadcasts);
        assertTrue(punishments().isEmpty());

        swear(); // 2: warn
        assertEquals(List.of("§eLast warning, Bob (2 strikes, step first-warning)."), f.platform.sentTo(bob));
        assertTrue(f.platform.broadcasts.isEmpty(), "a step sends only its own messages");
        assertEquals(2, f.engine.strikes().get(bob), "a ladder does not set the strikes back");
        assertEquals("first-warning", punishments().get(0).data().get("step"));
        assertEquals("2", punishments().get(0).data().get("threshold"));
        assertEquals("warn", punishments().get(0).data().get("actions"));
        assertEquals("default", punishments().get(0).data().get("profile"));

        swear(); // 3: mute + sound + command
        assertEquals(List.of("Bob 10m No swearing by system"), mutes);
        assertEquals(List.of("sound:short-mute"), f.platform.appliedActions);
        assertEquals(List.of("note Bob " + bob + " 3 3 fuck"), f.platform.consoleCommands);
        assertEquals(List.of("§cMuted for 10m: No swearing. You got a mute (10m), villager no sound, and server command."), f.platform.sentTo(bob));
        assertEquals(List.of("§7Bob was muted."), f.platform.broadcasts);
        assertEquals("10m", punishments().get(1).data().get("duration"));
        assertEquals("No swearing", punishments().get(1).data().get("reason"));

        f.platform.consoleCommands.clear();
        swear(); // 4: kick + ban, after the message
        assertEquals(List.of("§cBye."), f.platform.sentTo(bob));
        assertEquals(List.of("Bob: Language that is not permitted"), f.platform.kicks, "kick without a reason uses ladder.default-reason");
        assertEquals(List.of("ban Bob Repeated swearing"), f.platform.consoleCommands);

        swear(); // 5: between two repeats
        assertEquals(1, f.platform.kicks.size());
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §e§lWarning. §7Your message contains a word that is not permitted. After §f1§7 more violations, a penalty is applied."), f.platform.sentTo(bob));
        swear(); // 6: repeat
        assertEquals(2, f.platform.kicks.size());
        assertEquals(List.of("first-warning", "short-mute", "out", "out"), punishments().stream().map(e -> e.data().get("step")).toList());
        assertEquals(6, f.engine.strikes().get(bob));
        assertEquals(1, mutes.size(), "a once step does not come back");
    }

    @Test
    void heavyViolationReachesSeveralStepsOrOnlyTheHighest() {
        ladder(STEPS);
        // two steps share the threshold 2: one violation reaches both
        TestFiles.edit(f.file(LADDER), "at: 3", "at: 2");
        restart();
        f.engine.strikes().add(bob, 1);
        swear();
        assertEquals(List.of("first-warning", "short-mute"), punishments().stream().map(e -> e.data().get("step")).toList());

        TestFiles.edit(f.file(LADDER), "when-several-steps-are-reached: all", "when-several-steps-are-reached: highest");
        restart();
        f.engine.strikes().reset(bob);
        f.engine.strikes().add(bob, 1);
        swear();
        assertEquals(List.of("short-mute"), punishments().stream().map(e -> e.data().get("step")).toList());
    }

    @Test
    void punishmentOffMeansNoStep() {
        ladder(STEPS);
        TestFiles.edit(f.file(LADDER), "enabled: true", "enabled: false");
        restart();
        swear();
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §e§lWarning. §7Your message contains a word that is not permitted. After §f1§7 more violations, a penalty is applied."), f.platform.sentTo(bob));
        swear();
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §c§lMessage blocked. §7Your message contains a word that is not permitted. §7§oNo penalty is applied at this time."), f.platform.sentTo(bob));
        assertTrue(punishments().isEmpty());
        assertEquals(2, f.engine.strikes().get(bob));
    }

    @Test
    void muteWithoutTheMuteFeatureOnlyLogs() {
        ladder("steps:\n    m:\n      at: 1\n      message: \"{punishments}\"\n      actions: [mute perm]\n");
        f.engine.setMuteHandler(null);
        swear();
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.startsWith("Cannot mute Bob (permanent)")), f.platform.warnings.toString());
        assertEquals(List.of("punishment"), f.platform.sentTo(bob));
        assertEquals(1, punishments().size());
    }

    @Test
    void aFailingActionDoesNotStopTheStep() {
        ladder("steps:\n    m:\n      at: 1\n      message: \"done\"\n      actions: [\"mute 5m\", \"kick &cOut\"]\n");
        f.engine.setMuteHandler((player, name, millis, reason, actor) -> {
            throw new IllegalStateException("boom");
        });
        swear();
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("'mute 5m' of ladder step 'm'")));
        assertEquals(List.of("done"), f.platform.sentTo(bob));
        assertEquals(List.of("Bob: §cOut"), f.platform.kicks);
    }

    @Test
    void laddersCanDifferPerProfile() {
        TestFiles.write(f.file("worlds.yml"), """
            default-profile: default
            use-permission-profiles: false
            profiles:
              default: {}
              strict:
                ladder.steps:
                  out:
                    at: 1
                    repeat: 1
                    actions: [kick]
              classic:
                ladder.steps: {}
            worlds:
              hub: strict
              old: classic
            """);
        ladder(STEPS);
        f.platform.worlds.put(bob, "hub");
        swear();
        assertEquals(List.of("Bob: Language that is not permitted"), f.platform.kicks);
        assertEquals("strict", punishments().get(0).data().get("profile"));

        f.platform.worlds.put(bob, "world");
        swear(); // 2 strikes: the default profile's warning
        assertEquals("first-warning", punishments().get(1).data().get("step"));

        f.platform.worlds.put(bob, "old");
        swear(); // 3 strikes >= violations-before-punish 2: the classic setup of that profile, strikes start again
        assertEquals("legacy", punishments().get(2).data().get("step"));
        assertEquals(List.of("sound:villager_no"), f.platform.appliedActions);
        assertEquals(0, f.engine.strikes().get(bob));
        assertTrue(mutes.isEmpty());
    }

    @Test
    void classicSetupWrittenAsOneStepDoesTheSame() {
        // flat setup (no steps): warn, punish with the enabled actions, strikes back to 0
        restart();
        swear();
        swear();
        List<String> flatActions = List.copyOf(f.platform.appliedActions);
        assertEquals(List.of("sound:villager_no"), flatActions);
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §c§lPenalty. §7You used too many words that are not permitted. The penalty is: §fvillager no sound§7."), f.platform.sentTo(bob));
        assertEquals(0, f.engine.strikes().get(bob));
        assertEquals("legacy", punishments().get(0).data().get("step"));
        assertEquals("2", punishments().get(0).data().get("threshold"));
        swear();
        assertEquals(1, f.engine.strikes().get(bob));
        assertEquals(1, punishments().size());
        assertFalse(f.platform.infos.stream().anyMatch(i -> i.startsWith("Punishment ladder")));

        // the same as a one-step ladder
        f.platform.appliedActions.clear();
        f.engine.strikes().reset(bob);
        ladder("""
            steps:
                classic:
                  at: 2
                  repeat: once
                  message: "&8[&6&lAntiBadWords&8]&r &c&lPenalty. &7You used too many words that are not permitted. The penalty is: &f{PUNISHMENTS}&7."
                  actions: [punishments, reset-strikes]
            """);
        assertTrue(f.platform.infos.contains("Punishment ladder: 1 steps (the first at 2 strikes)."));
        swear();
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §e§lWarning. §7Your message contains a word that is not permitted. After §f1§7 more violations, a penalty is applied."), f.platform.sentTo(bob));
        swear();
        assertEquals(flatActions, f.platform.appliedActions);
        assertEquals(List.of("§8[§6§lAntiBadWords§8]§r §c§lPenalty. §7You used too many words that are not permitted. The penalty is: §fvillager no sound§7."), f.platform.sentTo(bob));
        assertEquals(0, f.engine.strikes().get(bob));
        swear();
        swear();
        assertEquals(2, punishments().size(), "after the reset the step is reached again");
    }

    @Test
    void wrongStepsAreReportedAtLoad() {
        ladder("steps:\n    bad:\n      at: 2\n      actions: [explode]\n    ok:\n      at: 1\n      actions: [warn]\n");
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("ladder.steps.bad.actions: 'explode' is ignored")),
            f.platform.warnings.toString());
        assertEquals(1, f.engine.settings().ladder().steps().size());
    }
}
