package com.jason.plugins.antibadwords.engine;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** A fake server plus an engine on a temp folder. */
final class Fixture {
    final Path dir;
    final FakePlatform platform;
    final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    Engine engine;

    Fixture(Path dir) {
        this.dir = dir;
        this.platform = new FakePlatform(dir);
    }

    /** Starts (or restarts) the engine, like a server start. */
    Engine start() {
        engine = TestEngines.track(Engine.load(platform, dir, now::get));
        return engine;
    }

    Path file(String relative) {
        return dir.resolve(relative);
    }

    /** A chat message from the player, judged and followed up like a loader does it. */
    Verdict say(java.util.UUID player, String source, String text) {
        Message message = engine.message(player, platform.players.get(player), source, text);
        Verdict verdict = engine.inspect(message);
        engine.onViolation(message, verdict);
        return verdict;
    }
}
