package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.moderation.Ladder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Before/after proof for the classic flat setup. "Before" is the flat flow (no steps, what every existing install runs);
 * "after" is the same install with the equivalent one-step ladder written into {@code ladder.yml} (the step the
 * documentation shows). Both must send the same messages in the same order, run the same commands and actions, set the
 * strikes back the same way and publish the same punishment events.
 */
class LadderMigrationTest {
    private static final String LADDER = "moderation/ladder.yml";
    private static final String EQUIVALENT_STEP = """
        steps:
            legacy:
              at: 3
              repeat: 1
              message: "&8[&6&lModerra&8]&r &c&lPenalty. &7You used too many words that are not permitted. The penalty is: &f{PUNISHMENTS}&7."
              broadcast: "&8[&6&lModerra&8]&r &c&lPenalty. &f{PLAYER}&7 used a word that is not permitted: &c&o{WORD}&7. The penalty is: &f{PUNISHMENTS}&7."
              final-command: "/tempmute {player} 30s {word}"
              actions: [punishments, reset-strikes]
        """;

    @TempDir
    Path dir;

    /** What one server run showed after each swear word. */
    private record Round(List<String> toPlayer, List<String> broadcasts, List<String> commands, List<String> actions, int strikes,
                         List<Map<String, String>> events) {
    }

    private List<Round> run(String name, boolean explicitLadder) {
        Fixture f = new Fixture(dir.resolve(name));
        f.start();
        String text = TestFiles.read(f.file(LADDER));
        text = text.replace("violations-before-punish: 2", "violations-before-punish: 3");
        text = after(text, "final-command:", "enabled: false", "enabled: true");
        text = after(text, "final-command:", "command: \"\"", "command: \"/tempmute {player} 30s {word}\"");
        text = after(text, "blindness:", "enabled: false", "enabled: true");
        text = after(text, "    mute:", "enabled: false", "enabled: true");
        TestFiles.write(f.file(LADDER), text);
        if (explicitLadder) {
            TestFiles.edit(f.file(LADDER), "steps: {}", EQUIVALENT_STEP.stripTrailing());
        }
        Engine engine = f.start();
        assertEquals(List.of(), engine.problems());
        assertEquals(!explicitLadder, engine.settings().ladder().isEmpty());
        List<ModerationEvent> events = new ArrayList<>();
        engine.events().subscribe(events::add);
        UUID bob = f.platform.join("Bob");
        List<Round> rounds = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            f.platform.sentTo(bob).clear();
            f.platform.broadcasts.clear();
            f.platform.consoleCommands.clear();
            f.platform.appliedActions.clear();
            events.clear();
            assertTrue(f.say(bob, Message.CHAT, "what the fuck").blocked());
            rounds.add(new Round(List.copyOf(f.platform.sentTo(bob)), List.copyOf(f.platform.broadcasts), List.copyOf(f.platform.consoleCommands),
                List.copyOf(f.platform.appliedActions), engine.strikes().get(bob),
                events.stream().filter(e -> e.kind().equals(ModerationEvent.PUNISHMENT)).map(e -> {
                    Map<String, String> data = new java.util.TreeMap<>();
                    for (String key : List.of("profile", "word", "source", "count", "punishments", "remaining", "player")) {
                        data.put(key, e.data().get(key));
                    }
                    return data;
                }).toList()));
        }
        return rounds;
    }

    /** Replaces the first {@code from} that follows {@code anchor} (the file has comments between the lines). */
    private static String after(String text, String anchor, String from, String to) {
        int at = text.indexOf(anchor);
        assertTrue(at >= 0, anchor);
        int hit = text.indexOf(from, at);
        assertTrue(hit >= 0, from);
        return text.substring(0, hit) + to + text.substring(hit + from.length());
    }

    @Test
    void theOneStepLadderBehavesLikeTheFlatSetup() {
        List<Round> before = run("before", false);
        List<Round> after = run("after", true);
        assertEquals(before, after);

        // the flat behaviour itself, as it was before the ladder existed (golden values)
        assertEquals(List.of("§8[§6§lModerra§8]§r §e§lWarning. §7Your message contains a word that is not permitted. After §f2§7 more violations, a penalty is applied."), before.get(0).toPlayer());
        assertEquals(1, before.get(0).strikes());
        Round punished = before.get(2);
        assertEquals(0, punished.strikes(), "strikes start again at 0");
        assertEquals(List.of("§8[§6§lModerra§8]§r §c§lPenalty. §7You used too many words that are not permitted. The penalty is: §f"
            + punished.events().get(0).get("punishments") + "§7."), punished.toPlayer());
        assertEquals(List.of("§8[§6§lModerra§8]§r §c§lPenalty. §fBob§7 used a word that is not permitted: §c§ofuck§7. The penalty is: §f"
            + punished.events().get(0).get("punishments") + "§7."), punished.broadcasts());
        assertEquals("tempmute Bob 30s fuck", punished.commands().get(punished.commands().size() - 1), "the final command runs last");
        assertEquals(1, punished.events().size());
        assertEquals("0", punished.events().get(0).get("remaining"));
        assertEquals(1, before.get(3).strikes(), "counting starts again");
        assertEquals(0, before.get(5).strikes(), "and punishes again after three more");
        assertEquals(1, before.get(6).strikes());
    }

    @Test
    void classicLadderIsWhatActiveLadderShowsForTheFlatSetup() {
        Fixture f = new Fixture(dir.resolve("show"));
        Engine engine = f.start();
        assertTrue(engine.settings().ladder().isEmpty());
        Ladder shown = engine.settings().activeLadder();
        assertEquals(1, shown.steps().size());
        Ladder.Step step = shown.steps().get(0);
        assertEquals(Ladder.CLASSIC_STEP, step.name());
        assertEquals(2, step.threshold(), "violations-before-punish");
        assertEquals(1, step.repeatEvery());
        assertEquals(List.of(Ladder.PUNISHMENTS, Ladder.RESET), step.actions().stream().map(Ladder.Action::type).toList());
        assertEquals("", step.finalCommand(), "final command is off by default");
    }
}
