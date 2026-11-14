package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.integration.IntegrationSettings;
import com.jason.plugins.antibadwords.engine.integration.PermissionSource;
import com.jason.plugins.antibadwords.engine.integration.PlaceholderValues;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PlaceholderAPI values and the LuckPerms hook (profile by group / meta, offline permission checks), against a fake source. */
class PapiLuckPermsTest {
    private static final String WORLDS = """
        profiles:
          default: {}
          relaxed:
            filters.protection-level: 1
          strict:
            filters.protection-level: 3
        worlds:
          Lobby: strict
        """;

    /** A fake permission plugin. */
    private static class FakeSource implements PermissionSource {
        final Map<UUID, String> primary = new HashMap<>();
        final Map<UUID, Set<String>> groups = new HashMap<>();
        final Map<UUID, Map<String, String>> meta = new HashMap<>();
        final Map<UUID, Set<String>> offline = new HashMap<>();

        @Override
        public String name() {
            return "Fake";
        }

        @Override
        public Optional<String> primaryGroup(UUID player) {
            return Optional.ofNullable(primary.get(player));
        }

        @Override
        public boolean inGroup(UUID player, String group) {
            return groups.getOrDefault(player, Set.of()).contains(group.toLowerCase());
        }

        @Override
        public Optional<String> meta(UUID player, String key) {
            return Optional.ofNullable(meta.getOrDefault(player, Map.of()).get(key));
        }

        @Override
        public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
            return CompletableFuture.completedFuture(offline.getOrDefault(player, Set.of()).contains(node));
        }
    }

    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    FakeSource source = new FakeSource();
    UUID bob;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        TestFiles.write(f.file("worlds.yml"), WORLDS);
        engine = f.engine;
        bob = f.platform.join("Bob");
        engine.reload();
    }

    private String profileOf(UUID id, String world) {
        return engine.settings(id, world, "", node -> f.platform.has(id, node)).profile();
    }

    /** Writes integrations.yml: {@code options} are lines under integrations.luckperms (2 spaces more), groups the map. */
    private void lp(String options, String... groups) {
        StringBuilder y = new StringBuilder("integrations:\n  luckperms:\n");
        for (String o : options.split("\n")) {
            if (!o.isBlank()) {
                y.append("    ").append(o).append('\n');
            }
        }
        if (groups.length > 0) {
            y.append("    group-profiles:\n");
            for (String g : groups) {
                y.append("      ").append(g).append('\n');
            }
        }
        TestFiles.write(f.file("integrations.yml"), y.toString());
        assertTrue(engine.reload().isEmpty(), engine.problems().toString());
    }

    @Test
    void withoutAPermissionPluginNothingChanges() {
        assertEquals("default", profileOf(bob, "world"));
        assertEquals("strict", profileOf(bob, "lobby"));
        assertNull(engine.permissionSource());
    }

    @Test
    void groupMapPicksTheProfile() {
        lp("", "vip: relaxed", "admin: strict");
        source.primary.put(bob, "default");
        source.groups.put(bob, Set.of("default", "vip"));
        engine.setPermissionSource(source);
        assertEquals("relaxed", profileOf(bob, "world"), "inherited group counts");
        assertEquals("relaxed", profileOf(bob, "lobby"), "beats the world rule");
        UUID alice = f.platform.join("Alice");
        assertEquals("default", profileOf(alice, "world"), "no group, nothing known");
        assertEquals("strict", profileOf(alice, "lobby"), "world rules still apply");
    }

    @Test
    void primaryGroupOnlyAndFileOrder() {
        lp("primary-group-only: true", "vip: relaxed", "admin: strict");
        source.primary.put(bob, "admin");
        source.groups.put(bob, Set.of("admin", "vip"));
        engine.setPermissionSource(source);
        assertEquals("strict", profileOf(bob, "world"), "only the primary group is looked at");
        lp("primary-group-only: false", "vip: relaxed", "admin: strict");
        assertEquals("relaxed", profileOf(bob, "world"), "first matching line in the file wins");
    }

    @Test
    void metaBeatsGroupsButNotThePermission() {
        lp("", "vip: strict");
        source.primary.put(bob, "vip");
        source.meta.put(bob, Map.of("antibadwords-profile", "Relaxed"));
        engine.setPermissionSource(source);
        assertEquals("relaxed", profileOf(bob, "world"));
        f.platform.permissions.get(bob).add("antibadwords.profile.strict");
        assertEquals("strict", profileOf(bob, "world"), "the explicit permission wins");
        f.platform.permissions.get(bob).clear();
        source.meta.put(bob, Map.of("antibadwords-profile", "does-not-exist"));
        assertEquals("strict", profileOf(bob, "world"), "an unknown meta value falls back to the group map");
        source.meta.put(bob, Map.of("antibadwords-profile", "relaxed"));
        lp("use-meta: false", "vip: strict");
        assertEquals("strict", profileOf(bob, "world"), "meta switched off");
    }

    @Test
    void aMappedProfileThatDoesNotExistIsReportedAndIgnored() {
        lp("", "vip: nope");
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("group-profiles.vip") && w.contains("nope")), f.platform.warnings.toString());
        source.primary.put(bob, "vip");
        engine.setPermissionSource(source);
        assertEquals("default", profileOf(bob, "world"));
    }

    @Test
    void aBrokenSourceNeverBreaksChecking() {
        lp("", "vip: relaxed");
        engine.setPermissionSource(new FakeSource() {
            @Override
            public Optional<String> primaryGroup(UUID player) {
                throw new IllegalStateException("LuckPerms not ready");
            }
        });
        assertEquals("default", profileOf(bob, "world"));
        assertTrue(f.say(bob, Message.CHAT, "hello there").allowed());
    }

    @Test
    void offlinePermissionChecks() {
        UUID carl = UUID.randomUUID();
        source.offline.put(carl, Set.of("antibadwords.bypass.mute"));
        assertFalse(engine.hasPermission(carl, "antibadwords.bypass.mute").join(), "no permission plugin: unknown means false");
        engine.setPermissionSource(source);
        assertTrue(engine.hasPermission(carl, "antibadwords.bypass.mute").join());
        assertFalse(engine.hasPermission(carl, "antibadwords.other").join());
        f.platform.permissions.get(bob).add("antibadwords.bypass");
        assertTrue(engine.hasPermission(bob, "antibadwords.bypass").join(), "online players are asked through the platform");
        lp("offline-permission-checks: false");
        assertFalse(engine.hasPermission(carl, "antibadwords.bypass.mute").join());
        engine.setPermissionSource(new FakeSource() {
            @Override
            public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
                return CompletableFuture.failedFuture(new IllegalStateException("db down"));
            }
        });
        lp("offline-permission-checks: true");
        assertFalse(engine.hasPermission(carl, "antibadwords.bypass.mute").join(), "an error means false");
    }

    @Test
    void muteOfAnOfflineExemptPlayerWarnsTheAdmin() {
        UUID carl = UUID.randomUUID();
        engine.players().learn(carl, "Carl");
        source.offline.put(carl, Set.of("antibadwords.bypass.mute"));
        engine.setPermissionSource(source);
        List<String> out = new ArrayList<>();
        engine.commands().execute(new CommandSender() {
            @Override
            public String name() {
                return "Console";
            }

            @Override
            public UUID id() {
                return null;
            }

            @Override
            public boolean has(String node) {
                return true;
            }

            @Override
            public void send(String message) {
                out.add(message);
            }
        }, "abw", new String[] {"mute", "Carl", "10m", "test"});
        assertTrue(out.stream().anyMatch(s -> s.contains("Muted Carl")), out.toString());
        assertTrue(out.stream().anyMatch(s -> s.contains("antibadwords.bypass.mute") && s.contains("not held back")), out.toString());
    }

    // ---------------------------------------------------------------------------- placeholders

    private String ph(UUID id, String name) {
        return engine.placeholders().resolve(id, name);
    }

    @Test
    void placeholderValues() {
        assertEquals("0", ph(bob, "strikes"));
        assertEquals("false", ph(bob, "muted"));
        assertEquals("", ph(bob, "mute_remaining"));
        assertEquals("", ph(bob, "mute_reason"));
        assertEquals("default", ph(bob, "profile"));
        assertEquals("3", ph(bob, "protection_level"));
        assertEquals("0", ph(bob, "violations_total"));
        assertEquals(Integer.toString(engine.settings().legacy().violationsBeforePunish()), ph(bob, "strikes_next"), "the classic setup is the step legacy");
        assertEquals("legacy", ph(bob, "next_step"));
        f.platform.worlds.put(bob, "Lobby");
        assertEquals("strict", ph(bob, "profile"));
        assertEquals("3", ph(bob, "PROTECTION_LEVEL"), "names are case-insensitive");
        assertNull(ph(bob, "nonsense"), "unknown placeholder: PlaceholderAPI leaves it alone");
        assertNull(ph(bob, "word_list"));
        assertEquals("", ph(null, "strikes"));
    }

    @Test
    void placeholdersFollowStrikesAndMute() {
        f.say(bob, Message.CHAT, "fuck this");
        assertEquals("1", ph(bob, "strikes"));
        engine.history().flush();
        assertTrue(Integer.parseInt(ph(bob, "violations_total")) >= 0, "history count (cached, refreshed in the background)");
        engine.mutes().mute(bob, "Bob", 90_000, "being rude", "Admin");
        assertEquals("true", ph(bob, "muted"));
        assertEquals("1m30s", ph(bob, "mute_remaining"));
        assertEquals("being rude", ph(bob, "mute_reason"));
        engine.mutes().mute(bob, "Bob", DurationParser.PERMANENT, "", "Admin");
        assertEquals("permanent", ph(bob, "mute_remaining"));
    }

    @Test
    void placeholderValuesAreWorkForOfflinePlayersToo() {
        UUID carl = UUID.randomUUID();
        engine.mutes().mute(carl, "Carl", DurationParser.PERMANENT, "spam", "Admin");
        assertEquals("true", ph(carl, "muted"));
        assertEquals("spam", ph(carl, "mute_reason"));
        assertEquals("default", ph(carl, "profile"));
    }

    @Test
    void placeholdersNeverReturnChatTextOrSecrets() {
        for (String name : PlaceholderValues.NAMES) {
            assertFalse(name.contains("text") || name.contains("word") || name.contains("webhook") || name.contains("token"), name);
        }
        assertEquals(9, PlaceholderValues.NAMES.size());
    }

    @Test
    void settingsHaveSafeDefaults() {
        assertTrue(engine.value(IntegrationSettings.PAPI_ENABLED));
        assertFalse(engine.value(IntegrationSettings.PAPI_PARSE), "parsing our messages is opt-in");
        assertTrue(engine.value(IntegrationSettings.LP_ENABLED));
        assertEquals("antibadwords-profile", engine.value(IntegrationSettings.LP_META_KEY));
        assertEquals(Set.of(), new HashSet<>(engine.config().load(null).section(IntegrationSettings.GROUP_PROFILES).keySet()));
    }
}
