package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code /abw ladder} and {@code /abw strikes}. */
class LadderCommandsTest {
    private static final String LADDER = "moderation/ladder.yml";

    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    final CommandsTest.Sender admin = new CommandsTest.Sender(Engine.PERMISSION_ADMIN);

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        TestFiles.edit(f.file(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        engine = f.start();
    }

    private List<String> run(CommandsTest.Sender sender, String line) {
        sender.out.clear();
        engine.commands().execute(sender, "abw", line.split(" "));
        return new ArrayList<>(sender.out);
    }

    private List<String> run(String line) {
        return run(admin, line);
    }

    private void steps() {
        TestFiles.edit(f.file(LADDER), "steps: {}", """
            steps:
                warn-first:
                  at: 2
                  actions: [warn]
                short-mute:
                  at: 4
                  repeat: 3
                  actions: ["mute 10m No swearing", "sound minecraft:entity.villager.no 1 1"]
            """.stripTrailing());
        engine = f.start();
    }

    @Test
    void ladderShowsTheClassicSetupWhenThereAreNoSteps() {
        assertEquals(List.of(
            "§6Punishment ladder§7 (profile §fdefault§7, classic setup, no steps in ladder.yml)",
            "§eAt 2 and every strike after strikes§7: §flegacy",
            "§7   enabled actions: villager_no; strikes reset",
            "§7Strikes start again at 0 after the punishment. Write steps under ladder.steps in moderation/ladder.yml to change this."),
            run("ladder"));
    }

    @Test
    void ladderShowsTheStepsOfTheSendersProfile() {
        steps();
        List<String> out = run("ladder");
        assertEquals(List.of(
            "§6Punishment ladder§7 (profile §fdefault§7, 2 steps)",
            "§eAt 2 strikes§7: §fwarn-first",
            "§7   warn",
            "§eAt 4, then every 3 strikes§7: §fshort-mute",
            "§7   mute 10m No swearing; sound minecraft:entity.villager.no 1 1",
            "§7When one violation reaches several steps: all of them, lowest first."), out);

        // a player in another world sees that world's profile
        TestFiles.write(f.file("worlds.yml"), """
            default-profile: default
            use-permission-profiles: false
            profiles:
              default: {}
              strict:
                ladder.steps:
                  out:
                    at: 1
                    actions: [kick]
            worlds:
              hub: strict
            """);
        engine = f.start();
        UUID bob = f.platform.join("Bob");
        engine.onJoin(bob, "Bob");
        f.platform.worlds.put(bob, "hub");
        CommandsTest.Sender player = new CommandsTest.Sender(Engine.PERMISSION_ADMIN) {
            @Override
            public UUID id() {
                return bob;
            }
        };
        assertEquals(List.of("§6Punishment ladder§7 (profile §fstrict§7, 1 step)", "§eAt 1 strikes§7: §fout", "§7   kick"), run(player, "ladder"));
        assertEquals(out.get(0), run(admin, "ladder").get(0), "the console sees the default profile");
        assertEquals(List.of("§6Punishment ladder§7 (profile §fstrict§7, 1 step)", "§eAt 1 strikes§7: §fout", "§7   kick"), run("ladder strict"));
        assertEquals(List.of("§cNo such profile. Known: default, strict"), run("ladder nope"));
        assertEquals(List.of("default", "strict"), engine.commands().complete(admin, new String[] {"ladder", ""}));
        assertEquals(List.of("strict"), engine.commands().complete(admin, new String[] {"ladder", "st"}));
    }

    @Test
    void ladderSaysWhenPunishmentIsOff() {
        TestFiles.edit(f.file(LADDER), "enabled: true", "enabled: false");
        engine = f.start();
        assertEquals("§cPunishment is switched off (punishment.enabled): bad words are blocked, nobody is punished.", run("ladder").get(1));
    }

    @Test
    void strikesShowPointsAndTheNextStep() {
        steps();
        UUID bob = f.platform.join("Bob");
        engine.onJoin(bob, "Bob");
        engine.strikes().add(bob, 3);
        assertEquals(List.of("§eBob: §f3§e strikes§7 (profile default)",
            "§7Next step: §fshort-mute§7 at §f4§7 strikes (1 more): mute 10m No swearing; sound minecraft:entity.villager.no 1 1"), run("strikes Bob"));
        engine.strikes().reset(bob);
        engine.strikes().add(bob, 1);
        assertEquals("§7Next step: §fwarn-first§7 at §f2§7 strikes (1 more): warn", run("strikes bob").get(1));
        engine.strikes().add(bob, 4); // 5: the repeat at 7 is next
        assertEquals("§7Next step: §fshort-mute§7 at §f7§7 strikes (2 more): mute 10m No swearing; sound minecraft:entity.villager.no 1 1",
            run("strikes Bob").get(1));
    }

    @Test
    void strikesInTheClassicSetupAndWithoutAFurtherStep() {
        UUID bob = f.platform.join("Bob");
        engine.onJoin(bob, "Bob");
        assertEquals(List.of("§eBob: §f0§e strikes§7 (profile default)", "§7Next step: §flegacy§7 at §f2§7 strikes (2 more): punishments; reset-strikes"),
            run("strikes Bob"));
        TestFiles.edit(f.file(LADDER), "steps: {}", "steps:\n    only:\n      at: 2\n      actions: [warn]");
        engine = f.start();
        engine.strikes().add(bob, 2);
        assertEquals("§7No further step: the ladder has no trigger point above 2.", run("strikes Bob").get(1));
    }

    @Test
    void strikesWorkForOfflinePlayersAndUnknownNames() {
        UUID bob = f.platform.join("Bob");
        engine.onJoin(bob, "Bob");
        engine.strikes().add(bob, 1);
        engine.onJoin(bob, "Bob");
        engine.onQuit(bob);
        f.platform.players.remove(bob);
        assertEquals("§eBob: §f1§e strike§7 (profile default)§7 (offline)", run("strikes Bob").get(0));
        assertEquals(List.of("§cPlayer not found (not online and never seen on this server)."), run("strikes Nobody"));
        assertEquals(List.of("§eUsage: /abw strikes <player>"), run("strikes"));
        assertEquals(List.of("Bob"), engine.commands().complete(admin, new String[] {"strikes", "b"}));
        assertEquals(List.of(), engine.commands().complete(admin, new String[] {"strikes", "bob", ""}));
        TestFiles.edit(f.file(LADDER), "enabled: true", "enabled: false");
        engine = f.start();
        f.platform.join("Alice");
        assertEquals("§7Punishment is switched off in this profile, so no step will happen.", run("strikes Alice").get(1));
    }

    @Test
    void bothNeedTheAdminPermission() {
        CommandsTest.Sender user = new CommandsTest.Sender();
        assertEquals(List.of("§cNo permission."), run(user, "ladder"));
        assertEquals(List.of("§cNo permission."), run(user, "strikes Bob"));
        assertEquals(List.of(), engine.commands().complete(user, new String[] {"strikes", ""}));
        assertEquals(List.of(), engine.commands().complete(user, new String[] {"ladder", ""}));
        assertTrue(engine.commands().complete(admin, new String[] {"s"}).contains("strikes"));
        assertTrue(engine.commands().complete(admin, new String[] {"l"}).contains("ladder"));
    }
}
