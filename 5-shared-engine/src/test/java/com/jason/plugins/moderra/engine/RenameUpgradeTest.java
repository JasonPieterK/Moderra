package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.config.ConfigStore;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.FolderMigration;
import com.jason.plugins.moderra.engine.config.MessageDefaults;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import com.jason.plugins.moderra.engine.config.YamlTree;
import com.jason.plugins.moderra.engine.discord.DiscordSettings;
import com.jason.plugins.moderra.engine.discord.DiscordSrvSettings;
import com.jason.plugins.moderra.engine.integrations.OutgoingScanSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Upgrade from the former project name: data folder, legacy permission nodes, old default values. */
class RenameUpgradeTest {
    @TempDir
    Path dir;

    private static Path oldFolderWithFiles(Path plugins, String oldName) throws IOException {
        Path old = plugins.resolve(oldName);
        Files.createDirectories(old.resolve("data"));
        Files.writeString(old.resolve("config.yml"), "marker: 1\n");
        Files.writeString(old.resolve("data/strikes.json"), "{}");
        return old;
    }

    @Test
    void theOldBukkitFolderIsMovedAndANoteStaysBehind() throws IOException {
        Path old = oldFolderWithFiles(dir, "AntiBadWords");
        Path current = dir.resolve("Moderra");
        List<String> info = new ArrayList<>();
        assertTrue(FolderMigration.migrate(current, info::add, info::add));
        assertEquals("marker: 1\n", Files.readString(current.resolve("config.yml")));
        assertTrue(Files.isRegularFile(current.resolve("data/strikes.json")));
        assertFalse(Files.exists(old.resolve("config.yml")));
        assertTrue(Files.isRegularFile(old.resolve(FolderMigration.NOTE_FILE)));
        assertEquals(1, info.size(), info.toString());
        // a second start does nothing
        assertFalse(FolderMigration.migrate(current, info::add, info::add));
        assertEquals(1, info.size());
    }

    @Test
    void theOldModFolderIsMoved() throws IOException {
        oldFolderWithFiles(dir, "antibadwords");
        Path current = dir.resolve("moderra");
        assertTrue(FolderMigration.migrate(current, s -> { }, s -> { }));
        assertTrue(Files.isRegularFile(current.resolve("config.yml")));
        assertTrue(Files.isRegularFile(dir.resolve("antibadwords").resolve(FolderMigration.NOTE_FILE)));
    }

    @Test
    void anEmptyNewFolderCreatedByTheServerIsReplaced() throws IOException {
        oldFolderWithFiles(dir, "AntiBadWords");
        Files.createDirectories(dir.resolve("Moderra"));
        assertTrue(FolderMigration.migrate(dir.resolve("Moderra"), s -> { }, s -> { }));
        assertTrue(Files.isRegularFile(dir.resolve("Moderra/config.yml")));
    }

    @Test
    void anExistingNewFolderIsNeverTouched() throws IOException {
        Path old = oldFolderWithFiles(dir, "AntiBadWords");
        Files.createDirectories(dir.resolve("Moderra"));
        Files.writeString(dir.resolve("Moderra/config.yml"), "new: 1\n");
        assertFalse(FolderMigration.migrate(dir.resolve("Moderra"), s -> { }, s -> { }));
        assertEquals("new: 1\n", Files.readString(dir.resolve("Moderra/config.yml")));
        assertTrue(Files.isRegularFile(old.resolve("config.yml")));
        assertFalse(Files.exists(old.resolve(FolderMigration.NOTE_FILE)));
    }

    @Test
    void withoutAnOldFolderNothingHappens() {
        List<String> messages = new ArrayList<>();
        assertFalse(FolderMigration.migrate(dir.resolve("Moderra"), messages::add, messages::add));
        assertFalse(Files.exists(dir.resolve("Moderra")));
        assertTrue(messages.isEmpty());
        assertNull(FolderMigration.oldFolderOf(dir.resolve("SomethingElse")));
    }

    @Test
    void theEngineMovesTheOldFolderBeforeItReadsAnything() throws IOException {
        Path plugins = dir.resolve("plugins");
        Path old = oldFolderWithFiles(plugins, "AntiBadWords");
        Files.createDirectories(old.resolve("wordlists"));
        Files.writeString(old.resolve("wordlists/custom.yml"), "words: [zorblax]\n");
        Fixture f = new Fixture(plugins.resolve("Moderra"));
        f.start();
        assertTrue(f.platform.infos.stream().anyMatch(l -> l.startsWith("Moved the old data folder")), f.platform.infos.toString());
        assertTrue(Files.isRegularFile(plugins.resolve("Moderra/wordlists/custom.yml")));
        assertTrue(Files.isRegularFile(old.resolve(FolderMigration.NOTE_FILE)));
    }

    @Test
    void anOldPermissionNodeCountsAsTheNewNodeButOnlyWhenExplicit() {
        assertEquals("antibadwords.bypass.spam", PermissionNodes.legacyOf("moderra.bypass.spam"));
        assertNull(PermissionNodes.legacyOf("minecraft.command.op"));
        // the normal check has no answer, the explicit check knows the old node
        assertTrue(PermissionNodes.has(n -> false, n -> n.equals("antibadwords.admin"), "moderra.admin"));
        assertFalse(PermissionNodes.has(n -> false, n -> n.equals("antibadwords.admin"), "moderra.notify"));
        assertTrue(PermissionNodes.has(n -> n.equals("moderra.admin"), n -> false, "moderra.admin"));
        // a foreign node never falls back
        assertFalse(PermissionNodes.has(n -> false, n -> true, "other.node"));
        // the check that answers later: only the legacy node is granted
        assertTrue(PermissionNodes.hasAsync(n -> CompletableFuture.completedFuture(n.equals("antibadwords.mute")), "moderra.mute").join());
        assertFalse(PermissionNodes.hasAsync(n -> CompletableFuture.completedFuture(false), "moderra.mute").join());
        assertFalse(PermissionNodes.hasAsync(n -> CompletableFuture.completedFuture(true), "other.node").thenApply(x -> !x).join());
    }

    @Test
    void oldDefaultValuesOfNamesAndListsAreUpgraded() throws IOException {
        Fixture f = new Fixture(dir);
        f.start();
        SettingsRegistry registry = new SettingsRegistry();
        Engine.registerSettings(registry);
        ConfigStore store = new ConfigStore(dir, registry);
        store.set(DiscordSettings.USERNAME, "AntiBadWords");
        store.set(DiscordSrvSettings.PREFIX, "!abw");
        store.set(CoreSettings.IGNORED_COMMANDS, List.of("antibadwords", "abw", "antiwords"));
        store.set(OutgoingScanSettings.IGNORE, List.of("[AntiBadWords]"));
        f.engine.reload();
        assertEquals("Moderra", value(registry, DiscordSettings.USERNAME));
        assertEquals("!moderra", value(registry, DiscordSrvSettings.PREFIX));
        assertEquals(List.of("moderra"), value(registry, CoreSettings.IGNORED_COMMANDS));
        assertEquals(List.of("[Moderra]"), value(registry, OutgoingScanSettings.IGNORE));
        // a changed list is never touched
        store.set(CoreSettings.IGNORED_COMMANDS, List.of("antibadwords", "mine"));
        f.engine.reload();
        assertEquals(List.of("antibadwords", "mine"), value(registry, CoreSettings.IGNORED_COMMANDS));
        // but the own command is never scanned anyway
        assertTrue(f.engine.settings().legacy().ignoredCommands().contains("moderra"));
        assertTrue(MessageDefaults.OLD_DEFAULTS.containsKey("discord.commands.prefix"));
    }

    private Object value(SettingsRegistry registry, Setting<?> setting) {
        return YamlTree.parse(TestFiles.read(dir.resolve(setting.file()))).get(setting.key());
    }
}
