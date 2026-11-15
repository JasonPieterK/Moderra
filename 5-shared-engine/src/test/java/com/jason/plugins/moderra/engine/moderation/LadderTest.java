package com.jason.plugins.moderra.engine.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.config.YamlTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading the steps and deciding which ones a change of strikes reaches (no engine involved). */
class LadderTest {
    private static final String STEPS = """
        kick:
          at: 6
          repeat: 2
          actions: ["kick Watch your language"]
        warn:
          at: 2
          repeat: once
          message: "&ewarned"
          actions: [warn]
        mute:
          at: 4
          actions:
            - mute 10m Bad language
            - sound minecraft:entity.villager.no 1 1
        """;

    private final List<String> warnings = new ArrayList<>();

    private Ladder ladder(String yaml) {
        return Ladder.parse(YamlTree.parse(yaml).root(), warnings::add);
    }

    private static List<String> names(List<Ladder.Step> steps) {
        return steps.stream().map(Ladder.Step::name).toList();
    }

    @Test
    void readsStepsSortedByThreshold() {
        Ladder ladder = ladder(STEPS);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(List.of("warn", "mute", "kick"), names(ladder.steps()));
        assertEquals(2, ladder.firstThreshold());
        Ladder.Step mute = ladder.steps().get(1);
        assertEquals(4, mute.threshold());
        assertFalse(mute.repeats());
        assertEquals(new Ladder.Action("mute", List.of("600000", "Bad language"), "mute 10m Bad language"), mute.actions().get(0));
        assertEquals(List.of("minecraft:entity.villager.no", "1", "1"), mute.actions().get(1).args());
        assertEquals(2, ladder.steps().get(2).repeatEvery());
        assertEquals("&ewarned", ladder.steps().get(0).message());
    }

    @Test
    void emptyMeansNoLadder() {
        assertTrue(ladder("{}").isEmpty());
        assertTrue(Ladder.parse(null, warnings::add).isEmpty());
        assertTrue(Ladder.empty().reached(0, 100).isEmpty());
        assertTrue(Ladder.empty().next(0).isEmpty());
    }

    @Test
    void thresholdsAreReachedOnTheWayUp() {
        Ladder ladder = ladder(STEPS);
        assertEquals(List.of(), names(ladder.reached(0, 1)));
        assertEquals(List.of("warn"), names(ladder.reached(1, 2)));
        assertEquals(List.of(), names(ladder.reached(2, 3)), "once: not again above the threshold");
        assertEquals(List.of("mute"), names(ladder.reached(3, 4)));
        assertEquals(List.of("warn", "mute"), names(ladder.reached(0, 5)), "a heavy violation reaches both, lowest first");
        assertEquals(List.of(), names(ladder.reached(4, 4)), "no change, nothing happens");
        assertEquals(List.of(), names(ladder.reached(5, 3)), "going down never punishes");
        assertEquals(List.of("warn"), names(ladder.reached(1, 2)), "after fading below the threshold it is reached again");
    }

    @Test
    void repeatingStepComesBackEveryNPoints() {
        Ladder ladder = ladder(STEPS);
        assertEquals(List.of("kick"), names(ladder.reached(5, 6)));
        assertEquals(List.of(), names(ladder.reached(6, 7)));
        assertEquals(List.of("kick"), names(ladder.reached(7, 8)));
        assertEquals(List.of("kick"), names(ladder.reached(8, 10)));
        assertEquals(List.of("kick"), names(ladder.reached(6, 12)), "passed three times by one change: happens once");
        assertEquals(List.of(), names(ladder.reached(8, 9)));
    }

    @Test
    void nextStepAndLimit() {
        Ladder ladder = ladder(STEPS);
        assertEquals("warn", ladder.next(0).orElseThrow().step().name());
        assertEquals(2, ladder.next(0).orElseThrow().at());
        assertEquals(4, ladder.next(2).orElseThrow().at());
        assertEquals(6, ladder.next(5).orElseThrow().at());
        assertEquals(8, ladder.next(6).orElseThrow().at());
        assertEquals(10, ladder.next(9).orElseThrow().at());
        assertEquals(2, ladder.limit(0));
        assertEquals(2, ladder.limit(2), "the trigger point the player is on counts");
        assertEquals(4, ladder.limit(3));
        assertEquals(8, ladder.limit(7));

        Ladder onceOnly = ladder("a:\n  at: 3\n  actions: [warn]\n");
        assertTrue(onceOnly.next(3).isEmpty());
        assertEquals(3, onceOnly.limit(9), "nothing more can happen: the highest threshold");
    }

    @Test
    void repeatSpellings() {
        Ladder ladder = ladder("a:\n  at: 1\n  repeat: every 3\n  actions: warn\nb:\n  at: 2\n  repeat: \"5\"\n  actions: [warn]\n"
            + "c:\n  at: 3\n  repeat: sometimes\n  actions: [warn]\n");
        assertEquals(List.of(3, 5, 0), ladder.steps().stream().map(Ladder.Step::repeatEvery).toList());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("ladder.steps.c.repeat"), warnings.get(0));
        assertEquals(1, ladder.steps().get(0).actions().size(), "one action may be written without a list");
    }

    @Test
    void wrongStepsAndActionsAreLeftOutAndReported() {
        Ladder ladder = ladder("""
            no-number:
              at: soon
              actions: [warn]
            zero:
              at: 0
              actions: [warn]
            not-a-section: kick
            empty:
              at: 3
            half:
              at: 4
              actions: [fly away, "mute", "mute tomorrow", "mute 0s", "command", "effect speed 1", "sound x loud 1", kick]
            """);
        assertEquals(List.of("half"), names(ladder.steps()));
        assertEquals(List.of("kick"), ladder.steps().get(0).actions().stream().map(Ladder.Action::type).toList());
        assertEquals(11, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unknown action 'fly'")));
    }

    @Test
    void everyActionKind() {
        assertEquals(List.of(""), Ladder.action("warn").args());
        assertEquals(List.of("&cStop it"), Ladder.action("WARN &cStop it").args());
        assertEquals(List.of("-1", ""), Ladder.action("mute perm").args());
        assertEquals(List.of("5400000", "be nice"), Ladder.action("mute 1h30m be nice").args());
        assertEquals(List.of(""), Ladder.action("kick").args());
        assertEquals(List.of("Too much"), Ladder.action("ban Too much").args());
        assertEquals(List.of("tempban {player} 1d {reason}"), Ladder.action("command tempban {player} 1d {reason}").args());
        assertEquals(List.of("minecraft:blindness", "0", "10"), Ladder.action("effect minecraft:blindness 0 10").args());
        assertEquals(List.of("minecraft:movement_speed", "0.05", "10"), Ladder.action("attribute minecraft:movement_speed 0.05 10").args());
        assertEquals("punishments", Ladder.action("punishments").type());
        assertEquals("reset-strikes", Ladder.action("reset").type());
        assertThrows(IllegalArgumentException.class, () -> Ladder.action(" "));
    }
}
