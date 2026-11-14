package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.filters.EvasionSettings;
import com.jason.plugins.antibadwords.engine.filters.LinkSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mini task 24: discord-in defaults, stale strikes files, /abw info integration lines. */
class Task24Test {
    private static final String LADDER = "moderation/ladder.yml";

    @TempDir
    Path dir;

    private static List<String> run(Engine engine, String line) {
        List<String> out = new ArrayList<>();
        engine.commands().execute(new CommandSender() {
            public String name() { return "Console"; }
            public UUID id() { return null; }
            public boolean has(String node) { return true; }
            public void send(String message) { out.add(message); }
        }, "abw", line.split(" "));
        return out;
    }

    // ---- discord-in in the default sources ------------------------------------------------------------

    @Test
    void newInstallsFilterDiscordMessagesForLinksAndBypassTricks() {
        Engine engine = new Fixture(dir).start();
        assertTrue(engine.value(LinkSettings.SOURCES).contains("discord-in"));
        assertTrue(engine.value(EvasionSettings.DETECT_SOURCES).contains("discord-in"));
        assertTrue(TestFiles.read(dir.resolve("filters/links.yml")).contains("discord-in"));
    }

    @Test
    void migratedInstallsKeepTheOldLists() {
        TestFiles.write(dir.resolve("config.yml"), TestFiles.legacyDefaultConfig());
        Engine engine = new Fixture(dir).start();
        assertEquals(LinkSettings.LEGACY_SOURCES, engine.value(LinkSettings.SOURCES));
        assertEquals(EvasionSettings.LEGACY_DETECT_SOURCES, engine.value(EvasionSettings.DETECT_SOURCES));
        assertFalse(engine.value(LinkSettings.SOURCES).contains("discord-in"));
    }

    // ---- stale strikes.json ------------------------------------------------------------------------------

    private static final String STRIKES = "{\"version\":1,\"strikes\":{\"%s\":{\"points\":4,\"last\":%d,\"decay-from\":%d}}}\n";

    @Test
    void staleStrikesFileIsSetAsideWhenClearOnQuitIsOn() {
        Fixture f = new Fixture(dir);
        UUID id = UUID.nameUUIDFromBytes("Bob".getBytes());
        long now = f.now.get();
        TestFiles.write(dir.resolve("data/strikes.json"), STRIKES.formatted(id, now, now));
        Engine engine = f.start(); // clear-violations-on-quit is true by default
        assertEquals(0, engine.strikes().get(id));
        assertFalse(Files.exists(dir.resolve("data/strikes.json")), "set aside");
        assertTrue(Files.exists(dir.resolve("data/strikes.json.discarded")), "but not lost");
        assertTrue(f.platform.infos.stream().anyMatch(i -> i.contains("Ignoring the saved strikes") && i.contains("clear-violations-on-quit")),
            f.platform.infos.toString());
        engine.shutdown();

        // the admin switches later: the old strikes do NOT come back
        TestFiles.edit(dir.resolve(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        engine = f.start();
        assertEquals(0, engine.strikes().get(id), "no silent revival");
        engine.shutdown();
    }

    @Test
    void aFileFromAnEarlierRunDoesNotComeBackAfterLaterSwitching() {
        Fixture f = new Fixture(dir);
        f.start();
        TestFiles.edit(dir.resolve(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        Engine engine = f.start();
        UUID id = f.platform.join("Bob");
        engine.onJoin(id, "Bob");
        engine.strikes().add(id, 2);
        engine.shutdown();
        assertTrue(Files.exists(dir.resolve("data/strikes.json")), "kept while strikes are kept");

        // switched back to clear-on-quit: the file is set aside at the next start
        TestFiles.edit(dir.resolve(LADDER), "clear-violations-on-quit: false", "clear-violations-on-quit: true");
        engine = f.start();
        assertEquals(0, engine.strikes().get(id));
        engine.shutdown();
        TestFiles.edit(dir.resolve(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        engine = f.start();
        assertEquals(0, engine.strikes().get(id), "the old two strikes stay gone");
    }

    @Test
    void keptStrikesFileIsReadAsBefore() {
        Fixture f = new Fixture(dir);
        f.start();
        TestFiles.edit(dir.resolve(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        Engine engine = f.start();
        UUID id = f.platform.join("Bob");
        engine.onJoin(id, "Bob");
        engine.strikes().add(id, 2);
        engine.shutdown();
        engine = f.start();
        assertEquals(2, engine.strikes().get(id));
        assertFalse(Files.exists(dir.resolve("data/strikes.json.discarded")));
    }

    @Test
    void savingSwitchedOffAlsoSetsTheFileAside() {
        Fixture f = new Fixture(dir);
        f.start();
        TestFiles.edit(dir.resolve(LADDER), "clear-violations-on-quit: true", "clear-violations-on-quit: false");
        TestFiles.edit(dir.resolve(LADDER), "save-to-disk: true", "save-to-disk: false");
        UUID id = UUID.nameUUIDFromBytes("Bob".getBytes());
        TestFiles.write(dir.resolve("data/strikes.json"), STRIKES.formatted(id, f.now.get(), f.now.get()));
        Engine engine = f.start();
        assertEquals(0, engine.strikes().get(id));
        assertTrue(f.platform.infos.stream().anyMatch(i -> i.contains("save-to-disk")), f.platform.infos.toString());
    }

    // ---- /abw info -----------------------------------------------------------------------------------------

    @Test
    void infoListsDetectedIntegrationsAndSaysDiscordIsBukkitOnly() {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        List<String> info = run(engine, "info");
        assertTrue(info.stream().anyMatch(l -> l.contains("Integrations detected: ") && l.endsWith("none")), info.toString());
        assertTrue(info.stream().anyMatch(l -> l.contains("DiscordSRV features") && l.contains("Bukkit/Paper/Purpur only")
            && l.contains("not available on Fake")), info.toString());
        f.platform.plugins.add("DiscordSRV");
        f.platform.plugins.add("LuckPerms");
        info = run(engine, "info");
        assertTrue(info.stream().anyMatch(l -> l.endsWith("DiscordSRV, LuckPerms")), info.toString());
    }
}
