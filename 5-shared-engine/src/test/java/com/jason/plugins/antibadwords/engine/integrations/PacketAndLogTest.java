package com.jason.plugins.antibadwords.engine.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.ModerationEvent;
import com.jason.plugins.antibadwords.engine.integrations.PacketScanPlan.Backend;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PacketAndLogTest {
    @Test
    void packetScanOnlyWhenEventsMissing() {
        assertEquals(Backend.NONE, PacketScanPlan.choose(true, true, true, true, true, true).backend());
        assertEquals(Backend.PROTOCOLLIB, PacketScanPlan.choose(false, true, true, true, true, true).backend());
        assertEquals(Backend.PACKETEVENTS, PacketScanPlan.choose(false, true, false, true, true, true).backend());
        assertEquals(Backend.PROTOCOLLIB, PacketScanPlan.choose(true, false, true, true, true, true).backend());
        assertEquals(Backend.NONE, PacketScanPlan.choose(false, true, true, false, true, false).backend());
        assertTrue(PacketScanPlan.choose(false, true, false, true, false, false).reason().contains("switched off"));
    }

    @Test
    void recordLine() {
        ModerationEvent e = new ModerationEvent("violation", 0, UUID.randomUUID(), "Bob", "w", "sign", Map.of("action", "block", "text", "bad\nword"));
        assertEquals("[AntiBadWords] block sign edit: bad word", BlockedEditLog.line(e, true));
        assertEquals("[AntiBadWords] block sign edit", BlockedEditLog.line(e, false));
        ModerationEvent longText = new ModerationEvent("violation", 0, null, "", "", "book", Map.of("text", "x".repeat(500)));
        assertEquals("[AntiBadWords] blocked book edit: ".length() + 203, BlockedEditLog.line(longText, true).length());
    }

    @Test
    void sinkList() {
        List<String> lines = new ArrayList<>();
        lines.add(BlockedEditLog.line(new ModerationEvent("violation", 0, null, "", "", "anvil", Map.of("action", "censor", "text", "t")), true));
        assertEquals(List.of("[AntiBadWords] censor anvil edit: t"), lines);
    }
}
