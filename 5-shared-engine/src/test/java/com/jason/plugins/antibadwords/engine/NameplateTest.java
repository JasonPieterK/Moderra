package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jason.plugins.antibadwords.engine.integrations.PermissionSource;
import com.jason.plugins.antibadwords.engine.integrations.PluginCommandScan;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TAB / NametagEdit command scanning and the {prefix} / {group} values (Vault side is a fake permission source). */
class NameplateTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    UUID bob;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        engine = f.engine;
        bob = f.platform.join("Bob");
    }

    private String scan(String line) {
        return PluginCommandScan.extraCommandText(engine, Set.of("abw"), line);
    }

    @Test
    void commandsAreScannedOnlyWhileThePluginIsInstalled() {
        assertNull(scan("tab player Bob tabprefix hello"));
        f.platform.plugins.add("TAB");
        assertEquals("player Bob tabprefix hello", scan("tab player Bob tabprefix hello"));
        assertNull(scan("nte player Bob prefix x"), "NametagEdit is not installed");
        f.platform.plugins.add("NametagEdit");
        assertEquals("player Bob prefix x", scan("NTE player Bob prefix x"));
        assertNull(scan("tab"), "no arguments");
        assertNull(scan("msg Bob hi"), "not one of their commands");
    }

    @Test
    void switchesAndIgnoredCommandsWin() {
        f.platform.plugins.add("TAB");
        TestFiles.write(f.file("integrations.yml"), "integrations:\n  tab:\n    enabled: false\n");
        engine.reload();
        assertNull(scan("tab player Bob tabprefix hello"));
        TestFiles.write(f.file("integrations.yml"), "integrations:\n  tab:\n    scan-commands: [abw]\n");
        engine.reload();
        assertNull(scan("abw x y"), "ignored-commands wins");
    }

    @Test
    void prefixAndGroupValues() {
        Map<String, String> v = new HashMap<>();
        PluginCommandScan.addGroupValues(engine, bob, v);
        assertEquals("", v.get("prefix"));
        assertEquals("", v.get("group"));
        engine.setPermissionSource(new PermissionSource() {
            public String name() {
                return "Vault";
            }

            public Optional<String> primaryGroup(UUID p) {
                return Optional.of("vip");
            }

            public boolean inGroup(UUID p, String g) {
                return false;
            }

            public Optional<String> meta(UUID p, String k) {
                return Optional.empty();
            }

            public Optional<String> prefix(UUID p) {
                return Optional.of("&6[VIP]");
            }

            public CompletableFuture<Boolean> hasPermission(UUID p, String n) {
                return CompletableFuture.completedFuture(false);
            }
        });
        v.clear();
        PluginCommandScan.addGroupValues(engine, bob, v);
        assertEquals("&6[VIP]", v.get("prefix"));
        assertEquals("vip", v.get("group"));
        TestFiles.write(f.file("integrations.yml"), "integrations:\n  vault:\n    message-placeholders: false\n");
        engine.reload();
        v.clear();
        PluginCommandScan.addGroupValues(engine, bob, v);
        assertEquals(Map.of(), v);
    }
}
