package com.jason.plugins.moderra.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.moderra.engine.integrations.BlockedEditLog;
import com.jason.plugins.moderra.engine.integrations.ExtraIntegrationSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockedEditLogTest {
    @TempDir
    Path dir;

    @Test
    void logsBlockedSignAndBookButNotChat() {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        List<String> records = new ArrayList<>();
        BlockedEditLog.install(engine, (UUID id, String name, String line) -> records.add(name + "|" + line));
        UUID bob = f.platform.join("Bob");
        assertTrue(f.say(bob, Message.SIGN, "fuck").blocked());
        assertTrue(f.say(bob, Message.CHAT, "fuck").blocked());
        assertTrue(f.say(bob, Message.BOOK, "fuck").blocked());
        f.say(bob, Message.SIGN, "hello");
        assertEquals(List.of("Bob|[Moderra] block sign edit: fuck", "Bob|[Moderra] block book edit: fuck"), records);
    }

    @Test
    void switchedOff() {
        Fixture f = new Fixture(dir);
        Engine engine = f.start();
        TestFiles.write(f.file("integrations.yml"), "integrations:\n  coreprotect:\n    enabled: false\n");
        engine = f.start();
        assertEquals(false, engine.value(ExtraIntegrationSettings.COREPROTECT));
        List<String> records = new ArrayList<>();
        BlockedEditLog.install(engine, (id, name, line) -> records.add(line));
        f.say(f.platform.join("Bob"), Message.SIGN, "fuck");
        assertTrue(records.isEmpty());
    }
}
