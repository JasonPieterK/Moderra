package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.integrations.ChatChannels;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Chat-channel plugins: channel name -> rule profile, channel of a command, Message.channel. */
class ChatChannelsTest {
    @TempDir
    Path dir;
    Fixture f;
    UUID bob;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        bob = f.platform.join("Bob");
    }

    private void profiles(String channelProfiles) {
        TestFiles.write(f.file("worlds.yml"), """
            default-profile: default
            use-permission-profiles: false
            profiles:
              default: {}
              strict:
                filters.protection-level: 3
              lax:
                filters.protection-level: 1
            worlds:
              world: strict
            """);
        TestFiles.write(f.file("integrations.yml"), """
            integrations:
              chatchannels:
                channel-profiles: %s
            """.formatted(channelProfiles));
        f.start();
    }

    @Test
    void messageChannelIsAdditiveAndSurvivesCopies() {
        Message m = Message.of(bob, "Bob", "world", Message.CHAT, "hi", null);
        assertEquals("", m.channel());
        Message c = m.withChannel("global");
        assertEquals("global", c.channel());
        assertEquals("global", c.withText("x").withDimension("d").asProbe().channel());
        assertEquals("", m.channel(), "immutable");
        assertEquals("", m.withChannel(null).channel());
    }

    @Test
    void channelBeatsTheWorldRule() {
        profiles("{global: lax, \"staff*\": default}");
        assertEquals("strict", f.engine.settings(bob, "world", "", null).profile(), "no channel: world rule");
        assertEquals("lax", f.engine.settings(bob, "world", "", null, "global").profile());
        assertEquals("lax", f.engine.settings(bob, "world", "", null, "GLOBAL").profile(), "case is ignored");
        assertEquals("default", f.engine.settings(bob, "world", "", null, "staff-chat").profile(), "pattern");
        assertEquals("strict", f.engine.settings(bob, "world", "", null, "trade").profile(), "unknown channel: world rule");
    }

    @Test
    void permissionProfileStillWinsOverTheChannel() {
        profiles("{global: lax}");
        TestFiles.edit(f.file("worlds.yml"), "use-permission-profiles: false", "use-permission-profiles: true");
        f.start();
        assertEquals("strict", f.engine.settings(bob, "world", "", n -> n.equals("moderra.profile.strict"), "global").profile());
    }

    @Test
    void unknownProfileIsReportedAndIgnored() {
        profiles("{global: nope}");
        assertEquals("strict", f.engine.settings(bob, "world", "", null, "global").profile());
        assertTrue(f.platform.warnings.stream().anyMatch(w -> w.contains("channel-profiles.global") && w.contains("nope")), f.platform.warnings.toString());
    }

    @Test
    void masterSwitchTurnsTheMapsOff() {
        profiles("{global: lax}");
        TestFiles.write(f.file("integrations.yml"), """
            integrations:
              chatchannels:
                enabled: false
                channel-profiles: {global: lax}
            """);
        f.start();
        assertEquals("strict", f.engine.settings(bob, "world", "", null, "global").profile());
    }

    @Test
    void commandsMapToChannelsAndAreJudgedWithTheirProfile() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("g", "global");
        m.put("sc", "staff");
        assertEquals("global", ChatChannels.channelOfCommand(m, "/g hello"));
        assertEquals("global", ChatChannels.channelOfCommand(m, "G hello"));
        assertEquals("staff", ChatChannels.channelOfCommand(m, "/venturechat:sc hi"));
        assertEquals("", ChatChannels.channelOfCommand(m, "/gg hello"));
        assertNull(ChatChannels.profileFor(Map.of(), "x", Set.of("a")));

        // end to end: the command's channel picks the profile used for the verdict
        profiles("{global: lax}");
        TestFiles.edit(f.file("integrations.yml"), "channel-profiles: {global: lax}",
            "channel-profiles: {global: lax}\n    channel-commands: {g: global}");
        f.start();
        Verdict v = f.engine.inspect(f.engine.message(bob, "Bob", Message.COMMAND, "/g hello"));
        assertEquals("lax", v.settings().profile());
        Verdict other = f.engine.inspect(f.engine.message(bob, "Bob", Message.COMMAND, "/w hello"));
        assertEquals("strict", other.settings().profile());
    }
}
